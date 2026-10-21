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
            if (!AppVisibility.foreground.value) {
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
        petView?.visibility = if (AppVisibility.foreground.value) View.GONE else View.VISIBLE
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
        val ids = settings.extraPetPersonaIds
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != active }
            .distinct()
            .take(3)
        val area = safeArea()
        ids.forEachIndexed { index, id ->
            val persona = personas.get(id) ?: return@forEachIndexed
            val character = characters.get(persona.characterId) ?: return@forEachIndexed
            val width = (dp(160) * settings.overlayScale).toInt().coerceAtLeast(dp(40))
            val height = (dp(180) * settings.overlayScale).toInt().coerceAtLeast(dp(45))
            val view = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = persona.name
                setImageDrawable(characters.loadClip(character, "idle"))
                (drawable as? AnimatedImageDrawable)?.start()
                visibility = if (AppVisibility.foreground.value) View.GONE else View.VISIBLE
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
                    // Conversation is opened from the speech bubble (or long-press menu),
                    // so a quick double tap on the pet itself does not jump to chat.
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
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        moved = false
                        longPressed = false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                        params.x = clampX(startX + dx.toInt(), width)
                        params.y = clampY(startY + dy.toInt(), height)
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (longPressed && !moved) {
                            longPressed = false
                            promoteExtraToMain(persona.id)
                        }
                    }
                }
                true
            }
            runCatching { windowManager.addView(view, params) }
            extraPets.add(ExtraPet(persona.id, character, view, params))
        }
        extraPets.forEach { scheduleExtraAction(it) }
    }

    private fun scheduleExtraAction(extra: ExtraPet) {
        extra.actionRunnable?.let(handler::removeCallbacks)
        val runnable = object : Runnable {
            override fun run() {
                if (!AppVisibility.foreground.value) {
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
            runCatching { windowManager.removeView(pet.view) }
        }
        extraPets.clear()
    }

    private fun playClipOnView(character: Character, view: ImageView, name: String) {
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
        handler.postDelayed({
            val idle = characters.loadClip(character, character.meta.defaultClip)
            if (idle != null) {
                view.setImageDrawable(idle)
                if (idle is AnimatedImageDrawable) idle.start()
            }
        }, duration + 60L)
    }

    private fun promoteExtraToMain(personaId: String) {
        settings.desktopMainPersonaId = personaId
        personas.reload()
        refreshExtraPets()
        playClip("idle", once = false)
        showMenu()
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

    private fun randomAction() {
        autonomousClip()?.let { playClip(it, once = true) }
    }

    private fun hideMenu() {
        menuView?.let { runCatching { windowManager.removeView(it) } }
        scrimView?.let { runCatching { windowManager.removeView(it) } }
        menuView = null
        menuParams = null
        scrimView = null
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

    private fun showMenu() {
        hideMenu()
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
            randomAction()
            hideMenu()
        }

        val sizeLabel = TextView(this).apply {
            text = "桌宠大小：${(settings.overlayScale * 100).toInt()}%"
            textSize = 15f * scale
            setTextColor(0xFF8A6A24.toInt())
            setPadding(sdp(16), sdp(9), sdp(16), 0)
        }
        box.addView(sizeLabel)
        box.addView(SeekBar(this).apply {
            max = 260
            progress = ((settings.overlayScale * 100).toInt() - 40).coerceIn(0, 260)
            setPadding(sdp(14), sdp(2), sdp(14), sdp(2))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val percent = progress + 40
                    sizeLabel.text = "桌宠大小：$percent%"
                    applyScale(percent / 100f)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })

        val brightnessLabel = TextView(this).apply {
            text = "亮度：${(settings.overlayBrightness * 100).toInt()}%"
            textSize = 15f * scale
            setTextColor(0xFF8A6A24.toInt())
            setPadding(sdp(16), sdp(9), sdp(16), 0)
        }
        box.addView(brightnessLabel)
        box.addView(SeekBar(this).apply {
            max = 100
            progress = ((settings.overlayBrightness * 100).toInt() - 50).coerceIn(0, 100)
            setPadding(sdp(14), sdp(2), sdp(14), sdp(2))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val percent = progress + 50
                    brightnessLabel.text = "亮度：$percent%"
                    settings.overlayBrightness = percent / 100f
                    applyBrightness()
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
        hideMenu()
        val persona = personas.get(mainPersonaId()) ?: personas.first()
        val character = characters.get(persona?.characterId) ?: characters.list().firstOrNull() ?: return
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
                        playClip(name, once = !clip.loop)
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
            val pet = petParams
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
                    vx = -vx * 0.62f
                } else if (x > maxX) {
                    x = maxX.toFloat()
                    vx = -vx * 0.62f
                }
                if (y < minY) {
                    y = minY.toFloat()
                    vy = -vy * 0.55f
                } else if (y > maxY) {
                    y = maxY.toFloat()
                    vy = -vy * 0.55f
                    vx *= 0.86f
                }

                params.x = x.toInt()
                params.y = y.toInt()
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
                    PetEvent.RefreshPets -> refreshExtraPets()
                }
            }
        }
    }

    private fun observeAppVisibility() {
        scope.launch {
            AppVisibility.foreground.collect { foreground ->
                petView?.visibility = if (foreground) View.GONE else View.VISIBLE
                extraPets.forEach { extra ->
                    extra.view.visibility = if (foreground) View.GONE else View.VISIBLE
                }
                if (foreground) {
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
        if (AppVisibility.foreground.value || dragging || menuView != null) return
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

    private fun mainPersonaId(): String? =
        settings.desktopMainPersonaId ?: settings.activePersonaId

    private fun playClip(name: String, once: Boolean) {
        personas.reload()
        val persona = personas.get(mainPersonaId()) ?: personas.first()
        val character = characters.get(persona?.characterId) ?: characters.list().firstOrNull() ?: return
        val drawable = characters.loadClip(character, name)
            ?: characters.previewBitmap(character)?.let { BitmapDrawable(resources, it) }
            ?: characters.list().firstOrNull { it.isBuiltIn }?.let { fallback ->
                characters.loadClip(fallback, name)
            }
            ?: return

        petView?.setImageDrawable(drawable)
        if (drawable is AnimatedImageDrawable) drawable.start()
        if (!AppVisibility.foreground.value) {
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
        petView?.scaleType = ImageView.ScaleType.FIT_CENTER

        revert?.let(handler::removeCallbacks)
        revert = null

        if (once) {
            val meta = character.meta.clips[name] ?: character.meta.clips[character.meta.defaultClip]
            val duration = meta?.durationMs?.takeIf { it > 0 } ?: 2200
            val runnable = Runnable { playClip("idle", once = false) }
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
        if (AppVisibility.foreground.value) return
        bubbleConversationId = conversationId
        bubblePersonaId = personaId
        bubbleGroupId = groupId
        hideBubble()
        bubbleAnchor = anchor
        val view = TextView(this).apply {
            this.text = text.take(220)
            setTextColor(settings.bubbleTextColor)
            textSize = 15f
            maxLines = 5
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
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        bubbleDownX = event.rawX
                        bubbleDownY = event.rawY
                        bubbleStartOffsetX = bubbleOffsetX
                        bubbleStartOffsetY = bubbleOffsetY
                        bubbleMoved = false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - bubbleDownX
                        val dy = event.rawY - bubbleDownY
                        if (!bubbleMoved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            bubbleMoved = true
                        }
                        if (bubbleMoved) {
                            bubbleOffsetX = bubbleStartOffsetX + dx
                            bubbleOffsetY = bubbleStartOffsetY + dy
                            moveBubble()
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!bubbleMoved) {
                            val now = System.currentTimeMillis()
                            if (now - lastBubbleTap in 1..450) {
                                lastBubbleTap = 0L
                                hideBubble()
                                openChat()
                            } else {
                                lastBubbleTap = now
                            }
                        }
                    }
                }
                true
            }
        }

        val params = WindowManager.LayoutParams(
            dp(220),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val pet = anchor ?: petParams
            x = (pet?.x ?: 0) + ((pet?.width ?: 0) - dp(220)) / 2
            y = (pet?.y ?: 0) - dp(80)
        }

        view.alpha = 0f
        bubbleView = view
        bubbleParams = params
        runCatching { windowManager.addView(view, params) }
        view.post {
            moveBubble()
            view.alpha = 1f
        }

        handler.postDelayed(hideBubbleRunnable, 10_000)
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
        var y = pet.y - view.height + dp(14) + bubbleOffsetY
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
