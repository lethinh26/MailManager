<#
.SYNOPSIS
    Khởi động Mail Server với cấu hình nạp tự động từ tệp .env.local hoặc .env.
.DESCRIPTION
    Script này đọc các biến môi trường chuẩn hóa (JWT_SECRET, AES_KEY, KEYSTORE_PASSWORD,
    DB_PASSWORD, ADMIN_INITIAL_PASSWORD, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI),
    gán vào phiên làm việc hiện tại của PowerShell và khởi chạy Mail Server.
.PARAMETER Jar
    Nếu được chỉ định, script sẽ chạy tệp JAR trong build/libs/ thay vì dùng gradlew bootRun.
.PARAMETER Profile
    Spring active profile (mặc định: "dev" nếu không chỉ định).
.PARAMETER EnvFile
    Đường dẫn tệp env (mặc định tìm .env.local trước, nếu không có thì tìm .env).
.EXAMPLE
    .\scripts\run-server.ps1
.EXAMPLE
    .\scripts\run-server.ps1 -Jar
.EXAMPLE
    .\scripts\run-server.ps1 -Profile prod
#>
param(
    [switch]$Jar,
    [string]$Profile = "dev",
    [string]$EnvFile = ""
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRootDir = Split-Path -Parent $scriptDir

Set-Location -Path $serverRootDir

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "             KHỞI ĐỘNG MAIL SERVER - ĐỒ ÁN MẠNG PTIT                      " -ForegroundColor Cyan
Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "Thư mục làm việc: $serverRootDir"

# Tìm kiếm tệp .env phù hợp
$targetEnvFile = $null
if ($EnvFile -and (Test-Path $EnvFile)) {
    $targetEnvFile = $EnvFile
} elseif (Test-Path (Join-Path $serverRootDir ".env.local")) {
    $targetEnvFile = Join-Path $serverRootDir ".env.local"
} elseif (Test-Path (Join-Path $serverRootDir ".env")) {
    $targetEnvFile = Join-Path $serverRootDir ".env"
}

if ($targetEnvFile) {
    Write-Host "[CẤU HÌNH] Đang nạp biến môi trường từ: $targetEnvFile" -ForegroundColor Green
    $loadedCount = 0
    Get-Content $targetEnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $key = $parts[0].Trim()
            $value = $parts[1].Trim()
            
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            
            [System.Environment]::SetEnvironmentVariable($key, $value, "Process")
            $loadedCount++
            Write-Host "  -> Đã nạp biến: $key" -ForegroundColor Gray
        }
    }
    Write-Host "[CẤU HÌNH] Đã nạp thành công $loadedCount biến môi trường vào tiến trình." -ForegroundColor Green
} else {
    Write-Host "[CẢNH BÁO] Không tìm thấy tệp .env.local hoặc .env!" -ForegroundColor Yellow
}

# Kiểm tra keystore SSL
$keystorePath = Join-Path $serverRootDir "src\main\resources\keystore.p12"
if (-not (Test-Path $keystorePath)) {
    Write-Host "[CẢNH BÁO] Chưa tìm thấy $keystorePath. Đang tự động tạo chứng chỉ..." -ForegroundColor Yellow
    & (Join-Path $scriptDir "gen-keystore.ps1")
}

Write-Host "Spring Active Profile: $Profile" -ForegroundColor Cyan

if ($Jar) {
    $jarFiles = @(Get-ChildItem -Path (Join-Path $serverRootDir "build\libs") -Filter "*.jar" -Exclude "*plain*.jar" -ErrorAction SilentlyContinue)
    if ($jarFiles.Count -eq 0) {
        Write-Host "Chưa có file JAR sẵn sàng. Đang tiến hành build bootJar..." -ForegroundColor Cyan
        & .\gradlew.bat bootJar -x test --no-daemon
        $jarFiles = @(Get-ChildItem -Path (Join-Path $serverRootDir "build\libs") -Filter "*.jar" -Exclude "*plain*.jar")
    }

    $targetJar = $jarFiles[0].FullName
    Write-Host "Đang khởi động ứng dụng từ tệp JAR: $targetJar" -ForegroundColor Green
    & java -jar $targetJar --spring.profiles.active=$Profile
} else {
    Write-Host "Đang khởi động ứng dụng qua Gradle bootRun..." -ForegroundColor Green
    & .\gradlew.bat bootRun --args="--spring.profiles.active=$Profile" --no-daemon
}
