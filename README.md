# Fluid Stage Manager 0.5.1

Fluid Stage Manager es un panel de multitarea flotante para Android. **No es un launcher**: no declara la categoría `HOME`, no sustituye la pantalla de inicio y no intenta reemplazar la pantalla Recientes del sistema.

El usuario abre Fluid desde el cajón de aplicaciones, concede los permisos que decida y activa un pequeño asa flotante. Al tocarla aparece un panel lateral inspirado visualmente en Stage Manager: aplicaciones recientes, favoritos, biblioteca, espacios de trabajo, búsqueda y accesos para abrir apps.

## Funciones incluidas

- Dock/asa flotante movible sobre las aplicaciones mediante `TYPE_APPLICATION_OVERLAY`.
- Panel lateral con estilo oscuro, bordes suaves y cabecera arrastrable.
- Pestañas de Recientes, Favoritos, Apps y Espacio.
- Catálogo de aplicaciones lanzables mediante una consulta de intents acotada (`MAIN` + `LAUNCHER`), sin `QUERY_ALL_PACKAGES`.
- Historial de uso opcional mediante `UsageStatsManager`.
- Favoritos y hasta 12 espacios persistentes, con selección de apps desde la pantalla de ajustes.
- Búsqueda en el panel.
- Activación y parada explícitas desde la app y desde la notificación persistente.
- Posición del dock guardada en preferencias.
- Inicio tras reinicio opcional, desactivado por defecto y solo después del consentimiento del usuario.
- Intento opcional de apertura en ventana libre con la API pública `ActivityOptions.setLaunchBounds`; si el fabricante no ofrece freeform/PiP, Android ignora los límites y abre a pantalla completa.
- Sin root, sin ADB, sin Accesibilidad, sin permiso de Internet, sin almacenamiento compartido y sin código Apple.

## Lo que no puede hacer una APK normal

Android conserva el control del gestor de tareas/Recientes, de las decoraciones de ventana y de las capturas reales de las aplicaciones. Fluid no puede interceptar el gesto Recientes, reorganizar tareas de otras apps, generar miniaturas vivas de todas las ventanas ni obligar a una app no compatible a funcionar en modo libre.

`ActivityOptions.setLaunchBounds` solo se respeta si el dispositivo/pantalla admite ventanas libres o PiP. Fluid permite un intento experimental incluso si el fabricante no anuncia la función: si el firmware no la admite, Android ignora los límites; una app no redimensionable también puede comportarse de otra forma. En la Raemond, prueba a activar «Habilitar ventanas de formato libre» en Opciones de desarrollador y luego el interruptor experimental de Fluid. Esta limitación procede de las API públicas de Android, no de un permiso que falte.

El dock flotante sí se muestra encima de las apps cuando el usuario concede el acceso especial de superposición. Los overlays de aplicaciones quedan por debajo de ventanas críticas del sistema, como la barra de estado y el teclado/IME.

## Privacidad y distribución en la UE

Fluid no declara permiso de Internet ni incorpora analítica o publicidad. Los permisos de acceso de uso, superposición y notificaciones se solicitan por separado y se pueden denegar. Los favoritos, espacios y posiciones se guardan localmente en las preferencias privadas de la app. Consulta `PRIVACIDAD.md`. Esta descripción técnica no sustituye una revisión jurídica para una distribución comercial: antes de publicar en una tienda, el distribuidor debe identificar al responsable y completar la información legal y de protección de datos que corresponda.

Si se distribuye mediante Google Play, el uso del tipo de servicio en primer plano `specialUse` debe justificarse y puede estar sujeto a revisión de las políticas de Play. Para instalación directa mediante APK no hay proceso de revisión de Play, pero Android sigue aplicando sus restricciones técnicas.

## Permisos y por qué se solicitan

| Permiso/acceso | Necesidad | Cómo se concede |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | Mostrar el asa y el panel sobre otras apps | Ajustes especiales de Android, aprobación manual |
| `PACKAGE_USAGE_STATS` | Ordenar Recientes según la actividad del usuario | Acceso de uso en Ajustes especiales; opcional |
| `POST_NOTIFICATIONS` | Mostrar la notificación persistente en el panel de notificaciones | Diálogo runtime en Android 13+; opcional para la lógica del dock |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Mantener el panel flotante vivo con servicio visible mientras el usuario lo ha activado | Declarados en el manifiesto; no son permisos runtime |
| `RECEIVE_BOOT_COMPLETED` | Respetar la opción de iniciar Fluid tras reiniciar | Broadcast del sistema; el autoarranque está desactivado por defecto |

No hay permisos de Accesibilidad, administrador del dispositivo, root, VPN, micrófono, cámara, contactos, ubicación ni Internet. La lista consultada se limita a las apps que exponen una actividad de inicio, mediante el filtro `MAIN` + `LAUNCHER`; no se declara `QUERY_ALL_PACKAGES`.

## Compilar en Windows

Requisitos:

- JDK 17.
- Android Studio reciente o Android SDK instalado.
- Android SDK Platform 36 y Build Tools 35.0.0.
- Gradle 8.11.1 (el script usa una instalación existente o descarga la distribución oficial y verifica SHA-256).

Pasos:

1. Descomprime el ZIP.
2. Abre PowerShell dentro de la carpeta del proyecto.
3. Ejecuta:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\build-apk-windows.ps1
   ```

4. El APK debug aparecerá en `app\build\outputs\apk\debug\app-debug.apk`.
5. Comprueba la huella SHA-256 que imprime el script y pasa el APK a la tablet.

También se incluye GitHub Actions (`.github/workflows/build-apk.yml`) para compilar el APK en un runner limpio y descargarlo como artefacto.

## Configuración en la Raemond M70

1. Instala `app-debug.apk` y abre **Fluid Stage Manager** desde el cajón de aplicaciones.
2. Pulsa **Conceder permiso** en “Mostrar sobre otras aplicaciones” y acepta en Ajustes.
3. Vuelve a Fluid y pulsa **Activar panel flotante**. Aparecerá el asa F sobre otras aplicaciones.
4. Opcional: concede “Acceso a aplicaciones usadas” para ordenar Recientes por última actividad.
5. Opcional: concede notificaciones para mostrar el control persistente en la bandeja.
6. Activa “Intentar abrir aplicaciones en ventanas” en los ajustes o desde el propio panel. Si el fabricante ofrece «Habilitar ventanas de formato libre» en Opciones de desarrollador, actívalo también. El modo es experimental cuando Android no anuncia la función.
7. Activa “Iniciar después de reiniciar” solo si quieres que Fluid intente volver a arrancar tras reiniciar. Algunos fabricantes aplican restricciones adicionales de inicio automático/ahorro de batería.

No selecciones Fluid como pantalla de inicio predeterminada. No hace falta y la aplicación no se ofrece como launcher.

## Comprobaciones antes de compilar

Ejecuta el verificador estático incluido:

```bash
python scripts/verify_project.py
```

Esto comprueba manifiesto, permisos, filtros HOME, configuración de SDK y archivos de licencia. No sustituye una compilación Gradle ni una prueba en el hardware real.

## Paquete

- Application ID: `com.fluidos.stagemanager`
- Versión: `0.5.1` (versionCode 6)
- minSdk: 26 (Android 8.0)
- targetSdk: 35
- compileSdk: 36
- Java/Kotlin target: 17
