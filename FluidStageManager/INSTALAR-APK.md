# Compilar e instalar Fluid Stage Manager 0.5.1

## 1. Preparar Windows

Instala JDK 17 y Android Studio. En Android Studio > SDK Manager, instala:

- Android SDK Platform 36
- Android SDK Build-Tools 35.0.0

No necesitas conectar la Raemond por ADB ni desbloquear el bootloader.

## 2. Compilar

Descomprime `FluidStageManager-0.5.1-source.zip`. Abre PowerShell en la carpeta `FluidStageManager` y ejecuta:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-apk-windows.ps1
```

El archivo se generará en:

```text
app\build\outputs\apk\debug\app-debug.apk
```

El script comprueba la configuración, ejecuta el verificador estático y calcula SHA-256 del APK al terminar. La compilación Gradle debe completarse correctamente para que exista el APK.

## 3. Instalar en la Raemond M70

1. Copia `app-debug.apk` a la tablet e instálalo.
2. Abre **Fluid Stage Manager** desde el cajón de aplicaciones.
3. En “Mostrar sobre otras aplicaciones”, pulsa **Conceder permiso** y permite la superposición para Fluid.
4. Vuelve a Fluid y pulsa **Activar panel flotante**.
5. Debe aparecer una pequeña pestaña con una F encima de las demás aplicaciones. Tócala para abrir el panel.
6. Prueba primero con una app normal, como Ajustes o el navegador. Para cerrar el panel toca la X; para desactivar el dock usa el botón de Fluid o la acción de la notificación.

## 4. Permisos opcionales

- **Acceso a aplicaciones usadas:** permite ordenar Recientes por uso real. Sin él, Fluid muestra apps instaladas como alternativa.
- **Notificaciones:** hace visible la notificación persistente. Si se deniega, Android puede ocultar la notificación, aunque el panel aún puede funcionar.
- **Iniciar después de reiniciar:** opt-in, desactivado inicialmente. El fabricante puede limitarlo mediante su gestor de batería o autoarranque.

## 5. Ventanas libres

Activa “Intentar abrir aplicaciones en ventanas” en Fluid. En la Raemond, activa también «Habilitar ventanas de formato libre» en Opciones de desarrollador si aparece. Fluid usa la API pública `ActivityOptions.setLaunchBounds`; no manipula el gestor de ventanas ni garantiza que todas las apps acepten el tamaño solicitado.

Si al pulsar una app se abre a pantalla completa, el firmware puede ignorar los límites o esa app puede no admitir el modo. No es un fallo del permiso de superposición.

## 6. Desinstalar o apagar

- Para ocultar temporalmente el dock: abre Fluid y pulsa **Desactivar panel flotante**, o usa la acción “Desactivar” de la notificación.
- Para desactivar el inicio tras reiniciar: apaga su interruptor en Fluid.
- Para retirar permisos: Ajustes de Android > Aplicaciones > Fluid Stage Manager > Acceso especial.
- Para quitar la aplicación: Ajustes > Aplicaciones > Fluid Stage Manager > Desinstalar.


## Privacidad y licencias

- Datos guardados localmente: favoritos, espacios, nombres y paquetes de apps, posición del dock/panel y preferencias.
- El historial de uso solo se consulta si el usuario concede el permiso especial; no se envía a ningún servidor.
- La app no declara permiso de Internet ni contiene telemetría o anuncios.
- Código propio: MIT. Dependencias AndroidX/Compose/Kotlin: Apache-2.0. Consulta `LICENSE`, `LICENSE-APACHE-2.0.txt`, `THIRD-PARTY-NOTICES.md` y `PRIVACIDAD.md`.
