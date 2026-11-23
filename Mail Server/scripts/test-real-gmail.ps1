<#
.SYNOPSIS
    Chay kiem thu tich hop Gmail that (RealGmailIntegrationTest) an toan,
    KHONG luu va KHONG in email/App Password ra file hay man hinh.
.DESCRIPTION
    Doc GMAIL_TEST_EMAIL bang Read-Host va GMAIL_TEST_APP_PASSWORD bang Read-Host -AsSecureString,
    chi truyen bien moi truong cho tien trinh con Gradle, sau do xoa sach khoi bo nho.
#>

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverDir = Split-Path -Parent $scriptDir

Write-Host "=== KIEM THU TICH HOP GMAIL THAT (RealGmailIntegrationTest) ===" -ForegroundColor Cyan
Write-Host "Luu y: Thong tin dang nhap chi ton tai trong bo nho tien trinh con, KHONG luu dia va KHONG in log." -ForegroundColor Gray

$email = Read-Host "Nhap dia chi Gmail kiem thu (GMAIL_TEST_EMAIL)"
if ([string]::IsNullOrWhiteSpace($email)) {
    Write-Error "Email khong duoc de trong."
    exit 1
}

$securePwd = Read-Host "Nhap Gmail App Password 16 ky tu (GMAIL_TEST_APP_PASSWORD)" -AsSecureString
$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePwd)
$plainPwd = $null

try {
    $plainPwd = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    if ([string]::IsNullOrWhiteSpace($plainPwd)) {
        Write-Error "App Password khong duoc de trong."
        exit 1
    }

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/c gradlew.bat test --tests com.mailapp.mail.RealGmailIntegrationTest --rerun-tasks --console=plain"
    $psi.WorkingDirectory = $serverDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $false
    $psi.RedirectStandardError = $false

    # Chi gan bien moi truong cho tien trinh con (khong gan vao env cua shell cha)
    $psi.EnvironmentVariables["GMAIL_TEST_EMAIL"] = $email.Trim()
    $psi.EnvironmentVariables["GMAIL_TEST_APP_PASSWORD"] = $plainPwd.Replace(" ", "")

    Write-Host "Dang chay RealGmailIntegrationTest..." -ForegroundColor Yellow
    $proc = [System.Diagnostics.Process]::Start($psi)
    $proc.WaitForExit()
    $exitCode = $proc.ExitCode
    exit $exitCode
} finally {
    # Xoa sach chuoi nhay cam khoi bo nho va bien moi truong (neu co)
    if ($bstr -ne [IntPtr]::Zero) {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
    $plainPwd = $null
    $securePwd = $null
    Remove-Item Env:GMAIL_TEST_EMAIL -ErrorAction SilentlyContinue
    Remove-Item Env:GMAIL_TEST_APP_PASSWORD -ErrorAction SilentlyContinue
}
