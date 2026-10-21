# Builds the signed release APK into dist\ with its SHA-256 and signing-certificate fingerprint.
# Before each new version, raise versionCode and versionName in launcher\app\build.gradle.
$ErrorActionPreference = "Stop"
$tools = "$env:LOCALAPPDATA\atv-tools"
$root = Join-Path $PSScriptRoot ".." | Resolve-Path
$proj = "$root\launcher"
if (-not (Test-Path "$root\signing\keystore.properties")) { throw "signing\keystore.properties is missing" }
$env:JAVA_HOME = (Get-ChildItem "$tools\jdk" -Directory)[0].FullName
$env:ANDROID_HOME = "$tools\sdk"
"sdk.dir=$($env:ANDROID_HOME -replace '\\','\\')" | Set-Content "$proj\local.properties" -Encoding ascii

cmd /c "`"$tools\gradle-8.7\bin\gradle.bat`" -p `"$proj`" --no-daemon -q assembleRelease"
if ($LASTEXITCODE -ne 0) { throw "build failed" }

$version = (Select-String -Path "$proj\app\build.gradle" -Pattern "versionName '([^']+)'").Matches[0].Groups[1].Value
$name = "GlassHome-$version.apk"
New-Item -ItemType Directory -Force "$root\dist" | Out-Null
Copy-Item "$proj\app\build\outputs\apk\release\app-release.apk" "$root\dist\$name" -Force

$sha = (Get-FileHash "$root\dist\$name" -Algorithm SHA256).Hash.ToLower()
$cert = (& "$tools\sdk\build-tools\34.0.0\apksigner.bat" verify --print-certs "$root\dist\$name" |
    Select-String "certificate SHA-256 digest: (\w+)").Matches[0].Groups[1].Value
@"
$sha  $name

Signing certificate SHA-256 (same for every version):
$cert
"@ | Set-Content "$root\dist\SHA256SUMS.txt" -Encoding ascii
Get-Content "$root\dist\SHA256SUMS.txt"
