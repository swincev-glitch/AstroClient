package com.astral.client

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import kotlin.math.abs
import kotlin.math.min

private const val ACTION_STOP = "com.astral.client.STOP"

private const val C_PANEL = 0xF00E0C1E.toInt()
private const val C_DIVIDER = 0xFF26224F.toInt()
private const val C_BORDER = 0xFF34306A.toInt()
private const val C_ROW = 0x0FFFFFFF
private const val C_ROW_SELECTED = 0x298B7CFF
private const val C_TEXT = 0xFFF2F0FF.toInt()
private const val C_MUTED = 0xFFA9A5CC.toInt()
private const val C_ACCENT = 0xFF8B7CFF.toInt()
private const val C_CYAN = 0xFF4FD1E8.toInt()
private const val C_BG = 0xFF0B0A14.toInt()
private const val C_TRACK_OFF = 0xFF3A3668.toInt()
private const val C_RESET = 0xFFFF9E9E.toInt()

/**
 * Плавающая иконка Astral и меню модулей.
 * Иконка видна только пока Minecraft на переднем плане. Если игра закрыта, сервис сам останавливается.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var icon: View? = null
    private var menu: View? = null

    private var polling = false
    private var currentApp: String? = null
    private var lastQuery = 0L
    private var awaySince = 0L

    private val poller = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (icon == null && Settings.canDrawOverlays(this)) showIcon()
        if (!polling) {
            polling = true
            awaySince = SystemClock.elapsedRealtime()
            handler.post(poller)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        polling = false
        closeMenu(restoreIcon = false)
        icon?.let { runCatching { wm.removeView(it) } }
        icon = null
        super.onDestroy()
    }

    // ---------- Слежение за игрой ----------

    private fun tick() {
        if (!Access.hasUsage(this)) return // без доступа не можем следить: иконка остаётся

        val inGame = readForegroundApp() == MINECRAFT_PACKAGE
        val now = SystemClock.elapsedRealtime()

        if (inGame) {
            awaySince = 0L
            if (menu == null) icon?.visibility = View.VISIBLE
        } else {
            icon?.visibility = View.GONE
            closeMenu(restoreIcon = false)
            if (awaySince == 0L) awaySince = now
            // Игра закрыта или давно свёрнута: выключаем иконку полностью.
            if (now - awaySince > 25_000) stopSelf()
        }
    }

    /** Какое приложение сейчас на экране (по событиям статистики, читаем только новое). */
    private fun readForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = if (lastQuery == 0L) end - 2 * 3600_000L else lastQuery - 2000L
        lastQuery = end

        val events = usm.queryEvents(begin, end)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                1 -> currentApp = e.packageName // на передний план
                2 -> if (e.packageName == currentApp) currentApp = null // ушло с переднего плана
            }
        }
        return currentApp
    }

    // ---------- Уведомление ----------

    private fun startAsForeground() {
        val channelId = "astral_overlay"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Astral Client", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Astral Client")
            .setContentText("Иконка активна в игре")
            .setSmallIcon(android.R.drawable.star_on)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Выключить",
                    stop,
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------- Плавающая иконка ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun showIcon() {
        val size = dp(52)
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(110)
        }

        val view = StarIconView(this)
        // Пока не знаем, что игра на экране, иконку не показываем.
        if (Access.hasUsage(this)) view.visibility = View.GONE

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        wm.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) openMenu()
            }
            true
        }

        wm.addView(view, params)
        icon = view
    }

    // ---------- Меню ----------

    private fun openMenu() {
        if (menu != null) return
        icon?.visibility = View.GONE

        val dm = resources.displayMetrics
        val width = min(dp(700), (dm.widthPixels * 0.94f).toInt())
        val height = min(dp(300), (dm.heightPixels * 0.9f).toInt())

        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (dm.widthPixels - width) / 2
            y = (dm.heightPixels - height) / 2
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        val panel = buildPanel(params)
        wm.addView(panel, params)
        menu = panel
    }

    private fun closeMenu(restoreIcon: Boolean = true) {
        menu?.let { runCatching { wm.removeView(it) } }
        menu = null
        if (restoreIcon) icon?.visibility = View.VISIBLE
    }

    // ---------- Помощники для построения интерфейса ----------

    private fun shape(color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun tv(text: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun makeSwitch(checked: Boolean, onChange: (Boolean) -> Unit): Switch {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        return Switch(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList(states, intArrayOf(C_TEXT, C_MUTED))
            trackTintList = ColorStateList(states, intArrayOf(C_ACCENT, C_TRACK_OFF))
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
    }

    private fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(tv(label, 13f, C_TEXT), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(makeSwitch(checked, onChange))
        return row
    }

    private fun seekRow(label: String, lo: Int, hi: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val valueText = tv(fmt(value), 12f, C_TEXT, bold = true)
        head.addView(tv(label, 12f, C_MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(valueText)
        box.addView(head)

        val seek = SeekBar(this).apply {
            max = hi - lo
            progress = value - lo
            progressTintList = ColorStateList.valueOf(C_ACCENT)
            thumbTintList = ColorStateList.valueOf(C_ACCENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress + lo
                    valueText.text = fmt(v)
                    onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        box.addView(seek, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)))
        return box
    }

    // ---------- Окно меню ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel(params: WindowManager.LayoutParams): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(C_PANEL, 18, C_BORDER)
            clipToOutline = true
        }

        var query = ""
        var selected = Modules.all.first().id

        // ----- Шапка -----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(4), 0)
        }
        header.addView(StarIconView(this), LinearLayout.LayoutParams(dp(26), dp(26)))
        header.addView(tv("Astral Client", 14f, C_TEXT, bold = true).apply {
            setPadding(dp(10), 0, dp(10), 0)
            setSingleLine()
        })

        val search = EditText(this).apply {
            hint = "Поиск модулей"
            setHintTextColor(C_MUTED)
            setTextColor(C_TEXT)
            textSize = 13f
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            background = shape(0x14FFFFFF, 17)
            setPadding(dp(14), 0, dp(14), 0)
        }
        header.addView(search, LinearLayout.LayoutParams(0, dp(34), 1f))

        val resetBtn = tv("Сброс", 12f, C_RESET, bold = true).apply {
            gravity = Gravity.CENTER
            background = shape(0x00000000, 9, 0xFFFF8A8A.toInt())
            setPadding(dp(12), 0, dp(12), 0)
        }
        header.addView(resetBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply {
            marginStart = dp(10)
        })

        val headerName = tv("", 12f, C_TEXT, bold = true).apply {
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(84)
            setPadding(dp(8), 0, dp(4), 0)
        }
        val headerSwitchHolder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(headerSwitchHolder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply {
            marginStart = dp(8)
        })

        header.addView(tv("×", 26f, C_MUTED).apply {
            gravity = Gravity.CENTER
            setOnClickListener { closeMenu() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))

        // Окно можно двигать за шапку (кроме поля поиска и кнопок)
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    wm.updateViewLayout(panel, params)
                }
            }
            true
        }
        panel.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        panel.addView(View(this).apply { setBackgroundColor(C_DIVIDER) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // ----- Тело: слева список модулей, справа настройки -----
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        body.addView(ScrollView(this).apply { addView(listBox) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        body.addView(View(this).apply { setBackgroundColor(C_DIVIDER) }, LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT))

        val detail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        body.addView(ScrollView(this).apply { addView(detail) }, LinearLayout.LayoutParams(dp(260), LinearLayout.LayoutParams.MATCH_PARENT))

        panel.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        lateinit var select: (String) -> Unit

        // Переключатель выбранного модуля в шапке
        fun refreshHeader() {
            headerSwitchHolder.removeAllViews()
            val m = Modules.all.first { it.id == selected }
            headerSwitchHolder.addView(makeSwitch(Modules.isOn(this, m.id)) { v ->
                Modules.set(this, m.id, v)
            }.apply { scaleX = 0.85f; scaleY = 0.85f })
            headerName.text = m.name
            headerSwitchHolder.addView(headerName)
        }

        fun renderDetail() {
            detail.removeAllViews()
            val m = Modules.all.first { it.id == selected }
            val gap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }

            detail.addView(tv(m.name, 17f, C_TEXT, bold = true))
            detail.addView(tv(m.hint, 12f, C_MUTED))

            val keyRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            keyRow.addView(tv("Клавиша: Нет", 12f, C_MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            keyRow.addView(tv("Сменить", 12f, C_BG, bold = true).apply {
                gravity = Gravity.CENTER
                background = shape(C_ACCENT, 10)
                setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener {
                    Toast.makeText(this@OverlayService, "Назначение клавиш появится позже", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)))
            detail.addView(keyRow, gap)

            detail.addView(tv("HUD", 11f, C_CYAN, bold = true).apply { letterSpacing = 0.1f }, gap)
            detail.addView(switchRow("Показывать", Modules.hud(this, m.id)) { Modules.setHud(this, m.id, it) })
            detail.addView(switchRow("Шрифт Minecraft", Modules.font(this, m.id)) { Modules.setFont(this, m.id, it) })

            detail.addView(
                seekRow("Масштаб", 50, 200, Modules.scale(this, m.id), { "$it%" }) { Modules.setScale(this, m.id, it) },
                gap,
            )
            detail.addView(
                seekRow("Прозрачность", 0, 100, Modules.opacity(this, m.id), { "%.1f".format(it / 100f) }) {
                    Modules.setOpacity(this, m.id, it)
                },
                gap,
            )
        }

        fun renderList() {
            listBox.removeAllViews()
            val items = Modules.all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

            if (items.isEmpty()) {
                listBox.addView(tv("Ничего не найдено", 13f, C_MUTED).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(24), 0, 0)
                })
                return
            }

            fun item(m: Module): View {
                val picked = m.id == selected
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = shape(
                        if (picked) C_ROW_SELECTED else C_ROW,
                        10,
                        if (picked) C_ACCENT else null,
                    )
                    setPadding(dp(6), 0, dp(8), 0)
                    setOnClickListener { select(m.id) }
                }
                row.addView(makeSwitch(Modules.isOn(this, m.id)) { v ->
                    Modules.set(this, m.id, v)
                    if (m.id == selected) refreshHeader()
                }.apply { scaleX = 0.8f; scaleY = 0.8f })
                row.addView(tv(m.name, 13f, C_TEXT).apply {
                    setSingleLine()
                    ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(tv("›", 20f, C_MUTED))
                return row
            }

            for (pair in items.chunked(2)) {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (m in pair) {
                    line.addView(item(m), LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    })
                }
                if (pair.size == 1) {
                    line.addView(View(this), LinearLayout.LayoutParams(0, dp(40), 1f))
                }
                listBox.addView(line)
            }
        }

        select = { id ->
            selected = id
            refreshHeader()
            renderDetail()
            renderList()
        }

        search.doAfterTextChanged {
            query = it?.toString().orEmpty()
            renderList()
        }

        resetBtn.setOnClickListener {
            Modules.reset(this, selected)
            select(selected)
        }

        // Поле поиска: окно становится «фокусируемым» только пока идёт ввод, иначе клавиатура не откроется
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        search.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN &&
                (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0
            ) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                params.y = dp(8)
                wm.updateViewLayout(panel, params)
                v.post {
                    v.requestFocus()
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            false
        }
        search.setOnEditorActionListener { v, _, _ ->
            imm.hideSoftInputFromWindow(v.windowToken, 0)
            v.clearFocus()
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            wm.updateViewLayout(panel, params)
            true
        }

        select(selected)
        return panel
    }
}

/** Круглая иконка со звездой и орбитой. */
private class StarIconView(context: Context) : View(context) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C_BG }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_CYAN
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C_ACCENT }
    private val orbit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = C_CYAN
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val c = w / 2f
        val inset = ring.strokeWidth / 2f
        canvas.drawCircle(c, c, c - inset, bg)
        canvas.drawCircle(c, c, c - inset, ring)

        val r = w * 0.28f
        val k = 0.22f
        val path = Path().apply {
            moveTo(c, c - r)
            lineTo(c + r * k, c - r * k)
            lineTo(c + r, c)
            lineTo(c + r * k, c + r * k)
            lineTo(c, c + r)
            lineTo(c - r * k, c + r * k)
            lineTo(c - r, c)
            lineTo(c - r * k, c - r * k)
            close()
        }
        canvas.drawPath(path, star)

        canvas.save()
        canvas.rotate(-25f, c, c)
        canvas.drawOval(RectF(c - r * 1.5f, c - r * 0.55f, c + r * 1.5f, c + r * 0.55f), orbit)
        canvas.restore()
    }
}
