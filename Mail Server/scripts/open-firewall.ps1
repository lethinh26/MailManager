<#
.SYNOPSIS
    Mở cổng TCP 8443 Inbound trên Windows Firewall cho Mail Server lắng nghe mạng LAN.
.DESCRIPTION
    Script này cấu hình tường lửa Windows (Windows Defender Firewall) để cho phép các máy PC Client
    trong mạng LAN (192.168.1.15, 192.168.1.20, 192.168.1.25) kết nối vào Mail Server (192.168.1.10)
    qua cổng HTTPS 8443.
    
    YÊU CẦU BẮT BUỘC: PHẢI CHẠY BẰNG POWERSHELL QUYỀN ADMINISTRATOR.
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\open-firewall.ps1
#>

# 1. Kiểm tra quyền Administrator
$currentPrincipal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
$isAdmin = $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "==========================================================================" -ForegroundColor Red
    Write-Host "[LỖI BẢO MẬT] SCRIPT NÀY YÊU CẦU QUYỀN POWERSHELL ADMINISTRATOR!" -ForegroundColor Red
    Write-Host "Vui lòng mở PowerShell bằng cách: Chuột phải vào biểu tượng PowerShell" -ForegroundColor Yellow
    Write-Host "và chọn 'Run as administrator' (Chạy với tư cách quản trị viên)." -ForegroundColor Yellow
    Write-Host "==========================================================================" -ForegroundColor Red
    exit 1
}

$ruleName = "MailApp Server HTTPS Port 8443"
$port = 8443

Write-Host "Đang kiểm tra luật tường lửa cho cổng $port..." -ForegroundColor Cyan

# Kiểm tra xem rule đã tồn tại chưa
$existingRule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue

if ($existingRule) {
    Write-Host "[THÔNG BÁO] Luật tường lửa '$ruleName' đã tồn tại." -ForegroundColor Green
    Set-NetFirewallRule -DisplayName $ruleName -Enabled True -Action Allow -Direction Inbound
    Write-Host "Đã cập nhật và kích hoạt luật tường lửa thành công." -ForegroundColor Green
} else {
    Write-Host "Đang tạo luật tường lửa Inbound mới cho cổng $port (TCP)..." -ForegroundColor Cyan
    New-NetFirewallRule `
        -DisplayName $ruleName `
        -Direction Inbound `
        -LocalPort $port `
        -Protocol TCP `
        -Action Allow `
        -Profile Any `
        -Description "Cho phép kết nối HTTPS Inbound tới Mail Server đồ án mạng PTIT trên cổng 8443" | Out-Null
    Write-Host "[THÀNH CÔNG] Đã mở cổng $port Inbound trên Windows Firewall cho mọi Profile (Domain, Private, Public)." -ForegroundColor Green
}

Write-Host "Kiểm tra trạng thái luật tường lửa:" -ForegroundColor Cyan
Get-NetFirewallRule -DisplayName $ruleName | Select-Object DisplayName, Direction, Action, Enabled | Format-Table -AutoSize

