package com.fluidos.stagemanager

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF10131B)
private val Panel = Color(0xFF1C212D)
private val Raised = Color(0xFF252C3B)
private val Pearl = Color(0xFFF2F4FA)
private val Muted = Color(0xFF9BA6BB)
private val Blue = Color(0xFFCAD6FF)
private val Green = Color(0xFF8ED8B0)

class MainActivity : ComponentActivity() {
    private val overlayAllowed = mutableStateOf(false)
    private val usageAllowed = mutableStateOf(false)
    private val serviceActive = mutableStateOf(false)
    private val notificationAllowed = mutableStateOf(true)
    private val autoStart = mutableStateOf(false)
    private val freeformSupported = mutableStateOf(false)
    private val freeformEnabled = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshStatus()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Ink,
                surface = Panel,
                primary = Blue,
                onPrimary = Ink,
                onBackground = Pearl,
                onSurface = Pearl,
                secondary = Green
            )) {
                FluidManagerScreen(
                    overlayAllowed = overlayAllowed.value,
                    usageAllowed = usageAllowed.value,
                    serviceActive = serviceActive.value,
                    notificationAllowed = notificationAllowed.value,
                    autoStart = autoStart.value,
                    freeformSupported = freeformSupported.value,
                    freeformEnabled = freeformEnabled.value,
                    onGrantOverlay = ::openOverlaySettings,
                    onGrantUsage = ::openUsageSettings,
                    onStartService = ::startDock,
                    onStopService = ::stopDock,
                    onRequestNotifications = ::requestNotifications,
                    onAutoStartChanged = { enabled ->
                        WorkspaceStore.setAutoStartEnabled(this, enabled)
                        autoStart.value = enabled
                    },
                    onFreeformChanged = { enabled ->
                        WorkspaceStore.setFreeformLaunchEnabled(this, enabled)
                        freeformEnabled.value = enabled
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        overlayAllowed.value = Settings.canDrawOverlays(this)
        usageAllowed.value = AppCatalog.hasUsageAccess(this)
        serviceActive.value = OverlayDockService.isRunning
        autoStart.value = WorkspaceStore.isAutoStartEnabled(this)
        freeformSupported.value = AppCatalog.hasFreeformSupport(this)
        freeformEnabled.value = WorkspaceStore.isFreeformLaunchEnabled(this)
        notificationAllowed.value = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun openOverlaySettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Abre Ajustes > Aplicaciones > Acceso especial > Mostrar sobre otras aplicaciones.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openUsageSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "No se han podido abrir los ajustes de acceso de uso.", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationAllowed.value) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun startDock() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        try {
            val intent = Intent(this, OverlayDockService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            Toast.makeText(this, "Fluid está activando el panel flotante.", Toast.LENGTH_SHORT).show()
            serviceActive.value = true
        } catch (error: Exception) {
            serviceActive.value = false
            Toast.makeText(this, "Android no ha permitido iniciar Fluid: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopDock() {
        try {
            stopService(Intent(this, OverlayDockService::class.java))
            serviceActive.value = false
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo detener el panel.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated by Android, retained for broad device support")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) refreshStatus()
    }

    companion object { private const val REQUEST_NOTIFICATIONS = 901 }
}

@Composable
private fun FluidManagerScreen(
    overlayAllowed: Boolean,
    usageAllowed: Boolean,
    serviceActive: Boolean,
    notificationAllowed: Boolean,
    autoStart: Boolean,
    freeformSupported: Boolean,
    freeformEnabled: Boolean,
    onGrantOverlay: () -> Unit,
    onGrantUsage: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRequestNotifications: () -> Unit,
    onAutoStartChanged: (Boolean) -> Unit,
    onFreeformChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var workspaces by remember { mutableStateOf(WorkspaceStore.loadWorkspaces(context)) }
    var allApps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var editingSpace by remember { mutableStateOf<Workspace?>(null) }
    var createSpace by remember { mutableStateOf(false) }
    var deleteSpace by remember { mutableStateOf<Workspace?>(null) }
    var showLicense by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var actualServiceActive by remember { mutableStateOf(serviceActive) }

    LaunchedEffect(serviceActive) {
        actualServiceActive = serviceActive
    }
    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) { AppCatalog.loadAll(context) }
        while (true) {
            actualServiceActive = OverlayDockService.isRunning
            delay(900)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Ink).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Blue, Color(0xFFB8A9FF)))),
                contentAlignment = Alignment.Center
            ) { Text("F", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("FLUID STAGE MANAGER", color = Pearl, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("Panel de ventanas flotante · v0.5.1", color = Muted, fontSize = 11.sp)
            }
            StatusDot(actualServiceActive, if (actualServiceActive) "ACTIVO" else "APAGADO")
        }

        Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Panel sobre tus aplicaciones", color = Pearl, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Fluid no reemplaza tu launcher. Activa una pequeña pestaña flotante que puedes abrir encima de cualquier aplicación para cambiar rápidamente a otra, ver favoritos y elegir espacios de trabajo.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                )
                Button(
                    onClick = if (actualServiceActive) onStopService else onStartService,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (actualServiceActive) Color(0xFF51343B) else Blue,
                        contentColor = if (actualServiceActive) Color(0xFFFFD9DE) else Ink
                    )
                ) {
                    Icon(if (actualServiceActive) Icons.Default.PowerSettingsNew else Icons.Default.Launch, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (actualServiceActive) "Desactivar panel flotante" else "Activar panel flotante", fontWeight = FontWeight.Bold)
                }
                Text(
                    if (actualServiceActive) "La pestaña F está superpuesta sobre las aplicaciones." else "El panel está apagado. Actívalo para que aparezca la pestaña F.",
                    color = if (actualServiceActive) Green else Muted, fontSize = 11.sp
                )
            }
        }

        Text("Permisos necesarios", color = Pearl, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        PermissionCard(
            title = "Mostrar sobre otras aplicaciones",
            description = "Necesario para dibujar el dock y el panel flotante por encima de la app que estés utilizando.",
            granted = overlayAllowed,
            action = if (overlayAllowed) "Permiso concedido" else "Conceder permiso",
            onClick = onGrantOverlay
        )
        PermissionCard(
            title = "Acceso a aplicaciones usadas",
            description = "Opcional. Ordena la pestaña Recientes según el uso. Sin este acceso, Fluid muestra una selección de aplicaciones instaladas.",
            granted = usageAllowed,
            action = if (usageAllowed) "Permiso concedido" else "Permitir acceso",
            onClick = onGrantUsage
        )
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionCard(
                title = "Notificaciones",
                description = "Permite ver la notificación persistente para saber que Fluid está activo y desactivarlo rápidamente. El dock puede funcionar aunque la notificación esté bloqueada por el usuario.",
                granted = notificationAllowed,
                action = if (notificationAllowed) "Permiso concedido" else "Permitir notificaciones",
                onClick = onRequestNotifications
            )
        }

        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Iniciar después de reiniciar", color = Pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Solo si ya has concedido el permiso de superposición. Algunos fabricantes pueden retrasarlo o bloquearlo.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                    Switch(checked = autoStart, onCheckedChange = onAutoStartChanged, enabled = overlayAllowed)
                }
                HorizontalDivider(color = Color(0xFF343B49))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Intentar abrir aplicaciones en ventanas", color = Pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (freeformSupported) "Android anuncia compatibilidad con ventanas libres. Fluid enviará límites al abrir apps; alguna puede ignorarlos." else "Modo experimental: activa también «Habilitar ventanas de formato libre» en Opciones de desarrollador. Android puede ignorar los límites si el firmware no lo admite.",
                            color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                        )
                    }
                    Switch(checked = freeformEnabled, onCheckedChange = onFreeformChanged, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Espacios de trabajo", color = Pearl, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { createSpace = true }, enabled = workspaces.size < 12, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (workspaces.size < 12) "Nuevo" else "Límite 12")
            }
        }
        workspaces.forEach { space ->
            Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(space.name, color = Pearl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${space.packages.size} aplicaciones", color = Muted, fontSize = 10.sp)
                    }
                    IconButton(onClick = { editingSpace = space }) {
                        Icon(Icons.Default.Edit, "Editar aplicaciones", tint = Blue)
                    }
                    IconButton(onClick = { deleteSpace = space }, enabled = workspaces.size > 1) {
                        Icon(Icons.Default.Delete, "Eliminar espacio", tint = Color(0xFFFFA8AF))
                    }
                }
            }
        }

        Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Qué controla Fluid", color = Pearl, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Fluid es un panel flotante de acceso rápido. Android sigue gestionando sus propias ventanas, el gesto Recientes, las notificaciones y la pantalla de bloqueo. No se instala como launcher ni sustituye componentes del sistema.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                Text("Cuando Android declara compatibilidad con ventanas libres, Fluid utiliza la API pública ActivityOptions.setLaunchBounds para solicitar un tamaño y posición al abrir una app. Android puede ignorar esa solicitud si el dispositivo o la aplicación no son compatibles; el sistema conserva el control de las ventanas.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                TextButton(onClick = { showLicense = true }, contentPadding = PaddingValues(0.dp)) { Text("Licencias y código abierto") }
                TextButton(onClick = { showPrivacy = true }, contentPadding = PaddingValues(0.dp)) { Text("Privacidad local") }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (createSpace) {
        WorkspaceNameDialog(
            title = "Crear espacio",
            initial = "",
            onDismiss = { createSpace = false },
            onSave = { name ->
                val newSpace = Workspace(UUID.randomUUID().toString(), name, emptyList())
                workspaces = (workspaces + newSpace).take(12)
                WorkspaceStore.saveWorkspaces(context, workspaces)
                createSpace = false
            }
        )
    }
    editingSpace?.let { space ->
        WorkspaceAppsDialog(
            workspace = space,
            apps = allApps,
            onDismiss = { editingSpace = null },
            onSave = { selected ->
                workspaces = workspaces.map { if (it.id == space.id) it.copy(packages = selected.toList()) else it }
                WorkspaceStore.saveWorkspaces(context, workspaces)
                editingSpace = null
            }
        )
    }
    deleteSpace?.let { space ->
        AlertDialog(
            onDismissRequest = { deleteSpace = null },
            title = { Text("Eliminar espacio") },
            text = { Text("Se eliminará «${space.name}». No se desinstalarán aplicaciones.") },
            confirmButton = {
                TextButton(onClick = {
                    workspaces = workspaces.filterNot { it.id == space.id }
                    WorkspaceStore.saveWorkspaces(context, workspaces)
                    if (WorkspaceStore.activeWorkspaceId(context) == space.id) {
                        WorkspaceStore.setActiveWorkspaceId(context, workspaces.first().id)
                    }
                    deleteSpace = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteSpace = null }) { Text("Cancelar") } }
        )
    }
    if (showLicense) {
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("Licencias") },
            text = { Text("El código propio de Fluid se distribuye bajo MIT. Las bibliotecas AndroidX, Jetpack Compose, Material y Kotlin utilizadas por el proyecto se distribuyen bajo Apache License 2.0. No se incorpora código de Apple ni recursos propietarios de iPadOS.") },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Cerrar") } }
        )
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Privacidad local") },
            text = { Text("Fluid no declara permiso de Internet ni incorpora publicidad o analítica. Favoritos, espacios y posiciones se guardan en el dispositivo. El historial de uso solo se consulta si concedes ese acceso especial. Puedes revocar los permisos o desinstalar Fluid desde Ajustes. Consulta PRIVACIDAD.md en el proyecto para más detalles.") },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun StatusDot(active: Boolean, label: String) {
    Surface(color = if (active) Color(0xFF1C382D) else Raised, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if (active) Green else Muted))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (active) Green else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PermissionCard(title: String, description: String, granted: Boolean, action: String, onClick: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = if (granted) Green else Color(0xFFFFC77C), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Pearl, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onClick) { Text(action, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun WorkspaceNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(28) },
                label = { Text("Nombre") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun WorkspaceAppsDialog(workspace: Workspace, apps: List<AppEntry>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember(workspace.id) { mutableStateOf(workspace.packages.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aplicaciones de ${workspace.name}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Buscar") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(apps.filter { it.label.contains(query, ignoreCase = true) }, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                selected = if (selected.contains(app.packageName)) selected - app.packageName else selected + app.packageName
                            }.padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(app.label, color = Pearl, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Checkbox(
                                checked = selected.contains(app.packageName),
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + app.packageName else selected - app.packageName
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("Guardar (${selected.size})") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
