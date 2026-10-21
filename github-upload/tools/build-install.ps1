# Builds the launcher (debug) and installs it on the TV as the default home screen.
param([string]$Device = "192.168.8.188:5555")
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$tools = "$env:LOCALAPPDATA\atv-tools"
$adb = "$tools\platform-tools\adb.exe"
$proj = Join-Path $PSScriptRoot "..\launcher" | Resolve-Path
$env:JAVA_HOME = (Get-ChildItem "$tools\jdk" -Directory)[0].FullName
$env:ANDROID_HOME = "$tools\sdk"

# One-time: Gradle and the Outfit font (SIL Open Font License)
$gradle = "$tools\gradle-8.7\bin\gradle.bat"
if (-not (Test-Path $gradle)) {
  Invoke-WebRequest https://services.gradle.org/distributions/gradle-8.7-bin.zip -OutFile "$tools\gradle.zip"
  Expand-Archive "$tools\gradle.zip" $tools -Force
}
$font = "$proj\app\src\main\assets\outfit.ttf"
if (-not (Test-Path $font)) {
  New-Item -ItemType Directory -Force (Split-Path $font) | Out-Null
  Invoke-WebRequest "https://github.com/google/fonts/raw/main/ofl/outfit/Outfit%5Bwght%5D.ttf" -OutFile $font
}
"sdk.dir=$($env:ANDROID_HOME -replace '\\','\\')" | Set-Content "$proj\local.properties" -Encoding ascii

& $gradle -p $proj --no-daemon -q assembleDebug
if ($LASTEXITCODE -ne 0) { throw "build failed" }
$apk = "$proj\app\build\outputs\apk\debug\app-debug.apk"

& $adb connect $Device | Out-Null
& $adb -s $Device install -r $apk
& $adb -s $Device shell cmd package set-home-activity com.glasshome.tv/.MainActivity
& $adb -s $Device shell pm disable-user --user 0 me.efesser.flauncher
& $adb -s $Device shell am start -a android.intent.action.MAIN -c android.intent.category.HOME

# Remove the earlier test build if it is still installed
& $adb -s $Device uninstall com.local.atvhome 2>$null | Out-Null
