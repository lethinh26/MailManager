<#
.SYNOPSIS
    Tạo chứng chỉ SSL/TLS tự ký (Self-signed Certificate) chuẩn PKCS12 cho Mail Server
    và tự động đồng bộ chứng chỉ công khai mailapp.cer sang Mail Client.
.PARAMETER AdditionalIps
    Danh sách địa chỉ IP bổ sung cho SAN (ví dụ: "192.168.1.74").
.PARAMETER AdditionalDns
    Danh sách tên miền DNS bổ sung (ví dụ: "mail.ptit.local").
.PARAMETER KeystorePass
    Mật khẩu Keystore (mặc định đọc từ biến môi trường KEYSTORE_PASSWORD hoặc tệp .env).
#>
param(
    [string[]]$AdditionalIps = @(),
    [string[]]$AdditionalDns = @(),
    [string]$KeystorePass = $env:KEYSTORE_PASSWORD,
    [string]$KeystoreFile = "",
    [string]$CertFile = "",
    [string]$Alias = "mailapp",
    [int]$ValidityDays = 365
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRootDir = Split-Path -Parent $scriptDir
$repoRootDir = Split-Path -Parent $serverRootDir

if (-not $KeystoreFile) {
    $KeystoreFile = Join-Path $serverRootDir "certs\keystore.p12"
}
if (-not $CertFile) {
    $CertFile = Join-Path $serverRootDir "certs\mailapp.cer"
}
$clientCertFile = Join-Path $repoRootDir "Mail Client\certs\mailapp.cer"
$envPath = Join-Path $serverRootDir ".env"
$envLocalPath = Join-Path $serverRootDir ".env.local"

# Tìm keytool (ưu tiên PATH, fallback JAVA_HOME)
$keytoolCmd = Get-Command keytool -ErrorAction SilentlyContinue
if ($keytoolCmd) {
    $keytoolBin = $keytoolCmd.Source
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\keytool.exe"))) {
    $keytoolBin = Join-Path $env:JAVA_HOME "bin\keytool.exe"
} else {
    Write-Error "Khong tim thay 'keytool'. Hay cai JDK 21 va them JAVA_HOME\bin vao PATH."
    exit 1
}

# Nếu chưa truyền KeystorePass, thử đọc từ Mail Server/.env
if (-not $KeystorePass -and (Test-Path $envPath)) {
    $line = Get-Content $envPath | Where-Object { $_ -match '^KEYSTORE_PASSWORD=.+' } | Select-Object -First 1
    if ($line) {
        $KeystorePass = $line.Substring('KEYSTORE_PASSWORD='.Length).Trim()
    }
}

if (-not $KeystorePass) {
    $chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    $rnd = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] 24
    $rnd.GetBytes($bytes)
    $passBuilder = [System.Text.StringBuilder]::new()
    for ($i = 0; $i -lt 24; $i++) {
        [void]$passBuilder.Append($chars[[int]$bytes[$i] % $chars.Length])
    }
    $KeystorePass = $passBuilder.ToString()
    Write-Host "[THONG BAO] Bien KEYSTORE_PASSWORD chua dat. Da sinh mat khau ngau nhien va luu vao .env.local." -ForegroundColor Yellow
    Set-Content -Path $envLocalPath -Value "KEYSTORE_PASSWORD=$KeystorePass" -Encoding UTF8
    $env:KEYSTORE_PASSWORD = $KeystorePass
}

$sanList = [System.Collections.Generic.List[string]]::new()
$sanList.Add("dns:localhost")
$sanList.Add("ip:127.0.0.1")
$sanList.Add("dns:mailapp-server")
$sanList.Add("ip:192.168.1.10")
foreach ($ip in $AdditionalIps) {
    if ($ip -and -not $sanList.Contains("ip:$ip")) { $sanList.Add("ip:$ip") }
}
foreach ($dns in $AdditionalDns) {
    if ($dns -and -not $sanList.Contains("dns:$dns")) { $sanList.Add("dns:$dns") }
}
$sanString = [string]::Join(",", $sanList)

New-Item -ItemType Directory -Path (Split-Path -Parent $KeystoreFile) -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $CertFile) -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $clientCertFile) -Force | Out-Null

if (Test-Path $KeystoreFile) { Remove-Item $KeystoreFile -Force }
if (Test-Path $CertFile) { Remove-Item $CertFile -Force }

Write-Host "Dang sinh keystore PKCS12 ($sanString)..." -ForegroundColor Cyan
& $keytoolBin -genkeypair `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -storetype PKCS12 `
    -keystore $KeystoreFile `
    -validity $ValidityDays `
    -storepass $KeystorePass `
    -keypass $KeystorePass `
    -dname "CN=MailApp Server, OU=PTIT, O=FinalProject, L=Hanoi, C=VN" `
    -ext "SAN=$sanString"
if ($LASTEXITCODE -ne 0) { Write-Error "Loi sinh keystore!"; exit 1 }

Write-Host "Dang xuat chung chi cong khai .cer va dong bo sang Mail Client..." -ForegroundColor Cyan
& $keytoolBin -exportcert `
    -alias $Alias `
    -keystore $KeystoreFile `
    -storetype PKCS12 `
    -storepass $KeystorePass `
    -file $CertFile `
    -rfc
if ($LASTEXITCODE -ne 0) { Write-Error "Loi xuat chung chi .cer!"; exit 1 }

Copy-Item -Path $CertFile -Destination $clientCertFile -Force

# Nếu thư mục certs ở gốc repo tồn tại, đồng bộ luôn để tránh lệch chứng chỉ khi chạy từ IntelliJ root
$rootCertsDir = Join-Path $repoRootDir "certs"
if (Test-Path $rootCertsDir) {
    Copy-Item -Path $CertFile -Destination (Join-Path $rootCertsDir "mailapp.cer") -Force
}

# Đồng bộ thêm bản cho dev bootRun ngoài Docker (src/main/resources)
$resDir = Join-Path $serverRootDir "src\main\resources"
if (Test-Path $resDir) {
    Copy-Item -Path $KeystoreFile -Destination (Join-Path $resDir "keystore.p12") -Force
    Copy-Item -Path $CertFile -Destination (Join-Path $resDir "mailapp.cer") -Force
}

Write-Host "===> HOAN TAT SINH VA DONG BO CHUNG CHI SSL!" -ForegroundColor Green
Write-Host "  Server Keystore: $KeystoreFile"
Write-Host "  Server Cert:     $CertFile"
Write-Host "  Client Cert:     $clientCertFile"
