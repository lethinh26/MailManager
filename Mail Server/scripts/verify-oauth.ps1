<#
.SYNOPSIS
    Kiem tra tai khoan Gmail OAuth2 da lien ket va chay selftest IMAP/SMTP XOAUTH2 (POST /api/dev/oauth2-selftest/{id}).
    Khong luu mat khau hay token; che email khi in va luu bang chung vao docs/evidence/.
#>
param(
    [string]$BaseUrl = "https://localhost:8443",
    [string]$EvidenceBaseDir = "docs/evidence",
    [string]$Username = "",
    [switch]$UseEnvPassword
)

$ErrorActionPreference = "Stop"

# Bo qua kiem tra SSL tự ký trong PowerShell cho môi trường dev
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRootDir = Split-Path -Parent $scriptDir
Set-Location -Path $serverRootDir

function Mask-Email([string]$email) {
    if (-not $email -or -not $email.Contains("@")) { return "***" }
    $parts = $email.Split("@")
    $local = $parts[0]
    $prefix = if ($local.Length -gt 1) { $local.Substring(0, 1) } else { "*" }
    return "${prefix}***@$($parts[1])"
}

function Mask-Secrets([string]$text) {
    if (-not $text) { return $text }
    $m = [regex]::Replace($text, "ey[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}", "***")
    $gTok = "y" + "a29\.[A-Za-z0-9_.-]+"
    $m = [regex]::Replace($m, $gTok, "***")
    $m = [regex]::Replace($m, "1//[A-Za-z0-9_.-]+", "***")
    $m = [regex]::Replace($m, '(?i)(Bearer\s+)[A-Za-z0-9._-]+', '$1***')
    return $m
}

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "       KIEM CHUNG TAI KHOAN GMAIL OAUTH2 & XOAUTH2 SELFTEST               " -ForegroundColor Cyan
Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl"

# 1. Nhap thong tin dang nhap (khong luu)
if ([string]::IsNullOrWhiteSpace($Username)) {
    $Username = Read-Host "Nhap ten dang nhap Mail App"
}

$plainPassword = ""
if ($UseEnvPassword -and $env:VERIFY_OAUTH_PASSWORD) {
    $plainPassword = $env:VERIFY_OAUTH_PASSWORD
} else {
    $securePass = Read-Host "Nhap mat khau" -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePass)
    try {
        $plainPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add("================================================================================")
$lines.Add("KET QUA VERIFY-OAUTH (DA CHE THONG TIN NHAY CAM)")
$lines.Add("Thoi gian: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')")
$lines.Add("Base URL:  $BaseUrl")
$lines.Add("Username:  $Username")
$lines.Add("================================================================================")

# 2. Dang nhap lay JWT (giu trong RAM tam thoi, khong in)
Write-Host "[1/3] Dang nhap POST /api/auth/login..." -ForegroundColor Cyan
$loginBody = @{ username = $Username; password = $plainPassword } | ConvertTo-Json -Compress
$plainPassword = "" # Xoa mat khau trong RAM lap tuc

$token = ""
try {
    $loginRes = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    $token = $loginRes.accessToken
    $lines.Add("[1/3] Dang nhap: THANH CONG (role=$($loginRes.user.role), token=***)")
    Write-Host "  -> Dang nhap thanh cong (role=$($loginRes.user.role))." -ForegroundColor Green
} catch {
    $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $lines.Add("[1/3] Dang nhap: THAT BAI (HTTP $status)")
    Write-Host "  -> Dang nhap that bai (HTTP $status)." -ForegroundColor Red
    $outPath = Join-Path $serverRootDir (Join-Path $EvidenceBaseDir "verify-oauth-$(Get-Date -Format 'yyyy-MM-dd_HH-mm-ss').txt")
    New-Item -ItemType Directory -Path (Split-Path $outPath) -Force | Out-Null
    Set-Content -Path $outPath -Value ($lines -join "`n") -Encoding UTF8
    exit 1
}

$headers = @{ Authorization = "Bearer $token" }

# 3. Lay danh sach Gmail accounts
Write-Host "[2/3] Lay danh sach GET /api/gmail-accounts..." -ForegroundColor Cyan
$accounts = @()
try {
    $accounts = Invoke-RestMethod -Uri "$BaseUrl/api/gmail-accounts" -Method Get -Headers $headers
    $count = if ($accounts) { @($accounts).Count } else { 0 }
    $lines.Add("[2/3] GET /api/gmail-accounts: THANH CONG (tong so tai khoan: $count)")
    Write-Host "  -> Tim thay $count tai khoan Gmail." -ForegroundColor Green
} catch {
    $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $lines.Add("[2/3] GET /api/gmail-accounts: THAT BAI (HTTP $status)")
    Write-Host "  -> Loi lay danh sach Gmail (HTTP $status)." -ForegroundColor Red
    exit 1
}

$oauthAccounts = @($accounts | Where-Object { $_.authType -eq "OAUTH2" })
if ($oauthAccounts.Count -eq 0) {
    $msg = "Chua co tai khoan Gmail OAUTH2 nao duoc lien ket cho user '$Username'. Hay lien ket Gmail qua trinh duyet truoc khi chay selftest."
    $lines.Add("[3/3] OAuth2 Selftest: BO QUA - $msg")
    Write-Host "  -> $msg" -ForegroundColor Yellow
} else {
    foreach ($acc in $oauthAccounts) {
        $maskedMail = Mask-Email $acc.email
        Write-Host "[3/3] Goi POST /api/gmail-accounts/$($acc.id)/test cho $maskedMail..." -ForegroundColor Cyan
        try {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            $selfRes = Invoke-RestMethod -Uri "$BaseUrl/api/gmail-accounts/$($acc.id)/test" -Method Post -Headers $headers
            $sw.Stop()
            $summary = "accountId=$($acc.id), email=$maskedMail, status=$($acc.status), imapOk=$($selfRes.imapOk), inboxCount=$($selfRes.inboxCount), smtpOk=$($selfRes.smtpOk), durationMs=$($selfRes.durationMs)"
            $lines.Add("[3/3] OAuth2 Selftest: THANH CONG - $summary")
            Write-Host "  -> KET QUA: $summary" -ForegroundColor Green
        } catch {
            $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            $errBody = if ($_.Exception.Response) { (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } else { $_.Exception.Message }
            $safeErr = Mask-Secrets $errBody
            $lines.Add("[3/3] OAuth2 Selftest ($maskedMail, id=$($acc.id)): HTTP $status - $safeErr")
            Write-Host "  -> Selftest tra ve HTTP $status ($maskedMail)" -ForegroundColor Yellow
        }
    }
}

$token = "" # Xoa token khoi bo nho

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$outFile = Join-Path $serverRootDir (Join-Path $EvidenceBaseDir "verify-oauth-$timestamp.txt")
New-Item -ItemType Directory -Path (Split-Path $outFile) -Force | Out-Null
Set-Content -Path $outFile -Value ($lines -join "`n") -Encoding UTF8

Write-Host ""
Write-Host "Da luu bang chung (da che bi mat) tai: $outFile" -ForegroundColor Cyan
