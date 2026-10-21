# Sends optional key presses, then saves a TV screenshot to shots\<name>.png
param([string]$Name = "shot", [string[]]$Keys = @(), [string]$Device = "192.168.8.188:5555")
$adb = "$env:LOCALAPPDATA\atv-tools\platform-tools\adb.exe"
foreach ($k in $Keys) { & $adb -s $Device shell input keyevent $k; Start-Sleep -Milliseconds 450 }
Start-Sleep -Milliseconds 700
$dir = Join-Path $PSScriptRoot "..\shots"
New-Item -ItemType Directory -Force $dir | Out-Null
& $adb -s $Device shell screencap -p /sdcard/s.png
& $adb -s $Device pull /sdcard/s.png "$dir\$Name.png" | Out-Null
"$dir\$Name.png"
