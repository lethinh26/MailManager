<#
.SYNOPSIS
    Dừng môi trường phát triển cục bộ (tắt Mail Server và dừng container MySQL).
#>
$ErrorActionPreference = "SilentlyContinue"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRootDir = Split-Path -Parent $scriptDir

Set-Location -Path $serverRootDir

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "                 DỪNG MÔI TRƯỜNG DEV (SERVER + DOCKER MYSQL)              " -ForegroundColor Cyan
Write-Host "==========================================================================" -ForegroundColor Cyan

# 1. Dừng Server nếu có ghi PID hoặc đang chiếm port 8443
$pidFile = Join-Path $serverRootDir ".server.pid"
if (Test-Path $pidFile) {
    $savedPid = Get-Content $pidFile
    if ($savedPid) {
        Write-Host "[1/2] Đang dừng tiến trình Server (PID=$savedPid)..." -ForegroundColor Yellow
        Stop-Process -Id $savedPid -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

# Kiểm tra port 8443 để chắc chắn giải phóng
$conn = Get-NetTCPConnection -LocalPort 8443 -ErrorAction SilentlyContinue
if ($conn) {
    $pids = $conn | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($p in $pids) {
        Write-Host "  -> Giải phóng port 8443 (PID=$p)..." -ForegroundColor Gray
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    }
}
Write-Host "  -> Port 8443 đã giải phóng." -ForegroundColor Green

# 2. Dừng MySQL container
Write-Host "[2/2] Dừng container MySQL trong Docker..." -ForegroundColor Yellow
$services = docker compose config --services 2>$null
$mysqlService = $services | Where-Object { $_ -match "mysql" } | Select-Object -First 1

if ($mysqlService) {
    docker compose stop $mysqlService
    Write-Host "  -> Đã dừng service $mysqlService." -ForegroundColor Green
} else {
    docker compose stop
}

Write-Host "===> Toàn bộ môi trường dev đã dừng an toàn." -ForegroundColor Green

