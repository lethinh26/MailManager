<#
.SYNOPSIS
    Khởi động nhanh môi trường phát triển (Dev Environment) không cần build Image Server.
.DESCRIPTION
    1. Kiểm tra Docker daemon đang hoạt động.
    2. Đọc danh sách services từ `docker compose config --services` để xác định service MySQL.
    3. Bật duy nhất service MySQL trong Docker và chờ đến khi đạt trạng thái healthy.
    4. Kiểm tra/sinh keystore SSL PKCS12 tự ký nếu chưa có.
    5. Khởi chạy Mail Server bằng Gradle bootRun với SPRING_PROFILES_ACTIVE=dev.
.PARAMETER Background
    Nếu được chỉ định, server chạy ngầm dưới dạng background process và lưu PID vào .server.pid.
#>
param(
    [switch]$Background
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRootDir = Split-Path -Parent $scriptDir

Set-Location -Path $serverRootDir

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "        KHỞI ĐỘNG NHANH MÔI TRƯỜNG DEV (MYSQL DOCKER + GRADLE BOOTRUN)    " -ForegroundColor Cyan
Write-Host "==========================================================================" -ForegroundColor Cyan

# 1. Kiểm tra Docker daemon
Write-Host "[1/4] Kiểm tra dịch vụ Docker..." -ForegroundColor Yellow
try {
    $null = docker info --format '{{.ServerVersion}}' 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker không chạy"
    }
    Write-Host "  -> Docker daemon đang hoạt động tốt." -ForegroundColor Green
} catch {
    Write-Host "[LỖI] Docker Desktop chưa khởi động hoặc không khả dụng. Vui lòng bật Docker Desktop trước." -ForegroundColor Red
    exit 1
}

# 2. Đọc tên service MySQL từ docker compose config --services
Write-Host "[2/4] Xác định và bật service MySQL trong Docker..." -ForegroundColor Yellow
$services = docker compose config --services
$mysqlService = $services | Where-Object { $_ -match "mysql" } | Select-Object -First 1

if (-not $mysqlService) {
    Write-Host "[LỖI] Không tìm thấy service MySQL trong docker-compose.yml" -ForegroundColor Red
    exit 1
}

Write-Host "  -> Đang bật duy nhất service: $mysqlService" -ForegroundColor Gray
docker compose up -d $mysqlService

# 3. Chờ MySQL đạt trạng thái healthy
Write-Host "  -> Đang chờ MySQL healthy..." -ForegroundColor Gray
$maxWait = 60
$waited = 0
$isHealthy = $false
while ($waited -lt $maxWait) {
    $status = docker compose ps --format '{{.Health}}' $mysqlService 2>$null
    if ($status -eq "healthy") {
        $isHealthy = $true
        break
    }
    Start-Sleep -Seconds 2
    $waited += 2
}

if (-not $isHealthy) {
    Write-Host "[CẢNH BÁO] Quá thời gian chờ MySQL healthy ($maxWait s). Vẫn tiếp tục thử khởi động..." -ForegroundColor Yellow
} else {
    Write-Host "  -> MySQL đã đạt trạng thái HEALTHY!" -ForegroundColor Green
}

# 4. Kiểm tra chứng chỉ Keystore
Write-Host "[3/4] Kiểm tra chứng chỉ SSL/TLS Keystore..." -ForegroundColor Yellow
$keystorePath = Join-Path $serverRootDir "src\main\resources\keystore.p12"
$envLocalPath = Join-Path $serverRootDir ".env.local"

if (-not (Test-Path $keystorePath)) {
    Write-Host "  -> Chưa có $keystorePath, đang gọi gen-keystore.ps1..." -ForegroundColor Yellow
    & (Join-Path $scriptDir "gen-keystore.ps1")
}

# Nạp KEYSTORE_PASSWORD vào môi trường nếu có trong .env.local hoặc .env
if (Test-Path $envLocalPath) {
    Get-Content $envLocalPath | ForEach-Object {
        if ($_ -match "^KEYSTORE_PASSWORD=(.*)$") {
            $env:KEYSTORE_PASSWORD = $matches[1].Trim()
        }
    }
} elseif (Test-Path (Join-Path $serverRootDir ".env")) {
    Get-Content (Join-Path $serverRootDir ".env") | ForEach-Object {
        if ($_ -match "^KEYSTORE_PASSWORD=(.*)$") {
            $env:KEYSTORE_PASSWORD = $matches[1].Trim()
        }
    }
}

[System.Environment]::SetEnvironmentVariable("KEYSTORE_PASSWORD", $env:KEYSTORE_PASSWORD, "Process")
[System.Environment]::SetEnvironmentVariable("SPRING_PROFILES_ACTIVE", "dev", "Process")

# 5. Khởi chạy Mail Server
Write-Host "[4/4] Khởi động Mail Server (profile dev)..." -ForegroundColor Cyan
& .\gradlew.bat bootRun --args="--spring.profiles.active=dev" "-Dserver.ssl.key-store-password=$env:KEYSTORE_PASSWORD"

