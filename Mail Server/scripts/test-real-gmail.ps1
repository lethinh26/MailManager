<#
.SYNOPSIS
    Chay kiem thu tich hop Gmail that (RealGmailIntegrationTest) an toan,
    KHONG luu va KHONG in email/App Password ra file hay man hinh.
.DESCRIPTION
    Doc GMAIL_TEST_EMAIL bang Read-Host va GMAIL_TEST_APP_PASSWORD bang Read-Host -AsSecureString,
    chi truyen bien moi truong cho tien trinh con Gradle; khong ghi credential vao file.
#>

param([switch]$PrepareMimeFixtures)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverDir = Split-Path -Parent $scriptDir

Write-Host "=== KIEM THU TICH HOP GMAIL THAT (RealGmailIntegrationTest) ===" -ForegroundColor Cyan
Write-Host "Luu y: Thong tin dang nhap chi ton tai trong bo nho tien trinh con, KHONG luu dia va KHONG in log." -ForegroundColor Gray
Write-Host "Chi dung hop thu Gmail TEST rieng. Bai test gui thu cho chinh no, chuyen Trash va xoa dung thu test." -ForegroundColor Yellow
if ((Read-Host "Nhap TEST-ACCOUNT de xac nhan day la hop thu kiem thu rieng") -cne 'TEST-ACCOUNT') {
    throw 'Huy kiem thu Gmail that.'
}
if ($PrepareMimeFixtures -and ((Read-Host 'Nhap KEEP-FIXTURES de giu 7 thu mau rieng cho nghiem thu thu cong') -cne 'KEEP-FIXTURES')) {
    throw 'Huy tao thu mau.'
}

$email = Read-Host "Nhap dia chi Gmail kiem thu (GMAIL_TEST_EMAIL)"
if ($email.Trim() -notmatch '^[a-zA-Z0-9.]+@gmail\.com$') {
    Write-Error "Chi dung dia chi Gmail test chinh thuc (khong alias)."
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
    $testFilters = '--tests com.mailapp.mail.RealGmailIntegrationTest'
    if ($PrepareMimeFixtures) {
        $testFilters += ' --tests com.mailapp.mail.RealGmailMimeFixturesTest'
    }
    $psi.Arguments = "/d /c gradlew.bat test $testFilters --rerun-tasks --max-workers=1 --no-daemon --console=plain"
    $psi.WorkingDirectory = $serverDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $false
    $psi.RedirectStandardError = $false

    # Chi gan bien moi truong cho tien trinh con (khong gan vao env cua shell cha)
    $psi.EnvironmentVariables["GMAIL_TEST_EMAIL"] = $email.Trim()
    $psi.EnvironmentVariables["GMAIL_TEST_APP_PASSWORD"] = $plainPwd.Replace(" ", "")
    $psi.EnvironmentVariables["MAILAPP_REAL_GMAIL"] = "1"
    $psi.EnvironmentVariables["MAILAPP_REAL_GMAIL_DEDICATED"] = "TEST-ACCOUNT"
    $psi.EnvironmentVariables["MAILAPP_REAL_GMAIL_XOAUTH2"] = "0"
    $psi.EnvironmentVariables.Remove("GMAIL_TEST_OAUTH2_ACCESS_TOKEN")
    $psi.EnvironmentVariables["MAILAPP_REAL_GMAIL_FIXTURES"] = if ($PrepareMimeFixtures) { "KEEP-FIXTURES" } else { "" }

    Write-Host "Dang chay RealGmailIntegrationTest..." -ForegroundColor Yellow
    $proc = [System.Diagnostics.Process]::Start($psi)
    $proc.WaitForExit()
    $exitCode = $proc.ExitCode
    if ($exitCode -eq 0) {
        $expectedSuites = @{ 'com.mailapp.mail.RealGmailIntegrationTest' = 2 }
        if ($PrepareMimeFixtures) { $expectedSuites['com.mailapp.mail.RealGmailMimeFixturesTest'] = 1 }
        foreach ($testClass in $expectedSuites.Keys) {
            $resultPath = Join-Path $serverDir "build/test-results/test/TEST-$testClass.xml"
            if (-not (Test-Path -LiteralPath $resultPath -PathType Leaf)) { throw 'Missing live test result.' }
            [xml]$result = Get-Content -LiteralPath $resultPath -Raw
            $suite = $result.testsuite
            if ([int]$suite.tests -ne $expectedSuites[$testClass] -or
                    [int]$suite.skipped -ne 0 -or [int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0) {
                throw "Live suite incomplete: $testClass"
            }
            Write-Host "$testClass passed with 0 skipped." -ForegroundColor Green
        }
    }
    exit $exitCode
} finally {
    # Release local references and clear the unmanaged buffer.
    if ($bstr -ne [IntPtr]::Zero) {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
    if ($null -ne $psi) {
        $psi.EnvironmentVariables.Remove("GMAIL_TEST_EMAIL")
        $psi.EnvironmentVariables.Remove("GMAIL_TEST_APP_PASSWORD")
    }
    if ($null -ne $proc) { $proc.Dispose() }
    if ($null -ne $securePwd) { $securePwd.Dispose() }
    $plainPwd = $null
    $securePwd = $null
}
