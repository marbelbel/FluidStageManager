# Aviso de privacidad local — Fluid Stage Manager 0.5.1

## Qué hace la aplicación

Fluid añade una pestaña y un panel flotantes para abrir aplicaciones instaladas. No es un launcher, no sustituye la pantalla de inicio ni administra el gestor de Recientes de Android.

## Datos tratados en el dispositivo

- **Aplicaciones instaladas:** consulta únicamente las actividades visibles para el filtro Android `ACTION_MAIN` + `CATEGORY_LAUNCHER`, para mostrar nombre e icono.
- **Actividad reciente:** si el usuario concede expresamente el acceso especial a aplicaciones usadas, consulta eventos de uso para ordenar la pestaña Recientes. Android controla y puede revocar este acceso.
- **Preferencias locales:** favoritos, espacios de trabajo y sus paquetes, posición del asa, posición del panel, apertura en ventanas libres y opción de inicio tras reiniciar.

Las preferencias se guardan en el almacenamiento privado de la aplicación. La consulta de uso se realiza en el dispositivo. Fluid no lee el contenido de otras aplicaciones, mensajes, fotos, contraseñas ni pulsaciones.

## Comunicaciones y publicidad

La aplicación no declara el permiso `INTERNET`, no incorpora analítica, publicidad ni servicios de cuenta, y no transmite estos datos a Fluid ni a terceros. Las operaciones de instalación, permisos, ventanas y notificaciones siguen dependiendo de Android y del fabricante.

## Control del usuario

Puedes desactivar el panel desde Fluid o desde su notificación, revocar «Mostrar sobre otras aplicaciones» y «Acceso a aplicaciones usadas» desde Ajustes de Android, desactivar el inicio automático o desinstalar la aplicación. Al desinstalar, Android elimina las preferencias privadas de Fluid.

## Licencias y responsabilidad de distribución

El código original se publica bajo MIT. Las bibliotecas enumeradas en `THIRD-PARTY-NOTICES.md` se distribuyen bajo Apache-2.0. No se redistribuye código ni recursos de Apple.

Este aviso describe el funcionamiento de esta compilación de proyecto y no constituye una certificación de cumplimiento legal. Quien distribuya la aplicación públicamente o con fines comerciales en el Espacio Económico Europeo debe facilitar la identidad y los datos de contacto del responsable del tratamiento, determinar la base jurídica aplicable y completar cualquier información adicional exigida por la normativa y la tienda usada. No se inventa aquí una identidad de responsable.
