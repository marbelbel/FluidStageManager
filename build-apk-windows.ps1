$ErrorActionPreference = "Stop"

$GradleVersion = "8.11.1"
$ExpectedSha256 = "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6"
$GradleExe = $null

# Android Gradle Plugin 8.10.1 requires Gradle 8.11.1 and JDK 17 or newer.
$JavaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $JavaCommand) { throw "No encuentro Java. Instala JDK 17 o superior y configura JAVA_HOME/PATH." }
$JavaOutput = (& java -version 2>&1 | Out-String)
$JavaMatch = [regex]::Match($JavaOutput, 'version\s+"(?<major>\d+)')
if (-not $JavaMatch.Success -or [int]$JavaMatch.Groups['major'].Value -lt 17) {
    throw "Se necesita JDK 17 o superior. Versión detectada: $JavaOutput"
}

$InstalledGradle = Get-Command gradle -ErrorAction SilentlyContinue
if ($InstalledGradle) {
    $InstalledVersionOutput = (& $InstalledGradle.Source --version 2>&1 | Out-String)
    if ($InstalledVersionOutput -match "Gradle $([regex]::Escape($GradleVersion))") {
        $GradleExe = $InstalledGradle.Source
    }
}

if (-not $GradleExe) {
    $CacheRoot = Join-Path $env:LOCALAPPDATA "FluidStageManager"
    $GradleHome = Join-Path $CacheRoot "gradle-$GradleVersion"
    $GradleExe = Join-Path $GradleHome "bin\gradle.bat"

    if (-not (Test-Path $GradleExe)) {
        New-Item -ItemType Directory -Force -Path $CacheRoot | Out-Null
        $ZipPath = Join-Path $CacheRoot "gradle-$GradleVersion-bin.zip"
        $Url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
        Write-Host "Descargando Gradle $GradleVersion desde el sitio oficial..."
        Invoke-WebRequest -Uri $Url -OutFile $ZipPath -UseBasicParsing
        $ActualSha256 = (Get-FileHash -Path $ZipPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($ActualSha256 -ne $ExpectedSha256) {
            Remove-Item $ZipPath -Force
            throw "La comprobación SHA-256 oficial de Gradle no coincide. Descarga cancelada."
        }
        Expand-Archive -Path $ZipPath -DestinationPath $CacheRoot -Force
        Remove-Item $ZipPath -Force
    }
}

$SdkPath = $env:ANDROID_SDK_ROOT
if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
if (-not $SdkPath) {
    $DefaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $DefaultSdk) { $SdkPath = $DefaultSdk }
}
if (-not $SdkPath -or -not (Test-Path (Join-Path $SdkPath "platforms\android-36\android.jar"))) {
    throw "No encuentro Android SDK Platform 36. Instálalo desde Android Studio > SDK Manager y vuelve a ejecutar este script."
}
$env:ANDROID_HOME = $SdkPath
$env:ANDROID_SDK_ROOT = $SdkPath
if (-not (Test-Path (Join-Path $SdkPath "build-tools\35.0.0\aapt.exe"))) {
    throw "No encuentro Android SDK Build-Tools 35.0.0. Instálalo desde Android Studio > SDK Manager y vuelve a ejecutar este script."
}

$Python = Get-Command py -ErrorAction SilentlyContinue
if ($Python) {
    & $Python.Source -3 (Join-Path $PSScriptRoot "scripts\verify_project.py")
    if ($LASTEXITCODE -ne 0) { throw "Falló el verificador estático del proyecto." }
} else {
    $Python = Get-Command python -ErrorAction SilentlyContinue
    if ($Python) {
        & $Python.Source (Join-Path $PSScriptRoot "scripts\verify_project.py")
        if ($LASTEXITCODE -ne 0) { throw "Falló el verificador estático del proyecto." }
    } else {
        Write-Warning "Python no está instalado; se omite el verificador estático. Gradle seguirá comprobando el proyecto."
    }
}

Write-Host "Compilando Fluid Stage Manager 0.5.1..." -ForegroundColor Cyan
Push-Location $PSScriptRoot
try {
    & $GradleExe :app:assembleDebug --no-daemon --stacktrace --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle terminó con error $LASTEXITCODE." }
} finally {
    Pop-Location
}

$ApkPath = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $ApkPath)) { throw "La compilación terminó pero no se encontró el APK esperado: $ApkPath" }
$ApkHash = (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "APK creado correctamente: $ApkPath" -ForegroundColor Green
Write-Host "SHA-256: $ApkHash" -ForegroundColor Green
