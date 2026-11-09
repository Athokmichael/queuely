$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_SDK_ROOT = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$adb = Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"
$emulator = Join-Path $env:ANDROID_SDK_ROOT "emulator\emulator.exe"

function Test-EmulatorReady {
    $out = & $adb devices
    return ($out -match "emulator-\d+\s+device")
}

if (-not (Test-EmulatorReady)) {
    Write-Host "Starting Light Phone emulator..."
    Start-Process -FilePath $emulator -ArgumentList @(
        "-avd", "Light_Phone_API_36",
        "-gpu", "host",
        "-cores", "4",
        "-memory", "1536",
        "-netdelay", "none",
        "-netspeed", "full",
        "-no-boot-anim"
    )
    & $adb wait-for-device | Out-Null
    $deadline = (Get-Date).AddMinutes(4)
    $boot = ""
    do {
        Start-Sleep -Seconds 2
        $boot = (& $adb shell getprop sys.boot_completed 2>$null)
        if ($boot) { $boot = "$boot".Trim() }
    } while ($boot -ne "1" -and (Get-Date) -lt $deadline)
    if ($boot -ne "1") {
        throw "The emulator did not finish starting. Wait a moment and run again."
    }
}

Set-Location $root
Write-Host "Installing QueueLy..."
& .\gradlew.bat installDebug --offline
if ($LASTEXITCODE -ne 0) {
    Write-Host "Offline install missed a file. Trying once with network..."
    & .\gradlew.bat installDebug
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

& $adb shell settings put global window_animation_scale 0 | Out-Null
& $adb shell settings put global transition_animation_scale 0 | Out-Null
& $adb shell settings put global animator_duration_scale 0 | Out-Null
& $adb shell am start -W -n com.usiu.queuely/.LoginActivity
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public class QueueLyLayout {
  public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc lpEnumFunc, IntPtr lParam);
  [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
  public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
}
"@
[QueueLyLayout]::SetProcessDPIAware() | Out-Null
$found = @{}
$cb = [QueueLyLayout+EnumProc] {
    param($h, $l)
    if (-not [QueueLyLayout]::IsWindowVisible($h)) { return $true }
    $sb = New-Object System.Text.StringBuilder 512
    [void][QueueLyLayout]::GetWindowText($h, $sb, $sb.Capacity)
    $title = $sb.ToString()
    if ($title -like "Android Emulator - Light_Phone_API_36*") { $found["emu"] = $h }
    elseif ($title -like "*queuely*Visual Studio Code") { $found["code"] = $h }
    return $true
}
[QueueLyLayout]::EnumWindows($cb, [IntPtr]::Zero) | Out-Null
if ($found["emu"]) {
    $rect = New-Object QueueLyLayout+RECT
    [QueueLyLayout]::GetWindowRect($found["emu"], [ref]$rect) | Out-Null
    $emuW = [Math]::Min(520, [Math]::Max(420, $rect.Right - $rect.Left))
    $emuX = 1920 - $emuW
    $notop = [IntPtr](-2)
    [QueueLyLayout]::ShowWindow($found["emu"], 9) | Out-Null
    [QueueLyLayout]::SetWindowPos($found["emu"], $notop, $emuX, 0, $emuW, 1020, 0x0040) | Out-Null
    if ($found["code"]) {
        [QueueLyLayout]::ShowWindow($found["code"], 9) | Out-Null
        [QueueLyLayout]::SetWindowPos($found["code"], [IntPtr]::Zero, 0, 0, $emuX, 1020, 0x0040) | Out-Null
    }
}

& .\gradlew.bat --stop | Out-Null
Write-Host "QueueLy is open on the Light Phone emulator."
