package com.dsh.companion.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
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

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var startX = 0
    private var startY = 0
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
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

    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var collapsed = false

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
        groupStore = GroupStore(this)
        groupEngine = GroupChatEngine(this)

        showPet()
        playClip("idle", once = false)
        observeEvents()
        observeAppVisibility()
        scheduleAutoChat()
        handler.postDelayed({
            if (!AppVisibility.foreground.value) {
                showBubble("我在这儿。点一下戳我，双击打开聊天。")
            }
        }, 1600)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacks(autoChatRunnable)
        flingRunnable?.let(handler::removeCallbacks)
        velocityTracker?.recycle()
        menuView?.let { runCatching { windowManager.removeView(it) } }
        menuView = null
        handler.removeCallbacksAndMessages(null)
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
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

        val metrics = resources.displayMetrics
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
            x = if (settings.overlayX >= 0) settings.overlayX else metrics.widthPixels - width - dp(6)
            y = if (settings.overlayY >= 0) settings.overlayY else (metrics.heightPixels * 0.32f).toInt()
            x = x.coerceIn(0, max(0, metrics.widthPixels - width))
            y = y.coerceIn(0, max(0, metrics.heightPixels - height))
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
                    playClip("poke", once = true)
                    showBubble(pokeLines.random())
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
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    if (!scaleDetector.isInProgress) {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            dragging = true
                        }
                        if (dragging) {
                            params.x = (startX + dx.toInt())
                                .coerceIn(0, max(0, metrics.widthPixels - params.width))
                            params.y = (startY + dy.toInt())
                                .coerceIn(0, max(0, metrics.heightPixels - params.height))
                            runCatching { windowManager.updateViewLayout(image, params) }
                            moveBubble()
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.addMovement(event)
                    if (dragging) {
                        settings.overlayX = params.x
                        settings.overlayY = params.y
                        playClip("dragging_release", once = true)
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vx = velocityTracker?.xVelocity ?: 0f
                        val vy = velocityTracker?.yVelocity ?: 0f
                        if (abs(vx) > 250f || abs(vy) > 250f) {
                            fling(vx, vy)
                        }
                    }
                    dragging = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                }
            }
            true
        }

        petView = image
        petParams = params
        windowManager.addView(image, params)
        applyBrightness()
        applyFlip()
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
        params.x = params.x.coerceIn(0, max(0, metrics.widthPixels - nextWidth))
        params.y = params.y.coerceIn(0, max(0, metrics.heightPixels - nextHeight))
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
        val metrics = resources.displayMetrics
        params.x = metrics.widthPixels - params.width - dp(6)
        params.y = metrics.heightPixels - params.height - dp(12)
        runCatching { windowManager.updateViewLayout(image, params) }
        settings.overlayX = params.x
        settings.overlayY = params.y
        collapsed = false
        moveBubble()
    }

    private fun collapseToSide() {
        val params = petParams ?: return
        val image = petView ?: return
        val metrics = resources.displayMetrics
        params.x = metrics.widthPixels - params.width / 5
        params.y = (metrics.heightPixels * 0.65f).toInt()
            .coerceIn(0, max(0, metrics.heightPixels - params.height))
        runCatching { windowManager.updateViewLayout(image, params) }
        settings.overlayX = params.x
        settings.overlayY = params.y
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
        val clips = listOf("poke", "head_pat", "tail", "eat_token")
        playClip(clips.random(), once = true)
    }

    private fun hideMenu() {
        val view = menuView ?: return
        menuView = null
        menuParams = null
        runCatching { windowManager.removeView(view) }
    }

    private fun showMenu() {
        hideMenu()
        val metrics = resources.displayMetrics
        val panelWidth = dp(250)
        val panelHeight = (metrics.heightPixels * 0.82f).toInt()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(8))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(box)
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(0xFFFFFBF0.toInt())
                setStroke(dp(1), 0x33C9A96A)
            }
        }

        val title = TextView(this).apply {
            text = "桌宠设置"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF2F2A22.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        box.addView(title)

        fun addItem(label: String, action: () -> Unit) {
            val item = TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(0xFF2F2A22.toInt())
                setPadding(dp(16), dp(13), dp(16), dp(13))
                isClickable = true
                setOnClickListener { action() }
            }
            box.addView(item)
        }

        fun addDivider() {
            box.addView(View(this).apply {
                setBackgroundColor(0x22A08050)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(1),
                )
            })
        }

        addItem("点击回应：${if (settings.tapResponds) "开" else "关"}") {
            settings.tapResponds = !settings.tapResponds
            hideMenu()
        }
        addItem("随机动作") {
            randomAction()
            hideMenu()
        }
        addItem("待机") {
            playClip("idle", once = false)
            hideMenu()
        }
        addItem("转向") {
            settings.overlayFlipped = !settings.overlayFlipped
            applyFlip()
            hideMenu()
        }
        addItem("摸头") {
            playClip("head_pat", once = true)
            showBubble("呼噜呼噜…")
            hideMenu()
        }
        addItem("漫游") {
            wander()
            hideMenu()
        }
        addDivider()

        val sizeLabel = TextView(this).apply {
            text = "大小：${(settings.overlayScale * 100).toInt()}%"
            textSize = 15f
            setTextColor(0xFF8A6A24.toInt())
            setPadding(dp(16), dp(10), dp(16), 0)
        }
        box.addView(sizeLabel)
        val sizeSeek = SeekBar(this).apply {
            max = 260
            progress = ((settings.overlayScale * 100).toInt() - 40).coerceIn(0, 260)
            setPadding(dp(14), dp(2), dp(14), dp(2))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val percent = progress + 40
                    sizeLabel.text = "大小：$percent%"
                    applyScale(percent / 100f)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        box.addView(sizeSeek)

        val brightnessLabel = TextView(this).apply {
            text = "亮度：${(settings.overlayBrightness * 100).toInt()}%"
            textSize = 15f
            setTextColor(0xFF8A6A24.toInt())
            setPadding(dp(16), dp(10), dp(16), 0)
        }
        box.addView(brightnessLabel)
        val brightnessSeek = SeekBar(this).apply {
            max = 100
            progress = ((settings.overlayBrightness * 100).toInt() - 50).coerceIn(0, 100)
            setPadding(dp(14), dp(2), dp(14), dp(2))
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
        }
        box.addView(brightnessSeek)

        addItem("恢复默认亮度") {
            settings.overlayBrightness = 1f
            applyBrightness()
            hideMenu()
        }
        addDivider()
        addItem("回到右下角") {
            moveToCorner()
            hideMenu()
        }
        addItem("收起（挂到侧边）") {
            collapseToSide()
            hideMenu()
        }
        addItem("关闭桌宠") {
            stopSelf()
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
            x = if (pet != null && pet.x > panelWidth + dp(12)) {
                pet.x - panelWidth - dp(8)
            } else {
                (pet?.x ?: 0) + (pet?.width ?: 0) + dp(8)
            }
            x = x.coerceIn(0, max(0, metrics.widthPixels - panelWidth))
            y = (pet?.y ?: dp(40)).coerceIn(0, max(0, metrics.heightPixels - panelHeight))
        }

        menuView = scroll
        menuParams = params
        runCatching { windowManager.addView(scroll, params) }
    }

    private fun fling(vx0: Float, vy0: Float) {
        val image = petView ?: return
        val params = petParams ?: return
        val metrics = resources.displayMetrics
        val maxX = max(0, metrics.widthPixels - params.width)
        val maxY = max(0, metrics.heightPixels - params.height)
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

                if (x < 0) {
                    x = 0f
                    vx = -vx * 0.62f
                } else if (x > maxX.toFloat()) {
                    x = maxX.toFloat()
                    vx = -vx * 0.62f
                }
                if (y < 0) {
                    y = 0f
                    vy = -vy * 0.55f
                } else if (y > maxY.toFloat()) {
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
                    settings.overlayX = params.x
                    settings.overlayY = params.y
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
                    is PetEvent.Say -> showBubble(event.text)
                    is PetEvent.Clip -> playClip(event.name, event.once)
                    PetEvent.Thinking -> playClip("thinking", once = false)
                    PetEvent.Success -> playClip("success", once = true)
                    PetEvent.Error -> playClip("error", once = true)
                }
            }
        }
    }

    private fun observeAppVisibility() {
        scope.launch {
            AppVisibility.foreground.collect { foreground ->
                petView?.visibility = if (foreground) View.GONE else View.VISIBLE
                if (foreground) hideBubble()
            }
        }
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
                    showBubble("${message.senderName ?: ""}：${message.content}")
                }
            } catch (_: Exception) {
                // Offline or API error: try again next tick.
            } finally {
                autoChatBusy = false
            }
        }
    }

    private fun playClip(name: String, once: Boolean) {
        val persona = personas.get(settings.activePersonaId) ?: personas.first()
        val character = characters.get(persona?.characterId) ?: characters.list().firstOrNull() ?: return
        val drawable = characters.loadClip(character, name) ?: return

        petView?.setImageDrawable(drawable)
        (drawable as? AnimatedImageDrawable)?.start()

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

    private fun showBubble(text: String) {
        hideBubble()
        val view = TextView(this).apply {
            this.text = text.take(220)
            setTextColor(settings.bubbleTextColor)
            textSize = 14f
            maxLines = 5
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(14), dp(10), dp(14), dp(18))
            background = BubbleDrawable(
                fillColor = settings.bubbleColor,
                strokeColor = 0x33FFFFFF,
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
                            hideBubble()
                            openChat()
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
        }

        bubbleView = view
        bubbleParams = params
        runCatching { windowManager.addView(view, params) }
        view.post { moveBubble() }

        handler.postDelayed(hideBubbleRunnable, 10_000)
    }

    private fun moveBubble() {
        val view = bubbleView ?: return
        val params = bubbleParams ?: return
        val pet = petParams ?: return
        val metrics = resources.displayMetrics
        params.x = (pet.x + (pet.width - view.width) / 2 + bubbleOffsetX)
            .toInt()
            .coerceIn(0, max(0, metrics.widthPixels - view.width))
        params.y = (pet.y - view.height + dp(14) + bubbleOffsetY)
            .toInt()
            .coerceAtLeast(0)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun hideBubble() {
        handler.removeCallbacks(hideBubbleRunnable)
        val view = bubbleView ?: return
        bubbleView = null
        bubbleParams = null
        runCatching { windowManager.removeView(view) }
    }

    private val hideBubbleRunnable = Runnable { hideBubble() }

    private fun openChat() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_CHAT, true)
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("点一下回到聊天")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pet_overlay"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.dsh.companion.action.STOP_OVERLAY"

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
