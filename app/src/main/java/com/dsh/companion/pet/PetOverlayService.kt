package com.dsh.companion.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dsh.companion.AppVisibility
import com.dsh.companion.MainActivity
import com.dsh.companion.R
import com.dsh.companion.data.Character
import com.dsh.companion.data.CharacterRepository
import com.dsh.companion.data.ChatMessage
import com.dsh.companion.data.GroupStore
import com.dsh.companion.data.PersonaStore
import com.dsh.companion.data.SettingsStore
import com.dsh.companion.group.GroupChatEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class PetOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var settings: SettingsStore
    private lateinit var personas: PersonaStore
    private lateinit var characters: CharacterRepository
    private lateinit var groupStore: GroupStore
    private lateinit var groupEngine: GroupChatEngine
    private var autoChatBusy = false

    private var petView: ImageView? = null
    private var bubbleView: TextView? = null
    private var petParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleAnchor: WindowManager.LayoutParams? = null

    private class ExtraPet(
        val personaId: String,
        val character: Character,
        val view: ImageView,
        val params: WindowManager.LayoutParams,
    ) {
        var lastClip: String? = null
        var actionRunnable: Runnable? = null
        var flingRunnable: Runnable? = null
        var dragging: Boolean = false
        var scale: Float = 1f
        var brightness: Float = 1f
    }
    private val extraPets = mutableListOf<ExtraPet>()

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var startX = 0
    private var startY = 0
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragClipStarted = false
    private var revert: Runnable? = null

    private var scaleDetector: ScaleGestureDetector? = null
    private var velocityTracker: VelocityTracker? = null
    private var flingRunnable: Runnable? = null
    private var baseWidth = 0
    private var baseHeight = 0

    private var bubbleOffsetX = 0f
    private var bubbleOffsetY = 0f
    private var actionLockUntil = 0L
    private var bubbleDownX = 0f
    private var bubbleDownY = 0f
    private var bubbleStartOffsetX = 0f
    private var bubbleStartOffsetY = 0f
    private var bubbleMoved = false
    private var bubbleConversationId: String? = null
    private var bubbleGroupId: String? = null
    private var bubblePersonaId: String? = null
    private var lastBubbleTap = 0L

    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var menuExtra: ExtraPet? = null
    private var scrimView: View? = null
    private var collapsed = false
    private var lastAutonomousClip: String? = null
    private var soundPool: SoundPool? = null
    private var quackId = 0

    private val pokeLines = listOf(
        "干嘛戳我…",
        "在呢在呢。",
        "别戳了，痒。",
        "要摸摸头吗？",
        "再戳就咬你。",
        "今天也一起待着吧。",
        "戳一下就够了哦。",
        "我在看你在干嘛。",
    )

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settings = SettingsStore(this)
        personas = PersonaStore(this)
        characters = CharacterRepository(this)
        personas.reload()
        if (settings.desktopMainPersonaId.isNullOrBlank()) {
            settings.desktopMainPersonaId = settings.activePersonaId ?: personas.first()?.id
        }
        groupStore = GroupStore(this)
        groupEngine = GroupChatEngine(this)
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        quackId = soundPool?.load(this, R.raw.duck_quack, 1) ?: 0

        showPet()
        refreshExtraPets()
        playClip("idle", once = false)
        observeEvents()
        observeAppVisibility()
        scheduleAutoChat()
        scheduleAutonomousAction()
        handler.postDelayed({
            if (canShowOverlay()) {
                showBubble(
                    "我在这儿。点一下戳我，双击打开聊天。",
                    personaId = mainPersonaId() ?: personas.first()?.id,
                )
            }
        }, 1600)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RESET_POSITION) {
            moveToCorner()
            return START_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val params = petParams ?: return
        val bounds = boundsFor(params.width, params.height)
        val x = if (settings.overlayXF >= 0f && settings.overlayYF >= 0f) {
            bounds[0] + (settings.overlayXF * (bounds[1] - bounds[0])).toInt()
        } else {
            params.x
        }
        val y = if (settings.overlayXF >= 0f && settings.overlayYF >= 0f) {
            bounds[2] + (settings.overlayYF * (bounds[3] - bounds[2])).toInt()
        } else {
            params.y
        }
        params.x = clampX(x, params.width)
        params.y = clampY(y, params.height)
        petView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        persistPosition()
        moveBubble()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacks(autoChatRunnable)
        handler.removeCallbacks(autonomousActionRunnable)
        soundPool?.release()
        soundPool = null
        flingRunnable?.let(handler::removeCallbacks)
        velocityTracker?.recycle()
        menuView?.let { runCatching { windowManager.removeView(it) } }
        menuView = null
        handler.removeCallbacksAndMessages(null)
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        clearExtraPets()
        petView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        petView = null
        super.onDestroy()
    }

    private fun scaleTypeFor(character: Character): ImageView.ScaleType {
        val w = character.meta.frameWidth
        val h = character.meta.frameHeight
        return if (w > 0 && h > 0 && w.toFloat() / h > 1.15f) {
            ImageView.ScaleType.CENTER_CROP
        } else {
            ImageView.ScaleType.FIT_CENTER
        }
    }

    private fun showPet() {
        val scale = settings.overlayScale.coerceIn(0.4f, 3f)
        baseWidth = dp(160)
        baseHeight = dp(180)
        val width = (baseWidth * scale).toInt().coerceAtLeast(dp(40))
        val height = (baseHeight * scale).toInt().coerceAtLeast(dp(45))

        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            contentDescription = "桌宠"
        }

        val area = safeArea()
        val bounds = boundsFor(width, height, area)
        val params = WindowManager.LayoutParams(
            width,
            height,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = when {
                settings.overlayXF >= 0f && settings.overlayYF >= 0f ->
                    bounds[0] + (settings.overlayXF * (bounds[1] - bounds[0])).toInt()
                settings.overlayX >= 0 -> settings.overlayX
                else -> area.right - width
            }
            y = when {
                settings.overlayXF >= 0f && settings.overlayYF >= 0f ->
                    bounds[2] + (settings.overlayYF * (bounds[3] - bounds[2])).toInt()
                settings.overlayY >= 0 -> settings.overlayY
                else -> area.bottom - height
            }
            x = clampX(x, width)
            y = clampY(y, height)
        }

        val gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (collapsed) {
                    collapsed = false
                    moveToCorner()
                    return true
                }
                if (!dragging && settings.tapResponds) {
                    playQuack()
                    playClip("poke", once = true)
                    showBubble(
                        pokeLines.random(),
                        personaId = mainPersonaId() ?: personas.first()?.id,
                    )
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!dragging) openChat()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                showMenu()
            }
        })

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                applyScale(settings.overlayScale * detector.scaleFactor)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                persistPosition()
            }
        })
        this.scaleDetector = scaleDetector

        image.setOnTouchListener { _, event ->
            gesture.onTouchEvent(event)
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    flingRunnable?.let(handler::removeCallbacks)
                    flingRunnable = null
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    startX = params.x
                    startY = params.y
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    dragClipStarted = false
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    if (!scaleDetector.isInProgress) {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            dragging = true
                            if (!dragClipStarted) {
                                dragClipStarted = true
                                playClip("dragging", once = false)
                            }
                        }
                        if (dragging) {
                            params.x = clampX(startX + dx.toInt(), params.width)
                            params.y = clampY(startY + dy.toInt(), params.height)
                            runCatching { windowManager.updateViewLayout(image, params) }
                            moveBubble()
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.addMovement(event)
                    if (dragging) {
                        persistPosition()
                        playClip("dragging_release", once = true)
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vx = velocityTracker?.xVelocity ?: 0f
                        val vy = velocityTracker?.yVelocity ?: 0f
                        if (abs(vx) > 250f || abs(vy) > 250f) {
                            fling(vx, vy)
                        }
                    }
                    dragging = false
                    dragClipStarted = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                }
            }
            true
        }

        petView = image
        petParams = params
        windowManager.addView(image, params)
        petView?.visibility = when {
            !canShowOverlay() -> View.GONE
            mainPersonaId() == null -> View.GONE
            else -> View.VISIBLE
        }
        applyBrightness()
        applyFlip()
        persistPosition()
    }

    private fun playQuack() {
        if (!settings.duckSoundEnabled) return
        val id = quackId
        if (id != 0) soundPool?.play(id, 0.85f, 0.85f, 1, 0, 1f)
    }

    private fun refreshExtraPets() {
        clearExtraPets()
        personas.reload()
        val active = mainPersonaId()
        val ids = desktopPersonaIds()
            .filterNot { it == active }
            .distinct()
            .take(3)
        val area = safeArea()
        ids.forEachIndexed { index, id ->
            val persona = personas.get(id) ?: return@forEachIndexed
            val character = characters.get(persona.characterId) ?: return@forEachIndexed
            val width = (dp(160) * settings.overlayScale).toInt().coerceAtLeast(dp(40))
            val height = (dp(180) * settings.overlayScale).toInt().coerceAtLeast(dp(45))
            val view = ImageView(this).apply {
                scaleType = scaleTypeFor(character)
                contentDescription = persona.name
                setImageDrawable(characters.loadClip(character, "idle"))
                (drawable as? AnimatedImageDrawable)?.start()
                visibility = if (canShowOverlay()) View.VISIBLE else View.GONE
            }
            val params = WindowManager.LayoutParams(
                width,
                height,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = clampX(area.left + dp(16) + index * (width + dp(8)), width)
                y = clampY(area.bottom - height - dp(28) - index * dp(14), height)
            }
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false
            var longPressed = false
            var tracker: VelocityTracker? = null
            val extra = ExtraPet(persona.id, character, view, params).apply {
                scale = settings.overlayScale
                brightness = settings.overlayBrightness
            }
            view.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setScale(extra.brightness, extra.brightness, extra.brightness, 1f)
            })
            val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    if (!moved && !longPressed) {
                        playQuack()
                        playClipOnView(character, view, "poke")
                        showBubble(
                            text = pokeLines.random(),
                            personaId = persona.id,
                            anchor = params,
                        )
                    }
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!moved) openChatForPersona(persona.id)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    if (!moved) longPressed = true
                }
            })
            view.setOnTouchListener { _, event ->
                detector.onTouchEvent(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        extra.flingRunnable?.let(handler::removeCallbacks)
                        extra.flingRunnable = null
                        tracker?.recycle()
                        tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        moved = false
                        longPressed = false
                        extra.dragging = false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        tracker?.addMovement(event)
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                            moved = true
                            extra.dragging = true
                        }
                        if (extra.dragging) {
                            params.x = clampX(startX + dx.toInt(), width)
                            params.y = clampY(startY + dy.toInt(), height)
                            runCatching { windowManager.updateViewLayout(view, params) }
                            if (bubbleAnchor === params) moveBubble()
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        tracker?.addMovement(event)
                        if (extra.dragging) {
                            tracker?.computeCurrentVelocity(1000)
                            val vx = tracker?.xVelocity ?: 0f
                            val vy = tracker?.yVelocity ?: 0f
                            extra.dragging = false
                            if (abs(vx) > 250f || abs(vy) > 250f) {
                                flingExtra(extra, vx, vy)
                            }
                        } else if (longPressed && !moved) {
                            longPressed = false
                            showMenu(extra)
                        }
                        longPressed = false
                        tracker?.recycle()
                        tracker = null
                    }
                }
                true
            }
            runCatching { windowManager.addView(view, params) }
            extraPets.add(extra)
        }
        extraPets.forEach { scheduleExtraAction(it) }
    }

    private fun scheduleExtraAction(extra: ExtraPet) {
        extra.actionRunnable?.let(handler::removeCallbacks)
        val runnable = object : Runnable {
            override fun run() {
                if (!canShowOverlay() && System.currentTimeMillis() >= actionLockUntil) {
                    val excluded = setOf("idle", "thinking", "error", "dragging", "dragging_release", "success")
                    val pool = extra.character.meta.clips.keys
                        .filterNot { it in excluded }
                        .filterNot { it == extra.lastClip }
                    val clip = pool.randomOrNull()
                        ?: extra.character.meta.clips.keys.filterNot { it in excluded }.randomOrNull()
                    if (clip != null) {
                        extra.lastClip = clip
                        playClipOnView(extra.character, extra.view, clip)
                    }
                }
                handler.postDelayed(this, 15_000L + Random.nextLong(20_000L))
            }
        }
        extra.actionRunnable = runnable
        handler.postDelayed(runnable, 8_000L + Random.nextLong(15_000L))
    }

    private fun clearExtraPets() {
        extraPets.forEach { pet ->
            pet.actionRunnable?.let(handler::removeCallbacks)
            pet.flingRunnable?.let(handler::removeCallbacks)
            runCatching { windowManager.removeView(pet.view) }
        }
        extraPets.clear()
    }

    private fun playClipOnView(
        character: Character,
        view: ImageView,
        name: String,
        priority: Boolean = false,
    ) {
        if (!priority && System.currentTimeMillis() < actionLockUntil) return
        val resolved = if (character.meta.clips.containsKey(name)) {
            name
        } else {
            character.meta.clips.keys
                .filterNot { it in setOf("idle", "thinking", "error", "dragging", "dragging_release") }
                .randomOrNull() ?: name
        }
        val clip = characters.loadClip(character, resolved)
            ?: characters.loadClip(character, character.meta.defaultClip)
            ?: return
        view.setImageDrawable(clip)
        if (clip is AnimatedImageDrawable) clip.start()
        val duration = (character.meta.clips[resolved]?.durationMs ?: 1200).toLong()
        if (priority) {
            actionLockUntil = System.currentTimeMillis() + duration + 800L
        }
        handler.postDelayed({
            val idle = characters.loadClip(character, character.meta.defaultClip)
            if (idle != null) {
                view.setImageDrawable(idle)
                if (idle is AnimatedImageDrawable) idle.start()
            }
        }, duration + 60L)
    }

    private fun openChatForPersona(personaId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_CHAT, true)
            putExtra(MainActivity.EXTRA_OPEN_PERSONA, personaId)
        }
        runCatching { startActivity(intent) }
    }

    private fun applyScale(value: Float) {
        val scale = value.coerceIn(0.4f, 3f)
        val image = petView ?: return
        val params = petParams ?: return
        settings.overlayScale = scale
        val metrics = resources.displayMetrics
        val nextWidth = (baseWidth * scale).toInt().coerceAtLeast(dp(40))
        val nextHeight = (baseHeight * scale).toInt().coerceAtLeast(dp(45))
        params.width = nextWidth
        params.height = nextHeight
        params.x = clampX(params.x, nextWidth)
        params.y = clampY(params.y, nextHeight)
        runCatching { windowManager.updateViewLayout(image, params) }
        moveBubble()
    }

    private fun applyExtraScale(extra: ExtraPet, value: Float) {
        val scale = value.coerceIn(0.4f, 3f)
        extra.scale = scale
        val nextWidth = (dp(160) * scale).toInt().coerceAtLeast(dp(40))
        val nextHeight = (dp(180) * scale).toInt().coerceAtLeast(dp(45))
        val params = extra.params
        val centerX = params.x + params.width / 2
        val centerY = params.y + params.height / 2
        params.width = nextWidth
        params.height = nextHeight
        params.x = clampX(centerX - nextWidth / 2, nextWidth)
        params.y = clampY(centerY - nextHeight / 2, nextHeight)
        runCatching { windowManager.updateViewLayout(extra.view, params) }
        if (bubbleAnchor === params) moveBubble()
    }

    private fun applyExtraBrightness(extra: ExtraPet, value: Float) {
        val brightness = value.coerceIn(0.5f, 1.5f)
        extra.brightness = brightness
        val matrix = ColorMatrix().apply { setScale(brightness, brightness, brightness, 1f) }
        extra.view.colorFilter = ColorMatrixColorFilter(matrix)
    }

    private fun applyBrightness() {
        val value = settings.overlayBrightness
        val matrix = ColorMatrix().apply { setScale(value, value, value, 1f) }
        petView?.colorFilter = ColorMatrixColorFilter(matrix)
    }

    private fun applyFlip() {
        petView?.scaleX = if (settings.overlayFlipped) -1f else 1f
    }

    private fun moveToCorner() {
        val params = petParams ?: return
        val image = petView ?: return
        val area = safeArea()
        params.x = clampX(area.right - params.width, params.width)
        params.y = clampY(area.bottom - params.height, params.height)
        runCatching { windowManager.updateViewLayout(image, params) }
        persistPosition()
        collapsed = false
        moveBubble()
    }

    private fun collapseToSide() {
        val params = petParams ?: return
        val image = petView ?: return
        val area = safeArea()
        params.x = clampX(area.right - visibleX(params.width), params.width)
        params.y = clampY((area.top + area.bottom) / 2 - params.height / 2, params.height)
        runCatching { windowManager.updateViewLayout(image, params) }
        persistPosition()
        collapsed = true
        moveBubble()
    }

    private fun wander() {
        fling(
            Random.nextFloat() * 1800f - 900f,
            -Random.nextFloat() * 1200f - 400f,
        )
    }

    private fun randomAction(target: ExtraPet? = menuExtra) {
        if (target != null) {
            val name = target.character.meta.clips.keys
                .filterNot { it in setOf("idle", "thinking", "error", "dragging", "dragging_release") }
                .randomOrNull()
            if (name != null) playClipOnView(target.character, target.view, name, priority = true)
        } else {
            autonomousClip()?.let { playClip(it, once = true, priority = true) }
        }
    }

    private fun hideMenu() {
        menuView?.let { runCatching { windowManager.removeView(it) } }
        scrimView?.let { runCatching { windowManager.removeView(it) } }
        menuView = null
        menuParams = null
        scrimView = null
        menuExtra = null
    }

    private fun showScrim() {
        val scrim = View(this).apply {
            setBackgroundColor(0x01000000)
            isClickable = true
            setOnClickListener { hideMenu() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { windowManager.addView(scrim, params) }
        scrimView = scrim
    }

    private fun showMenu(target: ExtraPet? = null) {
        hideMenu()
        menuExtra = target
        val scale = settings.menuScale
        fun sdp(value: Int): Int = dp((value * scale).toInt())
        val metrics = resources.displayMetrics
        val panelWidth = sdp(215)
        val maxPanelHeight = (metrics.heightPixels * 0.82f).toInt()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sdp(2), sdp(4), sdp(2), sdp(8))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(box)
            background = GradientDrawable().apply {
                cornerRadius = sdp(18).toFloat()
                setColor(0xFFFFFBF0.toInt())
                setStroke(sdp(1), 0x33C9A96A)
            }
        }

        box.addView(TextView(this).apply {
            text = "桌宠设置"
            textSize = 18f * scale
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF2F2A22.toInt())
            setPadding(sdp(16), sdp(12), sdp(16), sdp(4))
        })

        fun addItem(label: String, action: () -> Unit) {
            box.addView(TextView(this).apply {
                text = label
                textSize = 16f * scale
                setTextColor(0xFF2F2A22.toInt())
                setPadding(sdp(16), sdp(13), sdp(16), sdp(13))
                isClickable = true
                setOnClickListener { action() }
            })
        }

        fun addLabel(text: String) {
            box.addView(TextView(this).apply {
                this.text = text
                textSize = 15f * scale
                setTextColor(0xFF8A6A24.toInt())
                setPadding(sdp(16), sdp(9), sdp(16), 0)
            })
        }

        addItem("点击回应：${if (settings.tapResponds) "开" else "关"}") {
            settings.tapResponds = !settings.tapResponds
            hideMenu()
        }
        addItem("点击音效（小黄鸭）：${if (settings.duckSoundEnabled) "开" else "关"}") {
            settings.duckSoundEnabled = !settings.duckSoundEnabled
            if (settings.duckSoundEnabled) playQuack()
            hideMenu()
        }
        addItem("动作") {
            showActionList()
        }
        addItem("随机动作") {
            randomAction(menuExtra)
            hideMenu()
        }

        val currentScale = menuExtra?.scale ?: settings.overlayScale
        val sizeLabel = TextView(this).apply {
            text = "桌宠大小：${(currentScale * 100).toInt()}%"
            textSize = 15f * scale
            setTextColor(0xFF8A6A24.toInt())
            setPadding(sdp(16), sdp(9), sdp(16), 0)
        }
        box.addView(sizeLabel)
        box.addView(SeekBar(this).apply {
            max = 260
            progress = ((currentScale * 100).toInt() - 40).coerceIn(0, 260)
            setPadding(sdp(14), sdp(2), sdp(14), sdp(2))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val percent = progress + 40
                    sizeLabel.text = "桌宠大小：$percent%"
                    val extra = menuExtra
                    if (extra != null) {
                        applyExtraScale(extra, percent / 100f)
                    } else {
                        applyScale(percent / 100f)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })

        val currentBrightness = menuExtra?.brightness ?: settings.overlayBrightness
        val brightnessLabel = TextView(this).apply {
            text = "亮度：${(currentBrightness * 100).toInt()}%"
            textSize = 15f * scale
            setTextColor(0xFF8A6A24.toInt())
            setPadding(sdp(16), sdp(9), sdp(16), 0)
        }
        box.addView(brightnessLabel)
        box.addView(SeekBar(this).apply {
            max = 100
            progress = ((currentBrightness * 100).toInt() - 50).coerceIn(0, 100)
            setPadding(sdp(14), sdp(2), sdp(14), sdp(2))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val percent = progress + 50
                    brightnessLabel.text = "亮度：$percent%"
                    val extra = menuExtra
                    if (extra != null) {
                        applyExtraBrightness(extra, percent / 100f)
                    } else {
                        settings.overlayBrightness = percent / 100f
                        applyBrightness()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })

        val bounceLabel = TextView(this).apply {
            text = "碰撞弹力：${(settings.petBounce * 100).toInt()}%"
            textSize = 15f * scale
            setTextColor(0xFF8A6A24.toInt())
            setPadding(sdp(16), sdp(9), sdp(16), 0)
        }
        box.addView(bounceLabel)
        box.addView(SeekBar(this).apply {
            max = 90
            progress = ((settings.petBounce * 100).toInt() - 10).coerceIn(0, 90)
            setPadding(sdp(14), sdp(2), sdp(14), sdp(2))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val percent = progress + 10
                    bounceLabel.text = "碰撞弹力：$percent%"
                    settings.petBounce = percent / 100f
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })

        val uiLabel = TextView(this).apply {
            text = "界面大小：${(settings.menuScale * 100).toInt()}%"
            textSize = 15f * scale
            setTextColor(0xFF8A6A24.toInt())
            setPadding(sdp(16), sdp(9), sdp(16), 0)
        }
        box.addView(uiLabel)
        box.addView(SeekBar(this).apply {
            max = 55
            progress = ((settings.menuScale * 100).toInt() - 75).coerceIn(0, 55)
            setPadding(sdp(14), sdp(2), sdp(14), sdp(2))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val percent = progress + 75
                    uiLabel.text = "界面大小：$percent%"
                    settings.menuScale = percent / 100f
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    hideMenu()
                    showMenu()
                }
            })
        })

        addItem("关闭桌宠") {
            stopSelf()
        }

        val params = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val pet = petParams
            val gap = sdp(4)
            val rightSpace = if (pet != null) metrics.widthPixels - (pet.x + pet.width) else 0
            x = if (pet != null && rightSpace < panelWidth + gap && pet.x > panelWidth + gap) {
                pet.x - panelWidth - gap
            } else {
                (pet?.x ?: 0) + (pet?.width ?: 0) + gap
            }
            x = x.coerceIn(0, max(0, metrics.widthPixels - panelWidth))
            y = (pet?.y ?: sdp(40)).coerceIn(0, max(0, metrics.heightPixels - maxPanelHeight))
        }

        showScrim()
        menuView = scroll
        menuParams = params
        runCatching { windowManager.addView(scroll, params) }
    }

    private fun showActionList() {
        val target = menuExtra
        hideMenu()
        val persona = if (target != null) personas.get(target.personaId) else personas.get(mainPersonaId())
        val character = target?.character
            ?: characters.get(persona?.characterId)
            ?: characters.list().firstOrNull()
            ?: return
        val clips = character.meta.clips
        if (clips.isEmpty()) return

        val scale = settings.menuScale
        fun sdp(value: Int): Int = dp((value * scale).toInt())
        val metrics = resources.displayMetrics
        val panelWidth = sdp(280)
        val names = clips.keys.sorted()
        val rows = (names.size + 1) / 2
        val estimatedHeight = sdp(38) + rows * sdp(33) + sdp(10)
        val maxPanelHeight = (metrics.heightPixels * 0.72f).toInt()
        val panelHeight = min(estimatedHeight, maxPanelHeight)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(sdp(2), sdp(4), sdp(2), sdp(6))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = estimatedHeight > maxPanelHeight
            addView(box)
            background = GradientDrawable().apply {
                cornerRadius = sdp(18).toFloat()
                setColor(0xFFFFFBF0.toInt())
                setStroke(sdp(1), 0x33C9A96A)
            }
        }

        box.addView(TextView(this).apply {
            text = "动作"
            textSize = 17f * scale
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF2F2A22.toInt())
            setPadding(sdp(14), sdp(10), sdp(14), sdp(2))
        })

        names.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(sdp(4), 0, sdp(4), 0)
            }
            pair.forEach { name ->
                val clip = clips[name] ?: return@forEach
                row.addView(TextView(this).apply {
                    text = actionLabel(name)
                    textSize = 14f * scale
                    setTextColor(0xFF2F2A22.toInt())
                    setPadding(sdp(10), sdp(9), sdp(10), sdp(9))
                    isClickable = true
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        if (target != null) {
                            playClipOnView(target.character, target.view, name, priority = true)
                        } else {
                            playClip(name, once = !clip.loop, priority = true)
                        }
                        hideMenu()
                    }
                })
            }
            if (pair.size == 1) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            box.addView(row)
        }

        val params = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val pet = target?.params ?: petParams
            val gap = sdp(5)
            val rightSpace = if (pet != null) metrics.widthPixels - (pet.x + pet.width) else 0
            x = if (pet != null && rightSpace < panelWidth + gap && pet.x > panelWidth + gap) {
                pet.x - panelWidth - gap
            } else {
                (pet?.x ?: 0) + (pet?.width ?: 0) + gap
            }
            x = x.coerceIn(0, max(0, metrics.widthPixels - panelWidth))
            y = (pet?.y ?: sdp(40)).coerceIn(0, max(0, metrics.heightPixels - panelHeight))
        }

        showScrim()
        menuView = scroll
        menuParams = params
        runCatching { windowManager.addView(scroll, params) }
    }

    private fun actionLabel(name: String): String = when (name) {
        "idle" -> "待机"
        "sleepy" -> "打盹"
        "thinking" -> "思考"
        "success" -> "开心"
        "error" -> "难过"
        "poke" -> "戳一下"
        "head_pat" -> "摸头"
        "shy" -> "害羞"
        "dance" -> "跳舞"
        "jump" -> "跳一下"
        "tail" -> "吐泡泡"
        "tail_wag" -> "摇尾巴"
        "eat_token" -> "吃 token"
        "bow" -> "鞠躬"
        "dragging" -> "被拖拽"
        "dragging_release" -> "落地"
        "stocking" -> "丝袜"
        "fox_nap" -> "狐狸打盹"
        "bed_sleep" -> "床上睡觉"
        "wave" -> "挥手"
        "sign" -> "举牌"
        "work" -> "工作"
        "panic" -> "慌张"
        "tea" -> "喝茶"
        "wipe" -> "擦汗"
        "stretch" -> "伸懒腰"
        "hug" -> "抱尾巴"
        "nap" -> "打盹"
        "pout" -> "鼓脸"
        "dizzy" -> "头晕"
        "peek" -> "偷看"
        "sigh" -> "叹气"
        "tail_wag" -> "摇尾巴"
        else -> name
    }

    private fun overlapsAny(params: WindowManager.LayoutParams, ignore: ExtraPet?): Boolean {
        val others = mutableListOf<WindowManager.LayoutParams>()
        petParams?.takeIf { it !== params }?.let(others::add)
        extraPets.filter { it !== ignore && it.params !== params }.forEach { others.add(it.params) }
        return others.any { other ->
            val overlapX = min(params.x + params.width, other.x + other.width) - max(params.x, other.x)
            val overlapY = min(params.y + params.height, other.y + other.height) - max(params.y, other.y)
            overlapX > 0 && overlapY > 0
        }
    }

    private fun flingExtra(extra: ExtraPet, vx0: Float, vy0: Float) {
        extra.flingRunnable?.let(handler::removeCallbacks)
        val view = extra.view
        val params = extra.params
        val bounds = boundsFor(params.width, params.height)
        val minX = bounds[0].toFloat()
        val maxX = bounds[1].toFloat()
        val minY = bounds[2].toFloat()
        val maxY = bounds[3].toFloat()
        var x = params.x.toFloat()
        var y = params.y.toFloat()
        var vx = vx0.coerceIn(-6000f, 6000f)
        var vy = vy0.coerceIn(-6000f, 6000f)
        val bounce = settings.petBounce.coerceIn(0.1f, 1f)
        val gravity = 2200f
        var last = System.nanoTime()

        val runnable = object : Runnable {
            override fun run() {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                last = now
                vy += gravity * dt
                x += vx * dt
                y += vy * dt
                if (x < minX) {
                    x = minX
                    vx = -vx * bounce
                } else if (x > maxX) {
                    x = maxX
                    vx = -vx * bounce
                }
                if (y < minY) {
                    y = minY
                    vy = -vy * bounce
                } else if (y > maxY) {
                    y = maxY
                    vy = -vy * bounce
                    vx *= 0.86f
                }
                params.x = x.toInt()
                params.y = y.toInt()
                if (overlapsAny(params, extra)) {
                    vx = -vx * bounce
                    vy = -vy * bounce
                }
                runCatching { windowManager.updateViewLayout(view, params) }
                if (bubbleAnchor === params) moveBubble()
                val resting = y >= maxY - 1 && abs(vy) < 60f && abs(vx) < 60f
                if (!resting) {
                    extra.flingRunnable = this
                    handler.postDelayed(this, 16)
                } else {
                    extra.flingRunnable = null
                }
            }
        }
        extra.flingRunnable = runnable
        handler.post(runnable)
    }

    private fun fling(vx0: Float, vy0: Float) {
        val image = petView ?: return
        val params = petParams ?: return
        val metrics = resources.displayMetrics
        val bounds = boundsFor(params.width, params.height)
        val minX = bounds[0]
        val maxX = bounds[1]
        val minY = bounds[2]
        val maxY = bounds[3]
        var x = params.x.toFloat()
        var y = params.y.toFloat()
        var vx = vx0.coerceIn(-6000f, 6000f)
        var vy = vy0.coerceIn(-6000f, 6000f)
        var last = System.nanoTime()
        val gravity = 2200f
        val bounce = settings.petBounce.coerceIn(0.1f, 1f)

        val runnable = object : Runnable {
            override fun run() {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                last = now

                vy += gravity * dt
                x += vx * dt
                y += vy * dt

                if (x < minX) {
                    x = minX.toFloat()
                    vx = -vx * bounce
                } else if (x > maxX) {
                    x = maxX.toFloat()
                    vx = -vx * bounce
                }
                if (y < minY) {
                    y = minY.toFloat()
                    vy = -vy * bounce
                } else if (y > maxY) {
                    y = maxY.toFloat()
                    vy = -vy * bounce
                    vx *= 0.86f
                }

                params.x = x.toInt()
                params.y = y.toInt()
                if (overlapsAny(params, null)) {
                    vx = -vx * bounce
                    vy = -vy * bounce
                }
                runCatching { windowManager.updateViewLayout(image, params) }
                moveBubble()

                val resting = y >= (maxY - 1).toFloat() && abs(vy) < 60f && abs(vx) < 60f
                if (!resting) {
                    flingRunnable = this
                    handler.postDelayed(this, 16)
                } else {
                    persistPosition()
                    flingRunnable = null
                }
            }
        }
        flingRunnable = runnable
        handler.post(runnable)
    }

    private fun observeEvents() {
        scope.launch {
            PetBus.events.collect { event ->
                when (event) {
                    is PetEvent.Say -> showBubble(
                        text = event.text,
                        conversationId = event.conversationId,
                        personaId = event.personaId,
                        groupId = event.groupId,
                    )
                    is PetEvent.Clip -> playClip(event.name, event.once)
                    PetEvent.Thinking -> playClip("thinking", once = false)
                    PetEvent.Success -> playClip("success", once = true)
                    PetEvent.Error -> playClip("error", once = true)
                    PetEvent.MotionFrequencyChanged -> scheduleAutonomousAction()
                    PetEvent.RefreshPets -> {
                        refreshExtraPets()
                        applyVisibility()
                    }
                }
            }
        }
    }

    private fun observeAppVisibility() {
        scope.launch {
            AppVisibility.foreground.collect {
                applyVisibility()
                if (!canShowOverlay()) {
                    hideBubble()
                } else if (petView?.drawable == null) {
                    playClip("idle", once = false)
                }
            }
        }
    }

    private val autonomousActionRunnable = object : Runnable {
        override fun run() {
            performAutonomousAction()
            scheduleAutonomousAction()
        }
    }

    private fun scheduleAutonomousAction() {
        handler.removeCallbacks(autonomousActionRunnable)
        val interval = when (settings.desktopMotionFrequency) {
            0 -> 0L
            1 -> 5 * 60_000L
            2 -> 2 * 60_000L
            else -> 45_000L
        }
        if (interval > 0) handler.postDelayed(autonomousActionRunnable, interval)
    }

    private fun performAutonomousAction() {
        if (!canShowOverlay() || dragging || menuView != null) return
        if (System.currentTimeMillis() < actionLockUntil) return
        val clip = autonomousClip() ?: return
        playClip(clip, once = true)
    }

    private fun autonomousClip(): String? {
        val persona = personas.get(mainPersonaId()) ?: personas.first()
        val character = characters.get(persona?.characterId) ?: return null
        val excluded = setOf(
            "idle", "thinking", "error", "dragging", "dragging_release", "success",
        )
        val candidates = character.meta.clips.keys.filterNot { it in excluded }
        if (candidates.isEmpty()) return null
        val pool = if (candidates.size > 1) {
            candidates.filterNot { it == lastAutonomousClip }
        } else {
            candidates
        }
        val chosen = pool.randomOrNull() ?: candidates.randomOrNull()
        lastAutonomousClip = chosen
        return chosen
    }

    private val autoChatRunnable = object : Runnable {
        override fun run() {
            maybeAutoChat()
            handler.postDelayed(this, 60_000)
        }
    }

    private fun scheduleAutoChat() {
        handler.removeCallbacks(autoChatRunnable)
        handler.postDelayed(autoChatRunnable, 60_000)
    }

    private fun maybeAutoChat() {
        if (AppVisibility.foreground.value || autoChatBusy) return
        val group = groupStore.groups().firstOrNull { it.autoChat } ?: return
        val messages = groupStore.messages(group.id)
        val now = System.currentTimeMillis()
        val lastAt = messages.maxOfOrNull { it.at } ?: 0L
        val interval = group.autoChatMinutes.coerceAtLeast(5) * 60_000L
        if (now - lastAt < interval) return
        val lastUserAt = messages.lastOrNull { it.role == ChatMessage.ROLE_USER }?.at ?: 0L
        if (lastUserAt > 0 && now - lastUserAt < 5 * 60_000L) return

        autoChatBusy = true
        scope.launch {
            try {
                val message = groupEngine.autoMessage(group)
                if (message != null) {
                    groupStore.saveMessages(group.id, messages + message)
                    showBubble(
                        text = "${message.senderName ?: ""}：${message.content}",
                        groupId = group.id,
                    )
                }
            } catch (_: Exception) {
                // Offline or API error: try again next tick.
            } finally {
                autoChatBusy = false
            }
        }
    }

    private fun desktopPersonaIds(): Set<String> {
        val stored = settings.desktopPetPersonaIds
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (stored.isNotEmpty()) return stored
        return (
            listOfNotNull(settings.desktopMainPersonaId ?: settings.activePersonaId) +
                settings.extraPetPersonaIds.split(',').filter { it.isNotBlank() }
        ).toSet()
    }

    private fun mainPersonaId(): String? {
        val selected = desktopPersonaIds()
        val main = settings.desktopMainPersonaId
        return main?.takeIf { it in selected } ?: selected.firstOrNull()
    }

    private fun canShowOverlay(): Boolean =
        !AppVisibility.foreground.value || settings.showPetInApp

    private fun applyVisibility() {
        val show = canShowOverlay() && mainPersonaId() != null
        petView?.visibility = if (show) View.VISIBLE else View.GONE
        extraPets.forEach { it.view.visibility = if (show) View.VISIBLE else View.GONE }
    }

    private fun playClip(name: String, once: Boolean, priority: Boolean = false) {
        if (!priority && System.currentTimeMillis() < actionLockUntil) return
        personas.reload()
        val persona = personas.get(mainPersonaId()) ?: return
        val character = characters.get(persona?.characterId) ?: characters.list().firstOrNull() ?: return
        val drawable = characters.loadClip(character, name)
            ?: characters.previewBitmap(character)?.let { BitmapDrawable(resources, it) }
            ?: characters.list().firstOrNull { it.isBuiltIn }?.let { fallback ->
                characters.loadClip(fallback, name)
            }
            ?: return

        petView?.setImageDrawable(drawable)
        petView?.scaleType = scaleTypeFor(character)
        if (drawable is AnimatedImageDrawable) drawable.start()
        if (canShowOverlay()) {
            petView?.visibility = View.VISIBLE
        }

        // Keep the window size tied to the user's scale, not the clip's intrinsic size.
        val params = petParams
        if (params != null) {
            val expectedWidth = (baseWidth * settings.overlayScale).toInt().coerceAtLeast(dp(40))
            val expectedHeight = (baseHeight * settings.overlayScale).toInt().coerceAtLeast(dp(45))
            if (params.width != expectedWidth || params.height != expectedHeight) {
                params.width = expectedWidth
                params.height = expectedHeight
                petView?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
                moveBubble()
            }
        }
        petView?.scaleType = scaleTypeFor(character)

        revert?.let(handler::removeCallbacks)
        revert = null

        val duration = character.meta.clips[name]
            ?.durationMs
            ?.takeIf { it > 0 }
            ?: 2200
        if (priority) {
            actionLockUntil = System.currentTimeMillis() + duration + 800L
        }
        if (once) {
            val runnable = Runnable { playClip("idle", once = false, priority = true) }
            revert = runnable
            handler.postDelayed(runnable, duration.toLong() + 60L)
        }
    }

    private fun showBubble(
        text: String,
        conversationId: String? = null,
        personaId: String? = null,
        groupId: String? = null,
        anchor: WindowManager.LayoutParams? = null,
    ) {
        if (!canShowOverlay()) return
        bubbleConversationId = conversationId
        bubblePersonaId = personaId
        bubbleGroupId = groupId
        hideBubble()
        bubbleAnchor = anchor
        val bubbleGesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                hideBubble()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                hideBubble()
                openChat()
                return true
            }
        })
        val view = TextView(this).apply {
            this.text = text.take(220)
            setTextColor(settings.bubbleTextColor)
            textSize = 15f
            maxLines = 5
            maxWidth = dp(260)
            minWidth = dp(80)
            setLineSpacing(dp(2).toFloat(), 1f)
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(16), dp(11), dp(16), dp(20))
            background = BubbleDrawable(
                fillColor = settings.bubbleColor,
                strokeColor = 0x33000000,
                density = resources.displayMetrics.density,
            )
            elevation = dp(8).toFloat()
            setOnTouchListener { _, event ->
                bubbleGesture.onTouchEvent(event)
                true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val pet = anchor ?: petParams
            x = (pet?.x ?: 0) + (pet?.width ?: 0) / 2
            y = (pet?.y ?: 0) - dp(90)
        }

        view.alpha = 0f
        bubbleView = view
        bubbleParams = params
        runCatching { windowManager.addView(view, params) }
        view.post {
            moveBubble()
            view.alpha = 1f
        }

        handler.postDelayed(hideBubbleRunnable, 7_000)
    }

    private fun moveBubble() {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val pet = bubbleAnchor ?: petParams ?: return
        val area = safeArea()
        val margin = dp(8)
        var x = pet.x + (pet.width - view.width) / 2 + bubbleOffsetX
        if (x < margin) x = (pet.x + pet.width + margin).toFloat()
        if (x + view.width > area.right - margin) x = (pet.x - view.width - margin).toFloat()
        params.x = x.toInt().coerceIn(margin, max(margin, area.right - view.width - margin))
        var y = pet.y - view.height - dp(8) + bubbleOffsetY
        if (y < area.top + margin) y = (pet.y + pet.height + margin).toFloat()
        params.y = y.toInt().coerceIn(area.top + margin, max(area.top + margin, area.bottom - view.height - margin))
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun hideBubble() {
        handler.removeCallbacks(hideBubbleRunnable)
        val view = bubbleView ?: return
        bubbleView = null
        bubbleParams = null
        bubbleAnchor = null
        runCatching { windowManager.removeView(view) }
    }

    private val hideBubbleRunnable = Runnable { hideBubble() }

    private fun openChat() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_CHAT, true)
            bubbleConversationId?.let { putExtra(MainActivity.EXTRA_OPEN_CONVERSATION, it) }
            bubbleGroupId?.let { putExtra(MainActivity.EXTRA_OPEN_GROUP, it) }
            bubblePersonaId?.let { putExtra(MainActivity.EXTRA_OPEN_PERSONA, it) }
        }
        runCatching { startActivity(intent) }
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class SafeArea(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private fun safeArea(): SafeArea {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val modern = runCatching {
                val metrics = windowManager.currentWindowMetrics
                val bounds = metrics.bounds
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                SafeArea(
                    left = bounds.left + insets.left,
                    top = bounds.top + insets.top,
                    right = bounds.right - insets.right,
                    bottom = bounds.bottom - insets.bottom,
                )
            }.getOrNull()
            if (modern != null && modern.right > modern.left && modern.bottom > modern.top) {
                return modern
            }
        }
        return legacySafeArea()
    }

    @Suppress("DEPRECATION")
    private fun legacySafeArea(): SafeArea {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val status = systemDimen("status_bar_height", dp(24))
        val nav = systemDimen("navigation_bar_height", dp(48))
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            SafeArea(0, status, metrics.widthPixels - nav, metrics.heightPixels)
        } else {
            SafeArea(0, status, metrics.widthPixels, metrics.heightPixels - nav)
        }
    }

    private fun systemDimen(name: String, fallback: Int): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else fallback
    }

    private fun visibleX(width: Int): Int =
        min(dp(64), (width * 0.35f).toInt()).coerceAtLeast(dp(24))

    private fun visibleY(height: Int): Int =
        min(dp(64), (height * 0.35f).toInt()).coerceAtLeast(dp(24))

    /** left, right, top, bottom for a window of this size, in screen coordinates. */
    private fun boundsFor(width: Int, height: Int, area: SafeArea = safeArea()): IntArray {
        val minX = area.left - width + visibleX(width)
        val maxX = area.right - visibleX(width)
        // Keep the pet fully inside the safe area vertically: never let it drop
        // below the navigation bar/gesture area again.
        val minY = area.top + dp(4)
        val maxY = (area.bottom - height - dp(28)).coerceAtLeast(minY)
        return intArrayOf(minX, maxX, minY, maxY)
    }

    private fun clampX(x: Int, width: Int): Int {
        val bounds = boundsFor(width, 1)
        if (bounds[1] < bounds[0]) return bounds[0]
        return x.coerceIn(bounds[0], bounds[1])
    }

    private fun clampY(y: Int, height: Int): Int {
        val bounds = boundsFor(1, height)
        if (bounds[3] < bounds[2]) return bounds[2]
        return y.coerceIn(bounds[2], bounds[3])
    }

    private fun persistPosition() {
        val params = petParams ?: return
        settings.overlayX = params.x
        settings.overlayY = params.y
        val bounds = boundsFor(params.width, params.height)
        val spanX = bounds[1] - bounds[0]
        val spanY = bounds[3] - bounds[2]
        settings.overlayXF = if (spanX > 0) {
            ((params.x - bounds[0]).toFloat() / spanX).coerceIn(0f, 1f)
        } else {
            0f
        }
        settings.overlayYF = if (spanY > 0) {
            ((params.y - bounds[2]).toFloat() / spanY).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    private val touchSlop: Int by lazy {
        android.view.ViewConfiguration.get(this).scaledTouchSlop
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, PetOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val reset = PendingIntent.getService(
            this,
            2,
            Intent(this, PetOverlayService::class.java).setAction(ACTION_RESET_POSITION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("点一下回到聊天")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "找回桌宠", reset)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pet_overlay"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.dsh.companion.action.STOP_OVERLAY"
        const val ACTION_RESET_POSITION = "com.dsh.companion.action.RESET_POSITION"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PetOverlayService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PetOverlayService::class.java))
        }
    }
}
