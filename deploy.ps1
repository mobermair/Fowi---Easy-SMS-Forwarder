<#
.SYNOPSIS
    Builds the current code and installs it on the phone over wireless debugging.

.DESCRIPTION
    For trying out changes during development. The app is built as a signed release, so it
    installs over the Fowi version from GitHub and keeps its rules and history. If the phone
    is not connected, the script finds it on the network through wireless debugging.
    Nothing is tagged or published; use release.ps1 for that.
#>
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$sdk = (Select-String -Path 'local.properties' -Pattern '^sdk\.dir=(.*)$').Matches[0].Groups[1].Value -replace '\\:', ':' -replace '\\\\', '\'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "adb not found at $adb." }
if (-not (Test-Path 'keystore.properties')) { throw 'keystore.properties is missing, so the app cannot be signed.' }

function Get-ConnectedDevice {
    & $adb devices | Select-String '\tdevice$' | Select-Object -First 1
}

# Wireless debugging changes its port now and then, so reconnect through the network service it announces.
if (-not (Get-ConnectedDevice)) {
    Write-Host 'Phone not connected, searching the network...'
    $service = & $adb mdns services | Select-String '_adb-tls-connect' | Select-Object -First 1
    $address = if ($service) { [regex]::Match($service.Line, '\d+\.\d+\.\d+\.\d+:\d+').Value }
    if (-not $address) {
        throw 'Phone not found. Check that it is on the same Wi-Fi and wireless debugging is on (Settings > Developer options).'
    }
    & $adb connect $address | Out-Null
    Start-Sleep -Seconds 1
    if (-not (Get-ConnectedDevice)) { throw "Could not connect to $address. Turn wireless debugging off and on again." }
}

if (-not $env:JAVA_HOME) {
    $jbr = "$env:ProgramFiles\Android\Android Studio\jbr"
    if (Test-Path $jbr) { $env:JAVA_HOME = $jbr }
}
& .\gradlew.bat testDebugUnitTest assembleRelease --console=plain -q
if ($LASTEXITCODE -ne 0) { throw 'Build or tests failed.' }

& $adb install -r 'app/build/outputs/apk/release/app-release.apk'
if ($LASTEXITCODE -ne 0) { throw 'Installation failed.' }
& $adb shell am start -n at.om21.fowi/.MainActivity | Out-Null
Write-Host 'Fowi is installed and open on the phone.'
