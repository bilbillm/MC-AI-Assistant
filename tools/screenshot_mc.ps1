param(
    [string]$OutFile = "shot.png"
)
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$proc = Get-Process | Where-Object { $_.MainWindowTitle -like "*Minecraft*" } | Select-Object -First 1
if (-not $proc) {
    Write-Output "NO_MC_WINDOW"
    exit 1
}
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32P {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hWnd, IntPtr hdcBlt, uint nFlags);
    public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
[Win32P]::SetProcessDPIAware() | Out-Null
$rect = New-Object Win32P+RECT
[Win32P]::GetWindowRect($proc.MainWindowHandle, [ref]$rect) | Out-Null
$w = $rect.Right - $rect.Left
$h = $rect.Bottom - $rect.Top
if ($w -le 0 -or $h -le 0) { Write-Output "BAD_RECT"; exit 1 }

$bmp = New-Object System.Drawing.Bitmap($w, $h)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()
# PW_RENDERFULLCONTENT = 2 (captures GPU-rendered content)
$ok = [Win32P]::PrintWindow($proc.MainWindowHandle, $hdc, 2)
$g.ReleaseHdc($hdc)
$bmp.Save($OutFile, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Output "SAVED ok=$ok $OutFile ${w}x${h} title=$($proc.MainWindowTitle)"
