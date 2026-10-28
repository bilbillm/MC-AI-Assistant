param(
    [string]$Action = "click",   # click | key | resize
    [double]$XR = 0.5,           # click x as ratio of client area
    [double]$YR = 0.5,           # click y as ratio of client area
    [int]$Key = 27,              # virtual-key code for 'key' action
    [int]$W = 900,               # resize width
    [int]$H = 560                # resize height
)
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32I {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hWnd, ref POINT p);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr hWnd, int x, int y, int w, int h, bool repaint);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint mapType);
    public struct RECT { public int Left, Top, Right, Bottom; }
    public struct POINT { public int X, Y; }
}
"@
$proc = Get-Process | Where-Object { $_.MainWindowTitle -like "*Minecraft*" } | Select-Object -First 1
if (-not $proc) { Write-Output "NO_MC_WINDOW"; exit 1 }
[Win32I]::SetProcessDPIAware() | Out-Null
$hwnd = $proc.MainWindowHandle
[Win32I]::SetForegroundWindow($hwnd) | Out-Null
Start-Sleep -Milliseconds 400

switch ($Action) {
    "click" {
        $rect = New-Object Win32I+RECT
        [Win32I]::GetClientRect($hwnd, [ref]$rect) | Out-Null
        $p = New-Object Win32I+POINT
        $p.X = [int]($rect.Right * $XR)
        $p.Y = [int]($rect.Bottom * $YR)
        [Win32I]::ClientToScreen($hwnd, [ref]$p) | Out-Null
        [Win32I]::SetCursorPos($p.X, $p.Y) | Out-Null
        Start-Sleep -Milliseconds 150
        [Win32I]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)  # LEFTDOWN
        Start-Sleep -Milliseconds 80
        [Win32I]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)  # LEFTUP
        Write-Output "CLICKED $($p.X),$($p.Y) (ratio $XR,$YR client $($rect.Right)x$($rect.Bottom))"
    }
    "key" {
        $scan = [Win32I]::MapVirtualKey([uint32]$Key, 0)
        [Win32I]::keybd_event([byte]$Key, [byte]$scan, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 90
        [Win32I]::keybd_event([byte]$Key, [byte]$scan, 2, [UIntPtr]::Zero)  # KEYUP
        Write-Output "KEY $Key sent"
    }
    "resize" {
        $wr = New-Object Win32I+RECT
        [Win32I]::GetWindowRect($hwnd, [ref]$wr) | Out-Null
        [Win32I]::MoveWindow($hwnd, $wr.Left, $wr.Top, $W, $H, $true) | Out-Null
        Write-Output "RESIZED to ${W}x${H}"
    }
}
