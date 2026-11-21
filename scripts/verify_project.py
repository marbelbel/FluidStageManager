#!/usr/bin/env python3
"""Static sanity checks for Fluid Stage Manager; does not replace an Android build/device test."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"

def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
    manifest = ET.parse(manifest_path).getroot()
    app = manifest.find("application")
    require(app is not None, "AndroidManifest.xml has no <application>")

    permissions = {node.attrib.get(ANDROID + "name") for node in manifest.findall("uses-permission")}
    required = {
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.RECEIVE_BOOT_COMPLETED",
    }
    require(required.issubset(permissions), f"Missing expected permissions: {sorted(required - permissions)}")
    forbidden = {
        "android.permission.INTERNET",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.MANAGE_ACTIVITY_TASKS",
    }
    require(not (forbidden & permissions), f"Unexpected high-risk/broad permissions: {sorted(forbidden & permissions)}")

    optional_features = [node for node in manifest.findall("uses-feature")
                         if node.attrib.get(ANDROID + "name") == "android.software.freeform_window_management"]
    require(optional_features and optional_features[0].attrib.get(ANDROID + "required") == "false",
            "Freeform feature must be optional so unsupported devices can install Fluid")

    activities = app.findall("activity")
    require(len(activities) == 1, "Expected one user-facing activity")
    categories = set()
    for filt in activities[0].findall("intent-filter"):
        categories.update(cat.attrib.get(ANDROID + "name") for cat in filt.findall("category"))
    require("android.intent.category.LAUNCHER" in categories, "Main activity must remain accessible from app drawer")
    require("android.intent.category.HOME" not in categories, "Fluid must not register as Android HOME/launcher")

    services = app.findall("service")
    require(any(s.attrib.get(ANDROID + "name") == ".OverlayDockService" for s in services), "Overlay service missing")
    svc = next(s for s in services if s.attrib.get(ANDROID + "name") == ".OverlayDockService")
    require(svc.attrib.get(ANDROID + "foregroundServiceType") == "specialUse", "Overlay service must declare specialUse FGS type")
    require(svc.attrib.get(ANDROID + "exported") == "false", "Overlay service must not be exported")
    require(any(p.attrib.get(ANDROID + "name") == "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" for p in svc.findall("property")), "specialUse subtype property missing")

    receivers = app.findall("receiver")
    require(any(r.attrib.get(ANDROID + "name") == ".BootReceiver" for r in receivers), "Optional boot receiver missing")
    require("android.intent.action.BOOT_COMPLETED" in (ROOT / "app/src/main/AndroidManifest.xml").read_text(), "Boot intent filter missing")

    build = (ROOT / "app/build.gradle.kts").read_text()
    for token in ('compileSdk = 36', 'buildToolsVersion = "35.0.0"', 'targetSdk = 35', 'minSdk = 26', 'versionCode = 6', 'versionName = "0.5.1"'):
        require(token in build, f"Build config missing: {token}")

    catalog = (ROOT / "app/src/main/java/com/fluidos/stagemanager/AppCatalog.kt").read_text()
    require("ActivityOptions.makeBasic().setLaunchBounds(bounds)" in catalog, "Public freeform bounds API not wired")
    require("FEATURE_FREEFORM_WINDOW_MANAGEMENT" in catalog, "Freeform support check missing")

    for relative in ("LICENSE", "LICENSE-APACHE-2.0.txt", "THIRD-PARTY-NOTICES.md", "README.md", "INSTALAR-APK.md"):
        require((ROOT / relative).is_file(), f"Required documentation missing: {relative}")
    require("MIT License" in (ROOT / "LICENSE").read_text(), "Project license missing")
    require((ROOT / "PRIVACIDAD.md").is_file(), "Privacy notice missing")
    require("android:icon=\"@drawable/ic_fluid_app\"" in (ROOT / "app/src/main/AndroidManifest.xml").read_text(), "App icon resource missing")
    overlay = (ROOT / "app/src/main/java/com/fluidos/stagemanager/OverlayDockService.kt").read_text()
    require("TYPE_APPLICATION_OVERLAY" in overlay, "Floating overlay implementation missing")
    require("savePanelPosition" in overlay and "saveDockPosition" in overlay, "Dock/panel positions must be stored separately")
    require("setOnClickListener { showPanel() }" in overlay, "Floating handle click action missing")
    require("Apache-2.0" in (ROOT / "THIRD-PARTY-NOTICES.md").read_text(), "Third-party license notices missing")
    privacy = (ROOT / "PRIVACIDAD.md").read_text()
    require("no declara el permiso `INTERNET`" in privacy, "Privacy notice should explain that the app has no Internet permission")
    require("versionCode = 6" in build and 'versionName = "0.5.1"' in build, "Version number mismatch")

    print("OK: XML válido; permisos mínimos; sin HOME launcher; superposición; posiciones separadas; API freeform; privacidad y licencias presentes.")
    print("NOTA: son pruebas estáticas. No verifican compilación, ejecución ni compatibilidad del firmware real.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
