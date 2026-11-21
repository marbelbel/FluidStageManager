package com.fluidos.stagemanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.min

/**
 * User-invoked floating app shelf for Android.
 * It is deliberately NOT a HOME/launcher replacement and does not pretend to own Android Recents.
 */
class OverlayDockService : Service() {
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var panelOpen = false
    private var currentTab = ShelfTab.RECENT
    private var activeWorkspaceId = "main"
    private var currentQuery = ""
    private var appListContainer: LinearLayout? = null
    private var searchField: EditText? = null
    private var tabContainer: LinearLayout? = null
    private var workspaceContainer: LinearLayout? = null
    private var freeformToggle: TextView? = null
    private var cachedApps: List<AppEntry> = emptyList()
    private var cachedRecentApps: List<AppEntry> = emptyList()
    private var catalogLoading = false
    private var catalogLoadToken = 0

    private enum class ShelfTab { RECENT, FAVORITES, ALL, WORKSPACE }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        try {
            startAsForeground()
        } catch (error: Exception) {
            stopSelf()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        isRunning = true
        activeWorkspaceId = WorkspaceStore.activeWorkspaceId(this)
        showHandle()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_OPEN -> showPanel()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val openPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayDockService::class.java).setAction(ACTION_OPEN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, OverlayDockService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Fluid Stage Manager está activo")
            .setContentText("Panel flotante disponible sobre tus aplicaciones")
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desactivar", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showHandle() {
        removeOverlay()
        panelOpen = false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = rounded(0xF21A1D27.toInt(), 0xFF687493.toInt(), 22)
            elevation = dp(18).toFloat()
            isClickable = true
            contentDescription = "Abrir panel Fluid Stage Manager"
        }
        val logo = TextView(this).apply {
            text = "F"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFF151923.toInt())
            background = rounded(0xFFCAD6FF.toInt(), 0xFFE5EBFF.toInt(), 14)
        }
        root.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)))
        val dots = TextView(this).apply {
            text = "•••"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFDCE3F8.toInt())
        }
        root.addView(dots, LinearLayout.LayoutParams(dp(42), dp(22)))
        root.setOnClickListener { showPanel() }
        root.setOnTouchListener(makeDragListener(openOnTap = true))

        val saved = WorkspaceStore.dockPosition(this)
        val display = resources.displayMetrics
        val x = dp(saved.first).coerceIn(0, (display.widthPixels - dp(58)).coerceAtLeast(0))
        val y = dp(saved.second).coerceIn(0, (display.heightPixels - dp(90)).coerceAtLeast(0))
        val lp = baseLayoutParams(dp(56), dp(82)).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        overlayView = root
        overlayParams = lp
        try {
            windowManager.addView(root, lp)
        } catch (_: Exception) {
            overlayView = null
            stopSelf()
        }
    }

    private fun showPanel() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Concede a Fluid el permiso «Mostrar sobre otras aplicaciones».", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        removeOverlay()
        panelOpen = true
        currentQuery = ""
        currentTab = ShelfTab.RECENT
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            background = rounded(0xE51A1D27.toInt(), 0xFF56617B.toInt(), 27)
            elevation = dp(22).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnTouchListener(makeDragListener(openOnTap = false))
        }
        val logo = TextView(this).apply {
            text = "F"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFF111722.toInt())
            background = rounded(0xFFCAD6FF.toInt(), 0xFFE6ECFF.toInt(), 12)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(34), dp(34)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        heading.addView(text("FLUID STAGE", 11f, 0xFFE9EDFA.toInt(), true))
        heading.addView(text("PANEL DE MULTITAREA", 8f, 0xFF9DA8C0.toInt(), true))
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val settings = text("⚙", 20f, 0xFFD7DDF0.toInt(), false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { openSettings() }
        }
        header.addView(settings, LinearLayout.LayoutParams(dp(38), dp(38)))
        val close = text("×", 27f, 0xFFE8EBF4.toInt(), false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showHandle() }
        }
        header.addView(close, LinearLayout.LayoutParams(dp(34), dp(38)))
        panel.addView(header)

        val subtitle = text("Accesos rápidos sobre la aplicación que estés usando", 10f, 0xFF9DA6B9.toInt(), false)
        val subtitleParams = LinearLayout.LayoutParams(-1, -2)
        subtitleParams.topMargin = dp(8)
        subtitleParams.bottomMargin = dp(12)
        panel.addView(subtitle, subtitleParams)

        val freeformSupported = AppCatalog.hasFreeformSupport(this)
        val freeformRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF242A36.toInt(), 0xFF3E475B.toInt(), 13)
            setPadding(dp(11), 0, dp(10), 0)
        }
        val freeformLabel = text("Ventanas libres", 10f, 0xFFD9E0F1.toInt(), true)
        freeformRow.addView(freeformLabel, LinearLayout.LayoutParams(0, dp(38), 1f))
        val freeformStatus = text(
            freeformStateLabel(freeformSupported),
            9f,
            if (WorkspaceStore.isFreeformLaunchEnabled(this)) 0xFFBFD0FF.toInt() else 0xFF9AA5B9.toInt(),
            true
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(4), 0)
        }
        freeformToggle = freeformStatus
        freeformRow.addView(freeformStatus, LinearLayout.LayoutParams(-2, dp(38)))
        freeformRow.setOnClickListener {
            val enabled = !WorkspaceStore.isFreeformLaunchEnabled(this)
            WorkspaceStore.setFreeformLaunchEnabled(this, enabled)
            freeformToggle?.text = freeformStateLabel(freeformSupported)
            freeformToggle?.setTextColor(if (enabled) 0xFFBFD0FF.toInt() else 0xFF9AA5B9.toInt())
            val message = when {
                !enabled -> "Apertura normal activada"
                freeformSupported -> "Se intentará abrir aplicaciones en ventanas libres"
                else -> "Modo experimental: activa ventanas de formato libre en Opciones de desarrollador si está disponible"
            }
            val duration = if (enabled && !freeformSupported) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(this, message, duration).show()
        }
        val freeformParams = LinearLayout.LayoutParams(-1, dp(38))
        freeformParams.bottomMargin = dp(9)
        panel.addView(freeformRow, freeformParams)

        val search = EditText(this).apply {
            hint = "Buscar aplicaciones"
            textSize = 13f
            singleLine = true
            setTextColor(0xFFF2F4FA.toInt())
            setHintTextColor(0xFF8F98AB.toInt())
            setPadding(dp(13), 0, dp(13), 0)
            background = rounded(0xFF292E3B.toInt(), 0xFF454D61.toInt(), 15)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        panel.addView(search, LinearLayout.LayoutParams(-1, dp(44)))
        searchField = search
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString().orEmpty()
                refreshAppRows()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val tabsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabContainer = tabs
        tabsScroll.addView(tabs)
        val tabsParams = LinearLayout.LayoutParams(-1, dp(42))
        tabsParams.topMargin = dp(8)
        panel.addView(tabsScroll, tabsParams)
        buildTabs()

        val spacesScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val spaces = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        workspaceContainer = spaces
        spacesScroll.addView(spaces)
        val spacesParams = LinearLayout.LayoutParams(-1, dp(40))
        spacesParams.topMargin = dp(1)
        panel.addView(spacesScroll, spacesParams)
        buildWorkspaceChips()

        val separator = View(this).apply { setBackgroundColor(0xFF343B4D.toInt()) }
        val separatorParams = LinearLayout.LayoutParams(-1, dp(1))
        separatorParams.topMargin = dp(5)
        panel.addView(separator, separatorParams)

        val rowsScroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
        }
        val appRows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(7), 0, dp(5))
        }
        appListContainer = appRows
        rowsScroll.addView(appRows, ScrollView.LayoutParams(-1, -2))
        panel.addView(rowsScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        panel.isFocusableInTouchMode = true
        panel.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                showHandle()
                true
            } else {
                false
            }
        }

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val footerLabel = text("Fluid no sustituye Recientes de Android", 9f, 0xFF8F98AB.toInt(), false)
        footer.addView(footerLabel, LinearLayout.LayoutParams(0, dp(28), 1f))
        val home = text("Ajustes", 10f, 0xFFCAD6FF.toInt(), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(4), 0)
            setOnClickListener { openSettings() }
        }
        footer.addView(home, LinearLayout.LayoutParams(-2, dp(28)))
        panel.addView(footer)

        val display = resources.displayMetrics
        val maxWidth = (display.widthPixels - dp(24)).coerceAtLeast(dp(250))
        val width = min(dp(350), maxWidth)
        val maxHeight = (display.heightPixels - dp(48)).coerceAtLeast(dp(300))
        val height = min(dp(760), maxHeight)
        val savedPanel = WorkspaceStore.panelPosition(this)
        val maxPanelX = (display.widthPixels - width).coerceAtLeast(0)
        val maxPanelY = (display.heightPixels - height).coerceAtLeast(0)
        val lp = baseLayoutParams(width, height).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(savedPanel.first).coerceIn(0, maxPanelX)
            y = dp(savedPanel.second).coerceIn(0, maxPanelY)
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = 0.10f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            // Best-effort system blur; the translucent panel remains readable if the vendor disables blur.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    setBackgroundBlurRadius(dp(24))
                } catch (_: Exception) {
                    flags = flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                }
            }
        }
        overlayView = panel
        overlayParams = lp
        val panelAdded = try {
            windowManager.addView(panel, lp)
            true
        } catch (_: Exception) {
            // Some vendor builds reject blur-behind on application overlays. Retry as a
            // normal translucent panel rather than failing to show the whole dock.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0) {
                try {
                    try { windowManager.removeViewImmediate(panel) } catch (_: Exception) { }
                    lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                    lp.setBackgroundBlurRadius(0)
                    windowManager.addView(panel, lp)
                    true
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        }
        if (panelAdded) {
            panel.requestFocus()
            loadCatalogInBackground()
        } else {
            panelOpen = false
            overlayView = null
            showHandle()
            Toast.makeText(this, "Android no ha permitido abrir el panel flotante. Comprueba el permiso de superposición.", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildTabs() {
        val container = tabContainer ?: return
        container.removeAllViews()
        val values = listOf(
            ShelfTab.RECENT to "Recientes",
            ShelfTab.FAVORITES to "Favoritos",
            ShelfTab.ALL to "Apps",
            ShelfTab.WORKSPACE to "Espacio"
        )
        values.forEach { (tab, label) ->
            val selected = currentTab == tab
            val chip = text(label, 10f, if (selected) 0xFF151923.toInt() else 0xFFC2C9D8.toInt(), selected).apply {
                gravity = Gravity.CENTER
                setPadding(dp(13), 0, dp(13), 0)
                background = rounded(
                    if (selected) 0xFFCAD6FF.toInt() else 0xFF292E3A.toInt(),
                    if (selected) 0xFFCAD6FF.toInt() else 0xFF3A4151.toInt(),
                    13
                )
                setOnClickListener {
                    currentTab = tab
                    buildTabs()
                    refreshAppRows()
                }
            }
            val params = LinearLayout.LayoutParams(-2, dp(31))
            params.marginEnd = dp(7)
            container.addView(chip, params)
        }
    }

    private fun buildWorkspaceChips() {
        val container = workspaceContainer ?: return
        container.removeAllViews()
        val spaces = WorkspaceStore.loadWorkspaces(this)
        if (spaces.none { it.id == activeWorkspaceId }) {
            activeWorkspaceId = spaces.firstOrNull()?.id ?: "main"
        }
        spaces.forEach { space ->
            val selected = space.id == activeWorkspaceId
            val chip = text(space.name, 9f, if (selected) 0xFFDFE6FF.toInt() else 0xFFA5AEC1.toInt(), selected).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(10), 0)
                background = rounded(
                    if (selected) 0xFF343D52.toInt() else 0xFF252A35.toInt(),
                    if (selected) 0xFF7887AA.toInt() else 0xFF3A4150.toInt(),
                    11
                )
                setOnClickListener {
                    activeWorkspaceId = space.id
                    WorkspaceStore.setActiveWorkspaceId(this@OverlayDockService, space.id)
                    currentTab = ShelfTab.WORKSPACE
                    buildWorkspaceChips()
                    buildTabs()
                    refreshAppRows()
                }
            }
            val params = LinearLayout.LayoutParams(-2, dp(28))
            params.marginEnd = dp(6)
            container.addView(chip, params)
        }
    }

    private fun refreshAppRows() {
        val container = appListContainer ?: return
        container.removeAllViews()
        val all = cachedApps
        val recent = cachedRecentApps
        val favorites = WorkspaceStore.loadFavorites(this)
        val space = WorkspaceStore.loadWorkspaces(this).firstOrNull { it.id == activeWorkspaceId }
        val base = when (currentTab) {
            ShelfTab.RECENT -> if (recent.isNotEmpty()) recent else all.take(16)
            ShelfTab.FAVORITES -> all.filter { favorites.contains(it.packageName) }
            ShelfTab.ALL -> all
            ShelfTab.WORKSPACE -> all.filter { space?.packages?.contains(it.packageName) == true }
        }
        val shown = base.filter { it.label.contains(currentQuery, ignoreCase = true) }
        if (shown.isEmpty()) {
            val message = when {
                catalogLoading && all.isEmpty() -> "Cargando aplicaciones…"
                currentQuery.isNotBlank() -> "No se encontraron aplicaciones"
                currentTab == ShelfTab.FAVORITES -> "Aún no has marcado favoritos. Usa ☆ en una aplicación."
                currentTab == ShelfTab.WORKSPACE -> "Este espacio está vacío. En Apps, pulsa + para añadir aplicaciones."
                currentTab == ShelfTab.RECENT && !AppCatalog.hasUsageAccess(this) -> "Permite el acceso a aplicaciones usadas para ordenar por actividad reciente."
                else -> "No hay aplicaciones que mostrar."
            }
            container.addView(text(message, 11f, 0xFFADB6C8.toInt(), false).apply {
                setPadding(dp(8), dp(24), dp(8), dp(24))
                gravity = Gravity.CENTER
            })
            return
        }
        shown.forEach { app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), dp(5), dp(3), dp(5))
                background = rounded(0xFF222733.toInt(), 0x00333333, 13)
                isClickable = true
                setOnClickListener { launchFromPanel(app.packageName) }
                setOnLongClickListener {
                    toggleFavorite(app.packageName)
                    true
                }
            }
            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = app.label
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(39), dp(39)))
            val name = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(4), 0)
            }
            name.addView(text(app.label, 11f, 0xFFF0F2F8.toInt(), true), LinearLayout.LayoutParams(-1, -2))
            if (currentTab == ShelfTab.RECENT && app.lastUsedAt > 0) {
                name.addView(text("Usada recientemente", 8f, 0xFF909AAF.toInt(), false), LinearLayout.LayoutParams(-1, -2))
            } else if (currentTab == ShelfTab.RECENT && recent.isEmpty()) {
                name.addView(text("Apps instaladas · activa Acceso de uso para Recientes", 8f, 0xFF909AAF.toInt(), false), LinearLayout.LayoutParams(-1, -2))
            } else {
                name.addView(text(app.packageName, 7f, 0xFF828CA1.toInt(), false), LinearLayout.LayoutParams(-1, -2))
            }
            row.addView(name, LinearLayout.LayoutParams(0, dp(46), 1f))

            val isFavorite = favorites.contains(app.packageName)
            val favorite = text(if (isFavorite) "★" else "☆", 18f, if (isFavorite) 0xFFFFD889.toInt() else 0xFFAFB8CA.toInt(), false).apply {
                gravity = Gravity.CENTER
                setOnClickListener { toggleFavorite(app.packageName) }
                contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos"
            }
            row.addView(favorite, LinearLayout.LayoutParams(dp(33), dp(42)))

            if (currentTab != ShelfTab.WORKSPACE) {
                val add = text("+", 19f, 0xFFCAD6FF.toInt(), true).apply {
                    gravity = Gravity.CENTER
                    contentDescription = "Añadir al espacio ${space?.name ?: "activo"}"
                    setOnClickListener { addToWorkspace(app.packageName) }
                }
                row.addView(add, LinearLayout.LayoutParams(dp(30), dp(42)))
            }
            val params = LinearLayout.LayoutParams(-1, dp(52))
            params.bottomMargin = dp(5)
            container.addView(row, params)
        }
    }

    private fun loadCatalogInBackground() {
        val token = ++catalogLoadToken
        catalogLoading = true
        refreshAppRows()
        Thread {
            val loadedApps = AppCatalog.loadAll(this)
            val loadedRecent = AppCatalog.loadRecent(this, loadedApps, 40)
            mainHandler.post {
                if (token != catalogLoadToken || !panelOpen) return@post
                cachedApps = loadedApps
                cachedRecentApps = loadedRecent
                catalogLoading = false
                refreshAppRows()
            }
        }.apply { name = "FluidAppCatalog"; isDaemon = true }.start()
    }

    private fun addToWorkspace(packageName: String) {
        val spaces = WorkspaceStore.loadWorkspaces(this)
        val updated = spaces.map { space ->
            if (space.id == activeWorkspaceId) space.copy(packages = (space.packages + packageName).distinct()) else space
        }
        WorkspaceStore.saveWorkspaces(this, updated)
        currentTab = ShelfTab.WORKSPACE
        buildTabs()
        buildWorkspaceChips()
        refreshAppRows()
        Toast.makeText(this, "Añadida al espacio", Toast.LENGTH_SHORT).show()
    }

    private fun toggleFavorite(packageName: String) {
        val favorites = WorkspaceStore.loadFavorites(this).toMutableSet()
        val added = if (favorites.contains(packageName)) {
            favorites.remove(packageName)
            false
        } else {
            favorites.add(packageName)
            true
        }
        WorkspaceStore.saveFavorites(this, favorites)
        refreshAppRows()
        Toast.makeText(this, if (added) "Añadida a favoritos" else "Quitada de favoritos", Toast.LENGTH_SHORT).show()
    }

    private fun launchFromPanel(packageName: String) {
        showHandle()
        mainHandler.postDelayed({
            val tryFreeform = WorkspaceStore.isFreeformLaunchEnabled(this)
            if (!AppCatalog.launch(this, packageName, tryFreeform)) {
                Toast.makeText(this, "No se pudo abrir esta aplicación.", Toast.LENGTH_SHORT).show()
            }
        }, 80L)
    }

    private fun openSettings() {
        showHandle()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try { startActivity(intent) } catch (_: Exception) { }
    }

    private fun makeDragListener(openOnTap: Boolean): View.OnTouchListener {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        return View.OnTouchListener { view, event ->
            val lp = overlayParams ?: return@OnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        val display = resources.displayMetrics
                        val maxX = (display.widthPixels - lp.width).coerceAtLeast(0)
                        val maxY = (display.heightPixels - lp.height).coerceAtLeast(0)
                        lp.x = (startX + dx).coerceIn(0, maxX)
                        lp.y = (startY + dy).coerceIn(0, maxY)
                        try { overlayView?.let { windowManager.updateViewLayout(it, lp) } } catch (_: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        if (panelOpen) {
                            WorkspaceStore.savePanelPosition(this, pxToDp(lp.x), pxToDp(lp.y))
                        } else {
                            WorkspaceStore.saveDockPosition(this, pxToDp(lp.x), pxToDp(lp.y))
                        }
                    } else if (openOnTap) {
                        view.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try { windowManager.removeViewImmediate(view) } catch (_: Exception) { }
        }
        overlayView = null
        appListContainer = null
        searchField = null
        tabContainer = null
        workspaceContainer = null
        catalogLoadToken++
        catalogLoading = false
        freeformToggle = null
    }

    private fun freeformStateLabel(supported: Boolean): String {
        val enabled = WorkspaceStore.isFreeformLaunchEnabled(this)
        return when {
            !enabled -> "DESACTIVADO"
            supported -> "ACTIVADO"
            else -> "EXPERIMENTAL"
        }
    }

    private fun baseLayoutParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Fluid Stage Manager", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Indica que el panel flotante de Fluid está activo."
                }
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun pxToDp(value: Int): Int = (value / resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        removeOverlay()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.fluidos.stagemanager.action.STOP"
        const val ACTION_OPEN = "com.fluidos.stagemanager.action.OPEN"
        @Volatile var isRunning: Boolean = false
            private set
        private const val CHANNEL_ID = "fluid_stage_overlay_v2"
        private const val NOTIFICATION_ID = 3207
    }
}
