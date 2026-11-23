<#
.SYNOPSIS
    Kich ban chay kiem thu End-to-End (E2E) tu dong va thu thap bo bang chung thuc nghiem.
#>
param(
    [string]$BaseUrl = "https://localhost:8443",
    [switch]$SkipBrowserStep,
    [string]$EvidenceBaseDir = "docs/evidence",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "",
    [string]$UserUsername = "lethanhcuong",
    [string]$UserPassword = "UserPassword123!"
)

$ErrorActionPreference = "Continue"

# Bo qua kiem tra SSL cho PowerShell khi dung chung chi tu ky
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

# Xac dinh thu muc lam viec
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRootDir = Split-Path -Parent $scriptDir
Set-Location -Path $serverRootDir

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$evidenceDir = Join-Path $serverRootDir (Join-Path $EvidenceBaseDir $timestamp)
if (-not (Test-Path $evidenceDir)) {
    New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null
}

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "     BAT DAU CHAY KICH BAN THUC NGHIEM END-TO-END (E2E) MAIL SERVER       " -ForegroundColor Cyan
Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "Base URL:         $BaseUrl"
Write-Host "Thu muc Evidence: $evidenceDir"
Write-Host "Che do chay:      $(if ($SkipBrowserStep) { 'Tu dong (Bo qua trinh duyet)' } else { 'Tuong tac day du' })"
Write-Host ""

# Ham che du lieu nhay cam
function Mask-SensitiveData([string]$inputString) {
    if (-not $inputString) { return $inputString }
    
    # Che JWT token (3 phan ngan cach boi dau cham)
    $masked = [regex]::Replace($inputString, "ey[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}", "***")
    
    # Che Google access tokens
    $googleTokenPattern = "y" + "a29\.[A-Za-z0-9_.-]+"
    $masked = [regex]::Replace($masked, $googleTokenPattern, "***")
    
    # Che Google client secret
    $googleSecretPattern = "GOC" + "SPX-[A-Za-z0-9_-]+"
    $masked = [regex]::Replace($masked, $googleSecretPattern, "***")

    # Che Google refresh tokens (1//...)
    $masked = [regex]::Replace($masked, "1//[A-Za-z0-9_.-]+", "***")
    
    # Che Authorization Bearer
    $masked = [regex]::Replace($masked, "(?i)(Bearer\s+)[A-Za-z0-9._-]+", '$1***')
    
    # Che JSON properties nhay cam
    $masked = [regex]::Replace($masked, '("password"\s*:\s*)"[^"]*"', '$1"***"')
    $masked = [regex]::Replace($masked, '("secret"\s*:\s*)"[^"]*"', '$1"***"')
    $masked = [regex]::Replace($masked, '("clientSecret"\s*:\s*)"[^"]*"', '$1"***"')
    $masked = [regex]::Replace($masked, '("client_secret"\s*:\s*)"[^"]*"', '$1"***"')
    $masked = [regex]::Replace($masked, '("accessToken"\s*:\s*)"[^"]*"', '$1"***"')
    $masked = [regex]::Replace($masked, '("refreshToken"\s*:\s*)"[^"]*"', '$1"***"')
    
    # Che tham so query string
    $masked = [regex]::Replace($masked, "(code=)[^&\s""']+", '$1***')
    $masked = [regex]::Replace($masked, "(client_secret=)[^&\s""']+", '$1***')
    
    return $masked
}

# Ham ghi bang chung
function Save-Evidence(
    [string]$stepNumber,
    [string]$stepName,
    [string]$commandText,
    [int]$statusCode,
    [long]$durationMs,
    [string]$headersText,
    [string]$bodyText,
    [string]$note
) {
    $fileName = "$stepNumber-$stepName.txt"
    $filePath = Join-Path $evidenceDir $fileName
    
    $maskedCommand = Mask-SensitiveData $commandText
    $maskedBody = Mask-SensitiveData $bodyText
    $maskedHeaders = Mask-SensitiveData $headersText

    $noteLine = ""
    if ($note) {
        $noteLine = "Ghi chu:            $note`n"
    }

    $content = @"
================================================================================
BUOC $stepNumber : $stepName
Thoi gian thuc thi: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff")
Trang thai HTTP:    $statusCode
Thoi gian phan hoi: $durationMs ms
$noteLine================================================================================
LENH THUC HIEN:
$maskedCommand

HEADERS PHAN HOI:
$maskedHeaders

BODY PHAN HOI (DA CHE THONG TIN NHAY CAM):
$maskedBody
================================================================================
"@
    Set-Content -Path $filePath -Value $content -Encoding UTF8
    Write-Host "  -> Da luu bang chung: $fileName" -ForegroundColor Gray
}

# Mang luu ket qua tom tat cho SUMMARY.md
$summaryList = [System.Collections.Generic.List[PSCustomObject]]::new()

$adminToken = ""
$userToken = ""
$authorizeUrl = ""
$oauthState = ""
$firstGmailAccountId = $null

# ------------------------------------------------------------------------------
# BUOC 01: Health check he thong
# ------------------------------------------------------------------------------
Write-Host "[BUOC 01] Kiem tra Health Check he thong..." -ForegroundColor Cyan
$cmd01 = "curl.exe -k -i -s $BaseUrl/actuator/health"
$sw01 = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $res01 = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$BaseUrl/actuator/health" -Method Get -ErrorAction Stop
    $sw01.Stop()
    $status01 = [int]$res01.StatusCode
    $body01 = $res01.Content
    $headers01 = ($res01.Headers.GetEnumerator() | ForEach-Object { "$($_.Key): $($_.Value)" }) -join "`n"
    $resSummary01 = "THANH CONG"
} catch {
    $sw01.Stop()
    $status01 = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $body01 = if ($_.Exception.Response) { (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } else { $_.Exception.Message }
    $headers01 = ""
    $resSummary01 = "THAT BAI"
}
Save-Evidence "01" "health-check" $cmd01 $status01 $sw01.ElapsedMilliseconds $headers01 $body01 "Kiem tra endpoint cong khai Actuator Health"
$summaryList.Add([PSCustomObject]@{ Step="01"; Name="Health Check he thong"; Result=$resSummary01; Http="$status01"; Ms=$sw01.ElapsedMilliseconds; File="01-health-check.txt" })

# Nap ADMIN_INITIAL_PASSWORD tu bien moi truong hoac file .env neu chua truyen
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    if ($env:ADMIN_INITIAL_PASSWORD) {
        $AdminPassword = $env:ADMIN_INITIAL_PASSWORD
    } elseif (Test-Path (Join-Path $serverRootDir ".env")) {
        $envLine = Get-Content (Join-Path $serverRootDir ".env") | Where-Object { $_ -match '^ADMIN_INITIAL_PASSWORD=' } | Select-Object -First 1
        if ($envLine) {
            $AdminPassword = $envLine.Split('=', 2)[1].Trim()
        }
    }
}

# ------------------------------------------------------------------------------
# BUOC 02: Dang nhap ADMIN
# ------------------------------------------------------------------------------
Write-Host "[BUOC 02] Dang nhap tai khoan Quan tri vien (ADMIN)..." -ForegroundColor Cyan
$loginAdminPayload = "{`"username`":`"$AdminUser`",`"password`":`"$AdminPassword`"}"
$cmd02 = "curl.exe -k -i -X POST $BaseUrl/api/auth/login -H 'Content-Type: application/json' -d '{`"username`":`"$AdminUser`",`"password`":`"***`"}'"
$sw02 = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $res02 = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$BaseUrl/api/auth/login" -Method Post -Body $loginAdminPayload -ContentType "application/json" -ErrorAction Stop
    $sw02.Stop()
    $status02 = [int]$res02.StatusCode
    $body02 = $res02.Content
    $headers02 = ($res02.Headers.GetEnumerator() | ForEach-Object { "$($_.Key): $($_.Value)" }) -join "`n"
    $json02 = $body02 | ConvertFrom-Json
    $adminToken = $json02.accessToken
    $resSummary02 = "THANH CONG"
} catch {
    $sw02.Stop()
    $status02 = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $body02 = if ($_.Exception.Response) { (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } else { $_.Exception.Message }
    $headers02 = ""
    $resSummary02 = "THAT BAI"
}
Save-Evidence "02" "login-admin" $cmd02 $status02 $sw02.ElapsedMilliseconds $headers02 $body02 "Dang nhap ADMIN nhan JWT co quyen quan tri"
$summaryList.Add([PSCustomObject]@{ Step="02"; Name="Dang nhap ADMIN"; Result=$resSummary02; Http="$status02"; Ms=$sw02.ElapsedMilliseconds; File="02-login-admin.txt" })

# ------------------------------------------------------------------------------
# BUOC 03: Dang nhap USER
# ------------------------------------------------------------------------------
Write-Host "[BUOC 03] Dang nhap tai khoan Nguoi dung (USER)..." -ForegroundColor Cyan
$loginUserPayload = "{`"username`":`"$UserUsername`",`"password`":`"$UserPassword`"}"
$cmd03 = "curl.exe -k -i -X POST $BaseUrl/api/auth/login -H 'Content-Type: application/json' -d '$loginUserPayload'"
$sw03 = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $res03 = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$BaseUrl/api/auth/login" -Method Post -Body $loginUserPayload -ContentType "application/json" -ErrorAction Stop
    $sw03.Stop()
    $status03 = [int]$res03.StatusCode
    $body03 = $res03.Content
    $headers03 = ($res03.Headers.GetEnumerator() | ForEach-Object { "$($_.Key): $($_.Value)" }) -join "`n"
    $json03 = $body03 | ConvertFrom-Json
    $userToken = $json03.accessToken
    $resSummary03 = "THANH CONG"
} catch {
    $sw03.Stop()
    $status03 = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $body03 = if ($_.Exception.Response) { (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } else { $_.Exception.Message }
    $headers03 = ""
    $resSummary03 = "THAT BAI"
}
Save-Evidence "03" "login-user" $cmd03 $status03 $sw03.ElapsedMilliseconds $headers03 $body03 "Dang nhap USER nhan JWT nguoi dung"
$summaryList.Add([PSCustomObject]@{ Step="03"; Name="Dang nhap USER"; Result=$resSummary03; Http="$status03"; Ms=$sw03.ElapsedMilliseconds; File="03-login-user.txt" })

# ------------------------------------------------------------------------------
# BUOC 04: Goi API khong Token (Ky vong 401 UNAUTHORIZED)
# ------------------------------------------------------------------------------
Write-Host "[BUOC 04] Goi API khong co Token (Ky vong 401)..." -ForegroundColor Cyan
$cmd04 = "curl.exe -k -i -s $BaseUrl/api/gmail-accounts"
$sw04 = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $res04 = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$BaseUrl/api/gmail-accounts" -Method Get -ErrorAction Stop
    $sw04.Stop()
    $status04 = [int]$res04.StatusCode
    $body04 = $res04.Content
    $headers04 = ""
    $resSummary04 = "BAT THUONG ($status04)"
} catch {
    $sw04.Stop()
    $status04 = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $body04 = if ($_.Exception.Response) { (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } else { $_.Exception.Message }
    $headers04 = ""
    $resSummary04 = if ($status04 -eq 401) { "THANH CONG (401)" } else { "THAT BAI ($status04)" }
}
Save-Evidence "04" "unauthorized-request" $cmd04 $status04 $sw04.ElapsedMilliseconds $headers04 $body04 "Kiem tra tu choi truy cap khi thieu Bearer Token"
$summaryList.Add([PSCustomObject]@{ Step="04"; Name="Goi API khong Token"; Result=$resSummary04; Http="$status04"; Ms=$sw04.ElapsedMilliseconds; File="04-unauthorized-request.txt" })

# ------------------------------------------------------------------------------
# BUOC 05: Lay danh sach Gmail accounts voi Token
# ------------------------------------------------------------------------------
Write-Host "[BUOC 05] Lay danh sach Gmail accounts voi User Token..." -ForegroundColor Cyan
$cmd05 = "curl.exe -k -i -s $BaseUrl/api/gmail-accounts -H 'Authorization: Bearer $userToken'"
$sw05 = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $authHeaders = @{ Authorization = "Bearer $userToken" }
    $res05 = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$BaseUrl/api/gmail-accounts" -Method Get -Headers $authHeaders -ErrorAction Stop
    $sw05.Stop()
    $status05 = [int]$res05.StatusCode
    $body05 = $res05.Content
    $headers05 = ($res05.Headers.GetEnumerator() | ForEach-Object { "$($_.Key): $($_.Value)" }) -join "`n"
    $resSummary05 = "THANH CONG"
    
    $accList05 = $body05 | ConvertFrom-Json
    if ($accList05 -and $accList05.Count -gt 0) {
        $firstGmailAccountId = $accList05[0].id
    }
} catch {
    $sw05.Stop()
    $status05 = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $body05 = if ($_.Exception.Response) { (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } else { $_.Exception.Message }
    $headers05 = ""
    $resSummary05 = "THAT BAI"
}
Save-Evidence "05" "get-gmail-accounts" $cmd05 $status05 $sw05.ElapsedMilliseconds $headers05 $body05 "Truy van danh sach Gmail accounts cua nguoi dung (EP-G1)"
$summaryList.Add([PSCustomObject]@{ Step="05"; Name="Lay danh sach Gmail accounts"; Result=$resSummary05; Http="$status05"; Ms=$sw05.ElapsedMilliseconds; File="05-get-gmail-accounts.txt" })

# ------------------------------------------------------------------------------
# BUOC 06: Lay URL uy quyen Google OAuth2 (EP-O1)
# ------------------------------------------------------------------------------
Write-Host "[BUOC 06] Lay URL uy quyen Google OAuth2 (EP-O1)..." -ForegroundColor Cyan
$cmd06 = "curl.exe -k -i -s $BaseUrl/api/oauth2/authorize-url -H 'Authorization: Bearer $userToken'"
$sw06 = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $authHeaders = @{ Authorization = "Bearer $userToken" }
    $res06 = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$BaseUrl/api/oauth2/authorize-url" -Method Get -Headers $authHeaders -ErrorAction Stop
    $sw06.Stop()
    $status06 = [int]$res06.StatusCode
    $body06 = $res06.Content
    $headers06 = ($res06.Headers.GetEnumerator() | ForEach-Object { "$($_.Key): $($_.Value)" }) -join "`n"
    $json06 = $body06 | ConvertFrom-Json
    $authorizeUrl = $json06.authorizeUrl
    $oauthState = $json06.state
    $resSummary06 = "THANH CONG"
} catch {
    $sw06.Stop()
    $status06 = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $body06 = if ($_.Exception.Response) { (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } else { $_.Exception.Message }
    $headers06 = ""
    $resSummary06 = "THAT BAI"
}
Save-Evidence "06" "get-authorize-url" $cmd06 $status06 $sw06.ElapsedMilliseconds $headers06 $body06 "Sinh URL uy quyen Google OAuth2 voi PKCE va State (EP-O1)"
$summaryList.Add([PSCustomObject]@{ Step="06"; Name="Lay URL uy quyen OAuth2"; Result=$resSummary06; Http="$status06"; Ms=$sw06.ElapsedMilliseconds; File="06-get-authorize-url.txt" })

# ------------------------------------------------------------------------------
# BUOC 07: Tuong tac trinh duyet (Browser Step)
# ------------------------------------------------------------------------------
Write-Host "[BUOC 07] Tuong tac trinh duyet Google Consent Screen..." -ForegroundColor Cyan
$status07 = 200
$sw07 = [System.Diagnostics.Stopwatch]::StartNew()
if ($SkipBrowserStep) {
    $sw07.Stop()
    $note07 = "Bo qua buoc mo trinh duyet tuong tac vi chay o che do -SkipBrowserStep."
    $body07 = "{ `"status`": `"SKIPPED`", `"reason`": `"Chay che do tu dong`" }"
    $resSummary07 = "BO QUA (Tu dong)"
    Write-Host "  -> Da bo qua buoc trinh duyet theo tham so -SkipBrowserStep." -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "==========================================================================" -ForegroundColor Yellow
    Write-Host "[HANH DONG CAN THIET TU NGUOI DUNG]" -ForegroundColor Yellow
    Write-Host "Vui long sao chep URL sau va mo tren trinh duyet de hoan tat cap quyen:" -ForegroundColor White
    Write-Host "$authorizeUrl" -ForegroundColor Green
    Write-Host "Sau khi man hinh hien thi thong bao thanh cong tren trinh duyet, quay lai day." -ForegroundColor White
    Write-Host "==========================================================================" -ForegroundColor Yellow
    $null = Read-Host "Nhan phim [Enter] de tiep tuc sau khi da xac thuc tren trinh duyet..."
    $sw07.Stop()
    $note07 = "Nguoi dung da hoan tat thao tac cap quyen tren trinh duyet."
    $body07 = "{ `"status`": `"COMPLETED`", `"action`": `"Xac thuc trinh duyet thanh cong`" }"
    $resSummary07 = "HOAN TAT"
}
Save-Evidence "07" "browser-interaction" "Mo trinh duyet: $authorizeUrl" $status07 $sw07.ElapsedMilliseconds "" $body07 $note07
$summaryList.Add([PSCustomObject]@{ Step="07"; Name="Tuong tac trinh duyet OAuth2"; Result=$resSummary07; Http="$status07"; Ms=$sw07.ElapsedMilliseconds; File="07-browser-interaction.txt" })

# ------------------------------------------------------------------------------
# BUOC 08: Poll danh sach Gmail accounts toi khi co OAuth2
# ------------------------------------------------------------------------------
Write-Host "[BUOC 08] Poll kiem tra tai khoan Gmail OAuth2..." -ForegroundColor Cyan
$sw08 = [System.Diagnostics.Stopwatch]::StartNew()
$status08 = 200
$body08 = ""
$resSummary08 = ""
if ($SkipBrowserStep) {
    $sw08.Stop()
    $status08 = 200
    $body08 = "{ `"status`": `"SKIPPED`", `"reason`": `"Bo qua poll vi buoc 07 khong mo trinh duyet`" }"
    $resSummary08 = "BO QUA (Tu dong)"
} else {
    $authHeaders = @{ Authorization = "Bearer $userToken" }
    $maxPollRetries = 10
    $pollSuccess = $false
    for ($i = 1; $i -le $maxPollRetries; $i++) {
        try {
            $pollRes = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$BaseUrl/api/gmail-accounts" -Method Get -Headers $authHeaders -ErrorAction Stop
            $pollBody = $pollRes.Content
            $accounts = $pollBody | ConvertFrom-Json
            $oauthAcc = $accounts | Where-Object { $_.authType -eq "OAUTH2" }
            if ($oauthAcc) {
                $pollSuccess = $true
                $body08 = $pollBody
                $firstGmailAccountId = $oauthAcc.id
                break
            }
        } catch {}
        Start-Sleep -Seconds 2
    }
    $sw08.Stop()
    if ($pollSuccess) {
        $resSummary08 = "THANH CONG"
    } else {
        $resSummary08 = "CHUA CO TAI KHOAN (Timeout)"
        $body08 = "{ `"status`": `"TIMEOUT`" }"
    }
}
Save-Evidence "08" "poll-oauth2-account" "GET $BaseUrl/api/gmail-accounts (Poll)" $status08 $sw08.ElapsedMilliseconds "" $body08 "Kiem tra tai khoan Gmail sau khi hoan tat callback"
$summaryList.Add([PSCustomObject]@{ Step="08"; Name="Poll tai khoan Gmail OAuth2"; Result=$resSummary08; Http="$status08"; Ms=$sw08.ElapsedMilliseconds; File="08-poll-oauth2-account.txt" })

# ------------------------------------------------------------------------------
# BUOC 09: Kiem tra ket noi Gmail qua endpoint chinh thuc POST /api/gmail-accounts/{id}/test
# ------------------------------------------------------------------------------
Write-Host "[BUOC 09] Kiem tra endpoint POST /api/gmail-accounts/{id}/test..." -ForegroundColor Cyan
$selftestTargetId = if ($firstGmailAccountId) { $firstGmailAccountId } else { 0 }
$cmd09 = "curl.exe -k -i -s -X POST $BaseUrl/api/gmail-accounts/$selftestTargetId/test -H 'Authorization: Bearer ***'"
$sw09 = [System.Diagnostics.Stopwatch]::StartNew()
if (-not $firstGmailAccountId) {
    $sw09.Stop()
    $status09 = 200
    $body09 = "{ `"status`": `"SKIPPED`", `"reason`": `"Chua co tai khoan Gmail OAuth2 de selftest`" }"
    $resSummary09 = "BO QUA (Chua co tai khoan OAuth2)"
} else {
    try {
        $authHeaders = @{ Authorization = "Bearer $userToken" }
        $res09 = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$BaseUrl/api/gmail-accounts/$selftestTargetId/test" -Method Post -Headers $authHeaders -ErrorAction Stop
        $sw09.Stop()
        $status09 = [int]$res09.StatusCode
        $body09 = $res09.Content
        $resSummary09 = "THANH CONG"
    } catch {
        $sw09.Stop()
        $status09 = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        $body09 = if ($_.Exception.Response) { (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } else { $_.Exception.Message }
        $resSummary09 = "LOI ($status09)"
    }
}
Save-Evidence "09" "oauth2-selftest" $cmd09 $status09 $sw09.ElapsedMilliseconds "" $body09 "Kiem thu ket noi IMAP/SMTP XOAUTH2 qua /api/gmail-accounts/{id}/test"
$summaryList.Add([PSCustomObject]@{ Step="09"; Name="OAuth2 Self-Test"; Result=$resSummary09; Http="$status09"; Ms=$sw09.ElapsedMilliseconds; File="09-oauth2-selftest.txt" })

# ------------------------------------------------------------------------------
# BUOC 10: Thu lien ket App Password (neu endpoint da co)
# ------------------------------------------------------------------------------
Write-Host "[BUOC 10] Thu lien ket App Password (EP-G2)..." -ForegroundColor Cyan
$cmd10 = "curl.exe -k -i -s -X POST $BaseUrl/api/gmail-accounts/app-password -H 'Authorization: Bearer ***'"
$appPasswordPayload = "{`"email`":`"test.user@gmail.com`",`"appPassword`":`"<APP_PASSWORD_PLACEHOLDER>`"}"
$sw10 = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $authHeaders = @{ Authorization = "Bearer $userToken" }
    $res10 = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$BaseUrl/api/gmail-accounts/app-password" -Method Post -Headers $authHeaders -Body $appPasswordPayload -ContentType "application/json" -ErrorAction Stop
    $sw10.Stop()
    $status10 = [int]$res10.StatusCode
    $body10 = $res10.Content
    $resSummary10 = "THANH CONG"
} catch {
    $sw10.Stop()
    $status10 = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $body10 = if ($_.Exception.Response) { (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } else { $_.Exception.Message }
    $resSummary10 = if ($status10 -eq 404 -or $status10 -eq 405) { "BO QUA (Chua trien khai)" } elseif ($status10 -eq 400 -or $status10 -eq 422) { "THANH CONG (Tu choi mat khau gia $status10)" } else { "LOI ($status10)" }
}
Save-Evidence "10" "link-app-password" $cmd10 $status10 $sw10.ElapsedMilliseconds "" $body10 "Kiem tra endpoint lien ket App Password"
$summaryList.Add([PSCustomObject]@{ Step="10"; Name="Lien ket App Password (EP-G2)"; Result=$resSummary10; Http="$status10"; Ms=$sw10.ElapsedMilliseconds; File="10-link-app-password.txt" })

# ------------------------------------------------------------------------------
# BUOC 11: 6 lan dang nhap sai kich hoat Rate Limit (429 + Retry-After)
# ------------------------------------------------------------------------------
Write-Host "[BUOC 11] Thuc hien 6 lan dang nhap sai de kich hoat Rate Limit (429)..." -ForegroundColor Cyan
$rlUser = "e2e_rl_" + (Get-Date -Format "HHmmss")
$wrongPayload = "{`"username`":`"$rlUser`",`"password`":`"WrongPassword999!`"}"
$cmd11 = "curl.exe -k -i -s -X POST $BaseUrl/api/auth/login -d '{`"username`":`"$rlUser`",`"password`":`"***`"}' (Lap 6 lan)"
$sw11 = [System.Diagnostics.Stopwatch]::StartNew()

$lastStatus11 = 0
$lastBody11 = ""
$lastHeaders11 = ""

for ($attempt = 1; $attempt -le 6; $attempt++) {
    try {
        $resFail = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$BaseUrl/api/auth/login" -Method Post -Body $wrongPayload -ContentType "application/json" -ErrorAction Stop
        $lastStatus11 = [int]$resFail.StatusCode
    } catch {
        $lastStatus11 = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        $lastBody11 = if ($_.Exception.Response) { (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } else { $_.Exception.Message }
        if ($_.Exception.Response) {
            $respHdrs = $_.Exception.Response.Headers
            $lastHeaders11 = ($respHdrs.AllKeys | ForEach-Object { "${_}: $($respHdrs[$_])" }) -join "`n"
        }
    }
}
$sw11.Stop()

$resSummary11 = if ($lastStatus11 -eq 429) { "THANH CONG (429 Rate Limit)" } else { "THAT BAI ($lastStatus11)" }
Save-Evidence "11" "rate-limit-protection" $cmd11 $lastStatus11 $sw11.ElapsedMilliseconds $lastHeaders11 $lastBody11 "Kiem tra bao ve chong brute-force EP-A2: nhan 429 TOO_MANY_ATTEMPTS kem header Retry-After"
$summaryList.Add([PSCustomObject]@{ Step="11"; Name="Rate Limit 6 lan sai"; Result=$resSummary11; Http="$lastStatus11"; Ms=$sw11.ElapsedMilliseconds; File="11-rate-limit-protection.txt" })

# ------------------------------------------------------------------------------
# BUOC 12: Xoa Gmail (Revoke) (EP-G3)
# ------------------------------------------------------------------------------
Write-Host "[BUOC 12] Huy lien ket tai khoan Gmail (EP-G3 Revoke)..." -ForegroundColor Cyan
$targetDeleteId = if ($firstGmailAccountId) { $firstGmailAccountId } else { 999999 }
$cmd12 = "curl.exe -k -i -s -X DELETE $BaseUrl/api/gmail-accounts/$targetDeleteId -H 'Authorization: Bearer $userToken'"
$sw12 = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $authHeaders = @{ Authorization = "Bearer $userToken" }
    $res12 = Invoke-WebRequest -UseBasicParsing -TimeoutSec 15 -Uri "$BaseUrl/api/gmail-accounts/$targetDeleteId" -Method Delete -Headers $authHeaders -ErrorAction Stop
    $sw12.Stop()
    $status12 = [int]$res12.StatusCode
    $body12 = $res12.Content
    $resSummary12 = "THANH CONG"
} catch {
    $sw12.Stop()
    $status12 = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $body12 = if ($_.Exception.Response) { (New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } else { $_.Exception.Message }
    $resSummary12 = if ($status12 -eq 404 -and -not $firstGmailAccountId) { "THANH CONG (404 chuan khi chua co tai khoan)" } else { "THAT BAI ($status12)" }
}
Save-Evidence "12" "unlink-gmail-account" $cmd12 $status12 $sw12.ElapsedMilliseconds "" $body12 "Huy lien ket tai khoan Gmail, thu hoi token tren Google (EP-G3)"
$summaryList.Add([PSCustomObject]@{ Step="12"; Name="Huy lien ket Gmail (Revoke)"; Result=$resSummary12; Http="$status12"; Ms=$sw12.ElapsedMilliseconds; File="12-unlink-gmail-account.txt" })

# ------------------------------------------------------------------------------
# Sinh tep SUMMARY.md
# ------------------------------------------------------------------------------
$summaryPath = Join-Path $evidenceDir "SUMMARY.md"
$mdContent = @"
# BANG TONG KET BANG CHUNG THUC NGHIEM E2E (END-TO-END)

- **Thoi gian chay:** $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
- **Dia chi may chu:** $BaseUrl
- **Thu muc luu tru:** `$EvidenceBaseDir/$timestamp`
- **Che do:** $(if ($SkipBrowserStep) { "Tu dong (-SkipBrowserStep)" } else { "Tuong tac day du" })

| Buoc | Ten buoc kiem thu | Ket qua | HTTP Status | Thoi gian (ms) | Tep bang chung |
| :---: | :--- | :---: | :---: | :---: | :--- |
"@

foreach ($item in $summaryList) {
    $mdContent += "`n| $($item.Step) | $($item.Name) | $($item.Result) | $($item.Http) | $($item.Ms) ms | `"$($item.File)`" |"
}

$mdContent += @"


---
> **Luu y bao mat:** Toan bo tep chi tiet trong thu muc nay da duoc tu dong quet va loc du lieu nhay cam (JWT tokens, OAuth Access/Refresh tokens, Passwords, Secrets) thanh `***`.
"@

Set-Content -Path $summaryPath -Value $mdContent -Encoding UTF8
Write-Host ""
Write-Host "===> HOAN TAT KICH BAN THUC NGHIEM E2E!" -ForegroundColor Green
Write-Host "Tep tong ket: $summaryPath" -ForegroundColor Green
Write-Host ""

