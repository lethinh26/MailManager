# Disposable HTTPS + isolated H2/MySQL server and loopback GreenMail fixture.
# Never connect to an existing Mail Server, database or Gmail.
param(
    [Parameter(Mandatory = $true)]
    [string]$Java21Home,
    [switch]$SkipBuild,
    [ValidateSet('H2','MySQL')]
    [string]$DatabaseEngine = 'H2',
    [string]$MySqlBin,
    [string[]]$ClientTests,
    [switch]$VerifyDatabaseOnly,
    [switch]$VerifyLegacyUpgrade,
    [switch]$VerifyAuditUpgrade,
    [switch]$VerifyAccountRevisionUpgrade,
    [switch]$VerifyConfiguredPort,
    [switch]$VerifyPublicConfigFault,
    [switch]$RunPostman,
    [switch]$ManualSession,
    [switch]$ManualPostman,
    [switch]$ManualFaults,
    [switch]$ManualStalls,
    [switch]$ManualAuditFaults,
    [switch]$ManualAdminOverlap,
    [ValidateRange(1,128)][int]$ManualAuditCapacity = 128,
    [switch]$ManualLeases,
    [ValidateRange(60,3600)][int]$ManualSeconds = 1800
)
$ErrorActionPreference = 'Stop'
$serverDir = Split-Path -Parent $PSScriptRoot
$clientDir = Join-Path (Split-Path -Parent $serverDir) 'Mail Client'
$java = Join-Path $Java21Home 'bin\java.exe'
$keytool = Join-Path $Java21Home 'bin\keytool.exe'
if (!(Test-Path -LiteralPath $java) -or !(Test-Path -LiteralPath $keytool)) {
    throw 'Java 21 JDK required.'
}
$release = Get-Content -LiteralPath (Join-Path $Java21Home 'release') -Raw
if ($release -notmatch 'JAVA_VERSION="21\.') { throw 'Specify a Java 21 JDK.' }
$env:JAVA_HOME = $Java21Home
if ($VerifyDatabaseOnly -and $RunPostman) { throw 'Choose database-only verification or full HTTP acceptance.' }
if ($VerifyLegacyUpgrade -and (!$VerifyDatabaseOnly -or $DatabaseEngine -ne 'MySQL')) { throw 'Legacy upgrade requires isolated MySQL database-only mode.' }
if ($VerifyAuditUpgrade -and (!$VerifyDatabaseOnly -or $DatabaseEngine -ne 'MySQL' -or $VerifyLegacyUpgrade -or $VerifyConfiguredPort -or $VerifyPublicConfigFault)) { throw 'Audit upgrade requires its own isolated MySQL database-only run.' }
if ($VerifyAccountRevisionUpgrade -and (!$VerifyDatabaseOnly -or $DatabaseEngine -ne 'MySQL' -or $VerifyLegacyUpgrade -or $VerifyAuditUpgrade -or $VerifyConfiguredPort -or $VerifyPublicConfigFault)) { throw 'Account revision upgrade requires its own isolated MySQL database-only run.' }
if ($VerifyConfiguredPort -and !$VerifyDatabaseOnly) { throw 'Configured-port verification requires database-only mode.' }
if ($VerifyPublicConfigFault -and (!$VerifyDatabaseOnly -or $DatabaseEngine -ne 'MySQL' -or $VerifyLegacyUpgrade -or $VerifyConfiguredPort)) { throw 'Public config fault requires its own fresh MySQL database-only run.' }
if ($ManualSession -and ($VerifyDatabaseOnly -or $RunPostman -or $ClientTests)) {
    throw 'Manual session must be independent of automated tests.'
}
if ($ManualPostman -and !$ManualSession) { throw '-ManualPostman requires -ManualSession.' }
if ($ManualFaults -and !$ManualSession) { throw '-ManualFaults requires -ManualSession.' }
if ($ManualStalls -and !$ManualFaults) { throw '-ManualStalls requires -ManualFaults.' }
if ($ManualAuditFaults -and (!$ManualSession -or $DatabaseEngine -ne 'MySQL')) { throw '-ManualAuditFaults requires a fresh MySQL manual fixture.' }
if ($ManualAdminOverlap -and !$ManualAuditFaults) { throw '-ManualAdminOverlap requires the owned native MySQL fault fixture.' }
if ($ManualAuditCapacity -ne 128 -and !$ManualAuditFaults) { throw '-ManualAuditCapacity requires a fresh MySQL audit-fault fixture.' }
if ($ManualLeases -and !$ManualSession) { throw '-ManualLeases requires a manual fixture.' }
if (!$SkipBuild) {
    Push-Location $serverDir
    try {
        & .\gradlew.bat bootJar --no-daemon --max-workers=1 --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'bootJar failed.' }
    } finally { Pop-Location }
}
$jar = Get-ChildItem (Join-Path $serverDir 'build\libs') -Filter '*.jar' |
    Where-Object { $_.Name -notlike '*-plain.jar' } | Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (!$jar) { throw 'No bootJar found.' }
$tempDir = Join-Path ([IO.Path]::GetTempPath()) ('mailapp-e2e-' + [Guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $tempDir
$process = $null
$listener = $null
$imapListener = $null
$smtpListener = $null
$mysqlListener = $null
$oauthListener = $null
$oauthProcess = $null
$oauthPort = 1
$mysqlProcess = $null
$mysqlData = $null
$manualMailProcess = $null
$manualPointer = Join-Path $serverDir 'build\acceptance\manual-session.json'
$manualManifest = Join-Path $tempDir 'manual-private.json'
$manualStop = Join-Path $tempDir 'stop-manual'
$failed = $true
$legacyEvidence = @()
function Reserve-FixturePort {
    # Windows' ephemeral client-port range normally starts at 49152.
    # Reserve a random lower port to avoid outgoing HTTPS/Gradle connections
    # acquiring the same port after we release it to GreenMail.
    for ($i = 0; $i -lt 100; $i++) {
        $candidate = Get-Random -Minimum 20000 -Maximum 40000
        $socket = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $candidate)
        try {
            $socket.Start()
            return $socket
        } catch {
            $socket.Stop()
        }
    }
    throw 'No free loopback fixture port found.'
}
function Get-FixtureListenerPid([int]$Port) {
    $lines = netstat.exe -ano -p tcp | Where-Object { $_ -match "^\s*TCP\s+127\.0\.0\.1:$Port\s+\S+\s+LISTENING\s+\d+\s*$" }
    foreach ($line in $lines) { [int](($line.Trim() -split '\s+')[-1]) }
}
function Get-OwnedMySqlProcesses {
    if (!$mysqlData) { return }
    # mysqld on Windows may launch a second process that owns the listener.
    # Only processes explicitly using our unique datadir are eligible.
    Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object {
        $_.CommandLine -and $_.CommandLine.IndexOf($mysqlData, [StringComparison]::OrdinalIgnoreCase) -ge 0 -and $_.CommandLine -match '--no-defaults'
    }
}
function Test-OwnedMySqlListener {
    if (!$mysqlProcess -or $mysqlProcess.HasExited) { return $false }
    $owners = @(Get-FixtureListenerPid $mysqlPort)
    if ($owners.Count -ne 1) { return $false }
    $processes = @(Get-OwnedMySqlProcesses)
    return @($processes | Where-Object { $_.ProcessId -eq $owners[0] -and ($_.ProcessId -eq $mysqlProcess.Id -or $_.ParentProcessId -eq $mysqlProcess.Id) }).Count -eq 1
}
function Get-CachedFixtureJar([string]$Group,[string]$Artifact,[string]$Version) {
    $cache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
    $dir = Join-Path $cache "$Group\$Artifact\$Version"
    $j = @(Get-ChildItem -LiteralPath $dir -Recurse -File -Filter "$Artifact-$Version.jar" -ErrorAction Stop)
    if ($j.Count -ne 1) { throw "Missing or ambiguous cached fixture dependency: $Artifact $Version" }
    return $j[0].FullName
}
try {
    if ($ManualSession -and (Test-Path -LiteralPath $manualPointer)) {
        throw 'Manual fixture pointer already exists; close its owner before starting another session.'
    }
    if ($VerifyConfiguredPort) {
        # Do not replace, stop or probe an existing deployed server.
        $occupied = @(netstat.exe -ano -p tcp | Where-Object { $_ -match '^\s*TCP\s+\S+:8443\s+\S+\s+LISTENING\s+\d+\s*$' })
        if ($occupied.Count -gt 0) { throw 'Configured port 8443 is occupied; no existing service will be touched.' }
    }
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $(if ($VerifyConfiguredPort) {8443} else {0}))
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $imapListener = Reserve-FixturePort
    $imapPort = $imapListener.LocalEndpoint.Port
    $smtpListener = Reserve-FixturePort
    $smtpPort = $smtpListener.LocalEndpoint.Port
    if ($DatabaseEngine -eq 'MySQL') {
        $mysqlListener = Reserve-FixturePort
        $mysqlPort = $mysqlListener.LocalEndpoint.Port
    }
    if ($RunPostman -or $ManualSession) {
        $oauthListener = Reserve-FixturePort
        $oauthPort = $oauthListener.LocalEndpoint.Port
    }
    $listener.Stop()
    $listener = $null
    # Close all temporary listeners before starting any child process:
    # Windows can inherit open socket handles across process creation.
    $imapListener.Stop()
    $imapListener = $null
    $smtpListener.Stop()
    $smtpListener = $null
    if ($mysqlListener) {
        $mysqlListener.Stop()
        $mysqlListener = $null
    }
    if ($oauthListener) {
        $oauthListener.Stop()
        $oauthListener = $null
    }
    $p12 = Join-Path $tempDir 'server.p12'
    $cert = Join-Path $tempDir 'server.cer'
    $pem = Join-Path $tempDir 'server.pem'
    $pass = [Guid]::NewGuid().ToString('N')
    $admin = 'E2eAdmin@' + [Guid]::NewGuid().ToString('N')
    $changedAdmin = 'E2eChanged@' + [Guid]::NewGuid().ToString('N')
    & $keytool -genkeypair -alias mailapp -keyalg RSA -keysize 2048 -validity 1 -storetype PKCS12 -keystore $p12 -storepass $pass -keypass $pass -dname 'CN=localhost' -ext 'SAN=dns:localhost,ip:127.0.0.1' -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Certificate generation failed.' }
    & $keytool -exportcert -alias mailapp -keystore $p12 -storepass $pass -file $cert
    if ($LASTEXITCODE -ne 0) { throw 'Certificate export failed.' }
    # Readiness always verifies the fixture CA, including database-only mode.
    & $keytool -exportcert -rfc -alias mailapp -keystore $p12 -storepass $pass -file $pem
    if ($LASTEXITCODE -ne 0) { throw 'PEM certificate export failed.' }
    if ($RunPostman -or $ManualPostman) {
        # Pinned Newman, installed only inside this run's unique temp directory.
        # Do not alter global packages, npm configuration or Postman profiles.
        $newmanDir = Join-Path $tempDir $(if ($ManualPostman) { 'manual-postman' } else { 'newman' })
        $null = New-Item -ItemType Directory -Path $newmanDir
        $npmConfig = Join-Path $newmanDir 'empty.npmrc'
        $npmGlobalConfig = Join-Path $newmanDir 'global.npmrc'
        Set-Content -LiteralPath $npmConfig -Value '' -Encoding ASCII
        Set-Content -LiteralPath $npmGlobalConfig -Value '' -Encoding ASCII
        Push-Location $newmanDir
        try {
            & npm.cmd install --prefix $newmanDir --cache (Join-Path $newmanDir 'cache') --userconfig $npmConfig --globalconfig $npmGlobalConfig --registry https://registry.npmjs.org --no-audit --no-fund --ignore-scripts --no-package-lock --save-exact --fetch-retries=1 --fetch-timeout=30000 newman@6.2.1
            if ($LASTEXITCODE -ne 0) { throw 'Temporary Newman installation failed.' }
        } finally { Pop-Location }
    }
    $aes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($aes) } finally { $rng.Dispose() }
    # Run this script in its own PowerShell process. All values are disposable.
    # Do not inherit application/database/JVM configuration from another environment.
    foreach ($name in [Environment]::GetEnvironmentVariables().Keys) {
        if ($name -match '^(SPRING_|MAILAPP_|MAIL_|GOOGLE_|SERVER_|DB_|MYSQL_|FLYWAY_|MANAGEMENT_|ADMIN_|GMAIL_TEST_|JWT_|AES_|KEYSTORE_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)') {
            [Environment]::SetEnvironmentVariable([string]$name, $null, 'Process')
        }
    }
    $env:MAILAPP_LOAD_DOTENV = 'false'
    $env:MAILAPP_JWT_SECRET = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $env:MAILAPP_AES_KEY = [Convert]::ToBase64String($aes)
    $env:MAILAPP_KEYSTORE_PASS = $pass
    $env:KEYSTORE_PASSWORD = $pass
    $env:SERVER_SSL_KEY_STORE_PASSWORD = $pass
    $env:JWT_SECRET = $env:MAILAPP_JWT_SECRET
    $env:AES_KEY = $env:MAILAPP_AES_KEY
    $env:ADMIN_INITIAL_PASSWORD = $admin
    $env:GOOGLE_CLIENT_ID = 'mailapp-e2e.invalid'
    $env:GOOGLE_CLIENT_SECRET = 'local-test-only'
    $env:GOOGLE_REDIRECT_URI = "https://localhost:$port/api/oauth2/callback"
    if ($ManualFaults) { $env:MAILAPP_MANUAL_FAULTS = '1' }
    if ($ManualStalls) { $env:MAILAPP_MANUAL_STALLS = '1' }
    if ($RunPostman -or $ManualSession) {
        $env:MAILAPP_OAUTH_FIXTURE = '1'
        $oauthScript = Join-Path $PSScriptRoot 'oauth-fixture.cjs'
        $oauthProcess = Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @(('"{0}"' -f $oauthScript), "$oauthPort") -WorkingDirectory $tempDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $tempDir 'oauth.out') -RedirectStandardError (Join-Path $tempDir 'oauth.err')
        $oauthReady = $false
        for ($i = 0; $i -lt 15; $i++) {
            if ($oauthProcess.HasExited) { throw 'Disposable OAuth provider exited.' }
            if (@(Get-FixtureListenerPid $oauthPort) -contains $oauthProcess.Id) {
                $oauthReady = $true
                break
            }
            Start-Sleep -Seconds 1
        }
        if (!$oauthReady) { throw 'Disposable OAuth listener ownership check failed.' }
    }
    if ($ManualSession) {
        $env:MAILAPP_MANUAL_FIXTURE = '1'
        $env:MAILAPP_MANUAL_APP_PASSWORD = 'Fixture' + [Guid]::NewGuid().ToString('N')
        $manualJars = @(
            (Get-CachedFixtureJar 'com.icegreen' 'greenmail' '2.1.2'),
            (Get-CachedFixtureJar 'org.eclipse.angus' 'jakarta.mail' '2.0.3'),
            (Get-CachedFixtureJar 'jakarta.activation' 'jakarta.activation-api' '2.1.3'),
            (Get-CachedFixtureJar 'org.eclipse.angus' 'angus-activation' '2.0.2'),
            (Get-CachedFixtureJar 'org.slf4j' 'slf4j-api' '2.0.16')
        )
        $mailArgs = @('-Xmx192m','-cp',('"{0}"' -f ($manualJars -join ';')),
            ('"{0}"' -f (Join-Path $PSScriptRoot 'ManualGreenMail.java')), "$imapPort", "$smtpPort")
        $manualMailProcess = Start-Process -FilePath $java -ArgumentList $mailArgs -WorkingDirectory $tempDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $tempDir 'manual-mail.out') -RedirectStandardError (Join-Path $tempDir 'manual-mail.err')
        $mailReady = $false
        for ($i=0; $i -lt 60; $i++) {
            if ($manualMailProcess.HasExited) { throw 'Manual GreenMail exited before readiness.' }
            if ((@(Get-FixtureListenerPid $imapPort) -contains $manualMailProcess.Id) -and
                (@(Get-FixtureListenerPid $smtpPort) -contains $manualMailProcess.Id) -and
                (Select-String -LiteralPath (Join-Path $tempDir 'manual-mail.out') -SimpleMatch 'MANUAL_GREENMAIL_READY' -ErrorAction SilentlyContinue)) {
                $mailReady = $true; break
            }
            Start-Sleep -Seconds 1
        }
        if (!$mailReady) { throw 'Manual mail fixture ownership/readiness check failed.' }
        Write-Host 'Standalone IMAPS/SMTP fixture ready.'
    }
    $env:MAILAPP_DEV_SEED = 'false'
    Remove-Item Env:SPRING_APPLICATION_JSON,Env:SPRING_CONFIG_LOCATION,Env:SPRING_CONFIG_ADDITIONAL_LOCATION,Env:SPRING_PROFILES_ACTIVE,Env:SPRING_PROFILES_INCLUDE -ErrorAction SilentlyContinue
    $p12Uri = ([Uri]$p12).AbsoluteUri
    $env:SPRING_PROFILES_ACTIVE = 'test'
    $env:SERVER_PORT = "$port"
    $env:SERVER_SSL_KEY_STORE = $p12Uri
    $env:MAILAPP_KEYSTORE_PATH = $p12Uri
    $dbUrl = 'jdbc:h2:mem:mailapp_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1'
    $dbDriver = 'org.h2.Driver'
    $dbDialect = 'org.hibernate.dialect.H2Dialect'
    $dbUser = 'sa'
    $env:SPRING_DATASOURCE_PASSWORD = ''
    $env:SPRING_FLYWAY_PASSWORD = ''
    if ($DatabaseEngine -eq 'MySQL') {
        if (!$MySqlBin) {
            $MySqlBin = Split-Path -Parent (Get-Command mysql.exe -ErrorAction Stop).Source
        }
        $mysqld = Join-Path $MySqlBin 'mysqld.exe'
        $mysql = Join-Path $MySqlBin 'mysql.exe'
        if (!(Test-Path -LiteralPath $mysqld) -or !(Test-Path -LiteralPath $mysql)) {
            throw 'MySQL server and client executables are required.'
        }
        # A new datadir, no my.ini/defaults, no Windows service, no production URL.
        $mysqlData = Join-Path $tempDir 'mysql-data'
        $mysqlTmp = Join-Path $tempDir 'mysql-tmp'
        $null = New-Item -ItemType Directory -Path $mysqlData,$mysqlTmp
        $baseDir = Split-Path -Parent $MySqlBin
        $commonMySqlArgs = @('--no-defaults', ('--basedir="{0}"' -f $baseDir),
            ('--datadir="{0}"' -f $mysqlData), ('--tmpdir="{0}"' -f $mysqlTmp))
        Write-Host 'Initializing disposable native MySQL datadir (no existing service or database).'
        # Native invocation preserves paths with spaces in Windows PowerShell.
        # Initialization is offline, before any credential is generated.
        $priorErrorPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & $mysqld --no-defaults "--basedir=$baseDir" "--datadir=$mysqlData" "--tmpdir=$mysqlTmp" --initialize-insecure --console 2>&1 | ForEach-Object { [string]$_ } | Set-Content -LiteralPath (Join-Path $tempDir 'mysql-init.err') -Encoding UTF8
            $initExit = $LASTEXITCODE
        } finally { $ErrorActionPreference = $priorErrorPreference }
        if ($initExit -ne 0 -or !(Test-Path -LiteralPath (Join-Path $mysqlData 'mysql'))) { throw "MySQL initialization failed (exit $initExit)." }
        $dbUser = 'mailapp_fixture'
        $dbPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
        $rootPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
        $initSql = Join-Path $tempDir 'mysql-init.sql'
        $initStatements = @(
            "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';",
            'CREATE DATABASE mailapp_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;',
            "CREATE USER '$dbUser'@'localhost' IDENTIFIED BY '$dbPassword';",
            "CREATE USER '$dbUser'@'127.0.0.1' IDENTIFIED BY '$dbPassword';",
            "GRANT ALL PRIVILEGES ON mailapp_db.* TO '$dbUser'@'localhost';",
            "GRANT ALL PRIVILEGES ON mailapp_db.* TO '$dbUser'@'127.0.0.1';"
        )
        if ($VerifyLegacyUpgrade) {
            $conflictPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
            $initStatements += @(
                'CREATE DATABASE mailapp_conflict CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;',
                "CREATE USER 'mailapp_conflict_fixture'@'localhost' IDENTIFIED BY '$conflictPassword';",
                "CREATE USER 'mailapp_conflict_fixture'@'127.0.0.1' IDENTIFIED BY '$conflictPassword';",
                "GRANT ALL PRIVILEGES ON mailapp_conflict.* TO 'mailapp_conflict_fixture'@'localhost';",
                "GRANT ALL PRIVILEGES ON mailapp_conflict.* TO 'mailapp_conflict_fixture'@'127.0.0.1';"
            )
        }
        if ($ManualAdminOverlap) {
            # Read-only aggregate lock-wait telemetry in this fresh instance.
            foreach ($hostName in @('localhost','127.0.0.1')) {
                $initStatements += "GRANT SELECT ON performance_schema.data_lock_waits TO '$dbUser'@'$hostName';"
                $initStatements += "GRANT SELECT ON performance_schema.data_locks TO '$dbUser'@'$hostName';"
            }
        }
        $initStatements | Set-Content -LiteralPath $initSql -Encoding ASCII
        $mysqlArgs = $commonMySqlArgs + @('--console','--bind-address=127.0.0.1',"--port=$mysqlPort",
            '--mysqlx=OFF','--skip-log-bin','--skip-name-resolve','--max-connections=24',
            '--innodb-buffer-pool-size=64M',('--performance-schema=' + $(if ($ManualAdminOverlap) {'ON'} else {'OFF'})),('--init-file="{0}"' -f $initSql))
        $mysqlProcess = Start-Process -FilePath $mysqld -ArgumentList $mysqlArgs -WorkingDirectory $tempDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $tempDir 'mysql.out') -RedirectStandardError (Join-Path $tempDir 'mysql.err')
        $mysqlReady = $false
        for ($i = 0; $i -lt 60; $i++) {
            if ($mysqlProcess.HasExited) { throw 'Disposable MySQL exited before startup.' }
            # Prove our child owns the listener before giving any client a URL.
            $owned = Test-OwnedMySqlListener
            $readyLog = Select-String -LiteralPath (Join-Path $tempDir 'mysql.err') -Pattern 'ready for connections' -SimpleMatch -ErrorAction SilentlyContinue
            if ($owned -and $readyLog) { $mysqlReady = $true; break }
            Start-Sleep -Seconds 1
        }
        if (!$mysqlReady) { throw 'Disposable MySQL failed readiness/ownership checks.' }
        Remove-Item -LiteralPath $initSql -Force
        $dbUrl = "jdbc:mysql://127.0.0.1:$mysqlPort/mailapp_db?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8"
        $dbDriver = 'com.mysql.cj.jdbc.Driver'
        $dbDialect = 'org.hibernate.dialect.MySQLDialect'
        $env:SPRING_DATASOURCE_PASSWORD = $dbPassword
        $env:SPRING_FLYWAY_PASSWORD = $dbPassword
        Write-Host 'Disposable MySQL ready on an owned loopback listener.'
    }
    if ($VerifyLegacyUpgrade -or $VerifyAuditUpgrade -or $VerifyAccountRevisionUpgrade) {
        if (!(Test-OwnedMySqlListener)) { throw 'Legacy probe lost ownership of fixture database.' }
        $env:MAILAPP_LEGACY_FIXTURE = '1'
        $env:MAILAPP_LEGACY_URL = $dbUrl
        if ($VerifyLegacyUpgrade) { $env:MAILAPP_LEGACY_CONFLICT_PASSWORD = $conflictPassword }
        $legacyClasses = Join-Path $tempDir 'legacy-classes'
        $null = New-Item -ItemType Directory -Path $legacyClasses
        Push-Location $legacyClasses
        try {
            & (Join-Path $Java21Home 'bin\jar.exe') -xf $jar.FullName
            if ($LASTEXITCODE -ne 0) { throw 'Could not extract disposable final JAR.' }
        } finally { Pop-Location }
        $legacyClasspath = (Join-Path $legacyClasses 'BOOT-INF\classes') + ';' + (Join-Path $legacyClasses 'BOOT-INF\lib\*')
        function Invoke-LegacyProbe([string]$Phase) {
            if (!(Test-OwnedMySqlListener)) { throw 'Legacy probe lost ownership of fixture database.' }
            Push-Location $tempDir
            try {
                $probeOutput = Join-Path $tempDir "legacy-$Phase.out"
                $probeError = Join-Path $tempDir "legacy-$Phase.err"
                $oldPreference = $ErrorActionPreference
                try {
                    $ErrorActionPreference = 'Continue'
                    & $java '-Xmx256m' '-cp' $legacyClasspath (Join-Path $PSScriptRoot 'LegacyUpgradeProbe.java') $Phase > $probeOutput 2> $probeError
                    $code = $LASTEXITCODE
                } finally { $ErrorActionPreference = $oldPreference }
                $safeOutput = @(Get-Content -LiteralPath $probeOutput | Where-Object { $_ -match '^LEGACY ' })
                if ($code -ne 0) {
                    # Only the probe's fixed diagnostic labels; no SQL, row or credential dumps.
                    Get-Content -LiteralPath $probeError | Where-Object { $_ -match '^Exception in thread "main" java.lang.IllegalStateException: Legacy probe failed: [A-Za-z0-9 -]+$' } | Write-Host
                    throw "Legacy probe phase $Phase failed (exit $code)."
                }
                $safeOutput
            } finally { Pop-Location }
        }
        $legacyEvidence += @(Invoke-LegacyProbe $(if ($VerifyAccountRevisionUpgrade) { 'prepare-account-revision' } elseif ($VerifyAuditUpgrade) { 'prepare-audit-upgrade' } else { 'prepare' }))
        $legacyEvidence | Write-Host
        if ($VerifyLegacyUpgrade) { Remove-Item Env:MAILAPP_LEGACY_CONFLICT_PASSWORD }
    }
    $serverArgs = @(
        '-Xmx512m', '-jar', ('"{0}"' -f $jar.FullName),
        '--spring.profiles.active=test', '--spring.config.location=classpath:/application.properties',
        '--server.address=127.0.0.1', "--server.port=$port",
        '--server.ssl.enabled=true', "--server.ssl.key-store=$p12Uri",
        "--spring.datasource.url=$dbUrl",
        "--spring.datasource.driver-class-name=$dbDriver",
        "--spring.datasource.username=$dbUser",
        '--spring.jpa.hibernate.ddl-auto=validate', "--spring.jpa.database-platform=$dbDialect",
        '--spring.flyway.enabled=true', '--spring.flyway.baseline-on-migrate=false',
        "--spring.flyway.url=$dbUrl",
        "--spring.flyway.user=$dbUser",
        '--spring.flyway.locations=classpath:db/migration',
        '--management.health.mail.enabled=false',
        '--mail.audit.receipt-dir=./data/mail-audit-receipts',
        '--mail.gmail.imap.host=127.0.0.1', "--mail.gmail.imap.port=$imapPort",
        '--mail.gmail.smtp.host=127.0.0.1', "--mail.gmail.smtp.port=$smtpPort",
        '--mail.gmail.local-test-transport=true', '--mail.gmail.pool.enabled=false',
        "--google.oauth2.token-url=http://127.0.0.1:$oauthPort/token",
        "--google.oauth2.userinfo-url=http://127.0.0.1:$oauthPort/userinfo",
        "--google.oauth2.profile-url=http://127.0.0.1:$oauthPort/profile",
        "--google.oauth2.revoke-url=http://127.0.0.1:$oauthPort/revoke"
    )
    if ($ManualFaults) {
        # Small, explicit fixture-only limit makes a real 429 observable
        # without flooding the service. Never affects ordinary E2E/production.
        $serverArgs += '--mail.gmail.download.rate-limit.max-downloads=12'
    }
    if ($ManualStalls) {
        # Bound real socket timeout/retry observations to the disposable fixture.
        $serverArgs += @('--mail.gmail.connection-timeout-ms=2000',
            '--mail.gmail.timeout-ms=2000','--mail.gmail.write-timeout-ms=2000',
            '--mail.gmail.retry.enabled=true','--mail.gmail.retry.max-attempts=2',
            '--mail.gmail.retry.wait-duration-ms=300')
    }
    if ($ManualLeases) {
        # Override the ordinary fixture's disabled pool only for this opt-in.
        $serverArgs = @($serverArgs | Where-Object { $_ -ne '--mail.gmail.pool.enabled=false' })
        $serverArgs += @('--mail.gmail.pool.enabled=true','--mail.gmail.pool.idle-seconds=10','--mail.gmail.pool.max-per-account=1')
    }
    if ($ManualAuditFaults) {
        $serverArgs += @('--mail.audit.replay-delay-ms=2000','--mail.audit.retry-base-ms=1000',"--mail.audit.local-capacity=$ManualAuditCapacity")
    }
    $process = Start-Process -FilePath $java -ArgumentList $serverArgs -WorkingDirectory $tempDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $tempDir 'server.stdout.log') -RedirectStandardError (Join-Path $tempDir 'server.stderr.log')
    $url = "https://localhost:$port"
    $ready = $false
    for ($i = 0; $i -lt 90; $i++) {
        if ($process.HasExited) { throw 'Disposable server exited before startup.' }
        if (@(Get-FixtureListenerPid $port) -contains $process.Id) {
            & curl.exe --noproxy '*' --cacert $pem -f -s --max-time 2 "$url/actuator/health" -o NUL
            if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'Disposable server health check timed out.' }
    $env:MAILAPP_E2E = '1'
    $env:MAILAPP_E2E_DISPOSABLE = '1'
    $env:MAILAPP_E2E_URL = $url
    $env:MAILAPP_E2E_CERT = $cert
    $env:MAILAPP_E2E_ADMIN_PASSWORD = $admin
    $env:MAILAPP_E2E_CHANGED_ADMIN_PASSWORD = $changedAdmin
    $env:MAILAPP_E2E_IMAPS_PORT = "$imapPort"
    $env:MAILAPP_E2E_SMTP_PORT = "$smtpPort"
    $env:MAILAPP_E2E_DATABASE_ENGINE = $DatabaseEngine
    Write-Host "Disposable HTTPS server ready ($DatabaseEngine)."
    if ($VerifyConfiguredPort) {
        if ($port -ne 8443 -or !(@(Get-FixtureListenerPid $port) -contains $process.Id)) { throw 'Configured-port listener is not owned.' }
        & curl.exe --noproxy '*' --cacert $pem -f -s --max-time 5 "$url/actuator/health" -o NUL
        if ($LASTEXITCODE -ne 0) { throw 'Trusted TLS on configured port failed.' }
        Write-Host 'LEGACY configured port 8443, owned loopback HTTPS and generated SAN certificate: PASS'
        $legacyEvidence += 'LEGACY configured port 8443, owned loopback HTTPS and generated SAN certificate: PASS'
    }
    if ($VerifyLegacyUpgrade -or $VerifyAuditUpgrade -or $VerifyAccountRevisionUpgrade) {
        $legacyEvidence += @(Invoke-LegacyProbe $(if ($VerifyAccountRevisionUpgrade) { 'verify-account-revision' } elseif ($VerifyAuditUpgrade) { 'verify-audit-upgrade' } else { 'verify-startup' }))
        $legacyEvidence[-1] | Write-Host
    }
    if ($VerifyLegacyUpgrade -or $VerifyConfiguredPort -or $VerifyAuditUpgrade -or $VerifyAccountRevisionUpgrade) {
        $evidenceDir = Join-Path $serverDir 'build\acceptance'
        $null = New-Item -ItemType Directory -Path $evidenceDir -Force
        @("Verified at $([DateTime]::UtcNow.ToString('o')); final JAR SHA256: $((Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash)"; $legacyEvidence) |
            Set-Content -LiteralPath (Join-Path $evidenceDir $(if ($VerifyAccountRevisionUpgrade) { 'account-revision-upgrade-summary.txt' } elseif ($VerifyAuditUpgrade) { 'audit-upgrade-summary.txt' } else { 'legacy-upgrade-summary.txt' })) -Encoding UTF8
    }
    if ($VerifyPublicConfigFault) {
        # Public GET only: temporarily hide the settings table in the freshly
        # initialized, owned MySQL instance. Restore it in finally; no rows,
        # privileges, login/session data or production config are changed.
        function Invoke-PublicConfigProbe {
            if (!(@(Get-FixtureListenerPid $port) -contains $process.Id)) { throw 'Lost owned HTTPS listener.' }
            $responseFile = Join-Path $tempDir 'public-config-probe.json'
            $status = & curl.exe --noproxy '*' --cacert $pem --silent --show-error --max-time 20 --output $responseFile --write-out '%{http_code}' "$url/api/public/config"
            if ($LASTEXITCODE -ne 0) { throw 'Public config probe transport failed.' }
            @{ status = [int]$status; body = (Get-Content -LiteralPath $responseFile -Raw -Encoding UTF8 | ConvertFrom-Json) }
        }
        function Invoke-OwnedFaultSql([string]$Sql) {
            if (!(Test-OwnedMySqlListener)) { throw 'Lost owned MySQL listener.' }
            $env:MYSQL_PWD = $dbPassword
            try {
                $result = @(& $mysql --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$mysqlPort" "--user=$dbUser" --database=mailapp_db --batch --skip-column-names --execute=$Sql)
                if ($LASTEXITCODE -ne 0) { throw 'Disposable public fault SQL failed.' }
                $result
            } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        }
        $before = Invoke-PublicConfigProbe
        if ($before.status -ne 200) { throw 'Public config baseline failed.' }
        $tableCount = @(Invoke-OwnedFaultSql "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='mailapp_db' AND TABLE_NAME IN ('system_settings','fixture_unavailable_system_settings');")
        if ($tableCount.Count -ne 1 -or $tableCount[0] -ne '1') { throw 'Unexpected disposable table layout.' }
        $hidden = $false
        try {
            Invoke-OwnedFaultSql 'RENAME TABLE system_settings TO fixture_unavailable_system_settings;' | Out-Null
            $hidden = $true
            Start-Sleep -Seconds 4 # expire the actual settings cache
            $fault = Invoke-PublicConfigProbe
            $fields = @($fault.body.PSObject.Properties.Name | Sort-Object) -join ','
            if ($fault.status -ne 500 -or $fault.body.status -ne 500 -or $fault.body.code -ne 'INTERNAL_ERROR' -or $fault.body.errorCode -ne 'INTERNAL_ERROR' -or $fault.body.path -ne '/api/public/config' -or $fields -ne 'code,error,errorCode,message,path,status,timestamp' -or [string]::IsNullOrWhiteSpace($fault.body.message) -or $fault.body.message -match '(?i)exception|jdbc|sql|system_settings|fixture_unavailable|password|token|secret|stack') { throw 'Unexpected public fault response (body withheld).' }
            Write-Host 'PUBLIC FAULT 500 INTERNAL_ERROR, expected seven fields, no internal table/exception details: PASS'
        } finally {
            if ($hidden) { Invoke-OwnedFaultSql 'RENAME TABLE fixture_unavailable_system_settings TO system_settings;' | Out-Null }
        }
        $after = Invoke-PublicConfigProbe
        if ($after.status -ne 200 -or ($before.body | ConvertTo-Json -Compress) -ne ($after.body | ConvertTo-Json -Compress)) { throw 'Public config recovery did not preserve the baseline.' }
        $evidenceDir = Join-Path $serverDir 'build\acceptance'
        $null = New-Item -ItemType Directory -Path $evidenceDir -Force
        @("Verified UTC: $([DateTime]::UtcNow.ToString('o')); JAR SHA256: $((Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash)"; 'P001 public config 200'; 'P002 owned disposable table hidden, cache expired; 500 JSON INTERNAL_ERROR; code/errorCode/path/status and seven fields match; no internal table or exception details'; 'P003 table restored; public config 200 and identical JSON values') | Set-Content -LiteralPath (Join-Path $evidenceDir 'public-config-fault-summary.txt') -Encoding UTF8
        Write-Host 'PUBLIC FAULT restored table and identical public config: PASS'
    }
    if ($ManualSession) {
        $configDir = Join-Path $tempDir 'client-config'
        $downloadDir = Join-Path $tempDir 'downloads'
        $null = New-Item -ItemType Directory -Path $configDir,$downloadDir
        @(
            "server.url=$url",
            ('cert.path=' + $cert.Replace('\','/')),
            ('download.dir=' + $downloadDir.Replace('\','/')),
            'autoclean.enabled=false',
            'autoclean.dryrun=true'
        ) | Set-Content -LiteralPath (Join-Path $configDir 'config.properties') -Encoding ASCII
        $users = @('ui_manual','ui_other','http_manual' | ForEach-Object {
            @{username=$_;email="$_@test.local";password=('FixtureLogin@'+[Guid]::NewGuid().ToString('N'));
              appPassword=$env:MAILAPP_MANUAL_APP_PASSWORD;linkedId=$null}
        })
        $fixture = @{
            disposable=$true;root=$tempDir;baseUrl=$url;cert=$cert;pem=$pem;
            adminUsername='admin';adminInitialPassword=$admin;adminPassword=$changedAdmin;
            users=$users;clientConfigDir=$configDir;downloadDir=$downloadDir;
            imapsPort=$imapPort;smtpPort=$smtpPort;stopFile=$manualStop;serverPid=$process.Id;
            databaseEngine=$DatabaseEngine;oauthPort=$oauthPort;manualFaults=[bool]$ManualFaults;manualStalls=[bool]$ManualStalls;
            manualAuditFaults=[bool]$ManualAuditFaults;manualLeases=[bool]$ManualLeases;manualAdminOverlap=[bool]$ManualAdminOverlap
        }
        $fixture | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manualManifest -Encoding UTF8
        & node.exe (Join-Path $PSScriptRoot 'manual-http.cjs') seed $manualManifest
        if ($LASTEXITCODE -ne 0) { throw 'Manual fixture bootstrap failed.' }
        $null = New-Item -ItemType Directory -Path (Split-Path -Parent $manualPointer) -Force
        @{root=$tempDir;manifest=$manualManifest;pid=$PID} | ConvertTo-Json | Set-Content -LiteralPath $manualPointer -Encoding UTF8
        Write-Host "Manual session ready; bounded to $ManualSeconds seconds. Pointer: $manualPointer"
        $deadline = [DateTime]::UtcNow.AddSeconds($ManualSeconds)
        $auditCommand = Join-Path $tempDir 'audit-fixture-command.txt'
        $auditAck = Join-Path $tempDir 'audit-fixture-ack.txt'
        $auditFaultActive = $false
        $intentFaultActive = $false
        $outcomeFaultActive = $false
        $drainFaultActive = $false
        $roleDelayActive = $false
        $restartedForAudit = $false
        # Fixed SQL only, scoped to the owned disposable instance. This
        # command channel cannot execute caller-supplied SQL.
        function Invoke-AuditFixtureSql([string]$Sql) {
            if (!$ManualAuditFaults -or !(Test-OwnedMySqlListener)) { throw 'Lost owned audit fixture.' }
            $env:MYSQL_PWD = $dbPassword
            try {
                $r = @(& $mysql --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$mysqlPort" "--user=$dbUser" --database=mailapp_db --batch --skip-column-names --execute=$Sql)
                if ($LASTEXITCODE -ne 0) { throw 'Audit fixture SQL failed.' }
                $r
            } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        }
        try {
        while ([DateTime]::UtcNow -lt $deadline -and !(Test-Path -LiteralPath $manualStop)) {
            if ($process.HasExited -or $manualMailProcess.HasExited -or ($DatabaseEngine -eq 'MySQL' -and $mysqlProcess.HasExited)) {
                throw 'Manual fixture child stopped unexpectedly.'
            }
            if ($ManualAuditFaults -and (Test-Path -LiteralPath $auditCommand -PathType Leaf)) {
                $file = Get-Item -LiteralPath $auditCommand -Force
                if ($file.Length -gt 64 -or ($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -or (Test-Path -LiteralPath $auditAck)) { throw 'Invalid audit fixture control.' }
                $action = (Get-Content -LiteralPath $auditCommand -Raw -Encoding ASCII).Trim()
                Remove-Item -LiteralPath $auditCommand
                switch ($action) {
                    'delay-role' {
                        if (!$ManualAdminOverlap -or $roleDelayActive) { throw 'Explicit overlap fixture required.' }
                        # Only two sacrificial fixture users, never the bootstrap admin.
                        Invoke-AuditFixtureSql "CREATE TRIGGER fixture_delay_role BEFORE UPDATE ON users FOR EACH ROW DO IF(OLD.username IN ('ui_other','http_manual') AND OLD.role='ADMIN' AND NEW.role='USER', SLEEP(12), 0);" | Out-Null
                        $roleDelayActive = $true
                        $reply = 'delay-role OK'
                    }
                    'role-overlap-status' {
                        if (!$ManualAdminOverlap -or !$roleDelayActive) { throw 'Overlap delay is not active.' }
                        $counts = @(Invoke-AuditFixtureSql "SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID AND l.ENGINE=w.ENGINE WHERE l.OBJECT_SCHEMA='mailapp_db' AND l.OBJECT_NAME='users';")
                        if ($counts.Count -ne 1 -or $counts[0] -notmatch '^\d+$') { throw 'Unexpected lock-wait count.' }
                        $reply = "role-overlap-status OK waits=$($counts[0])"
                    }
                    'restore-role' {
                        if (!$roleDelayActive) { throw 'Role delay not active.' }
                        Invoke-AuditFixtureSql 'DROP TRIGGER fixture_delay_role;' | Out-Null
                        $roleDelayActive = $false
                        $reply = 'restore-role OK'
                    }
                    'reject-audit' {
                        if ($auditFaultActive) { throw 'Audit fault already active.' }
                        Invoke-AuditFixtureSql "CREATE TRIGGER fixture_reject_audit_insert BEFORE INSERT ON audit_logs FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fixture audit insert rejected';" | Out-Null
                        $auditFaultActive = $true
                        $reply = 'reject-audit OK'
                    }
                    'restore-audit' {
                        if (!$auditFaultActive) { throw 'Audit fault not active.' }
                        Invoke-AuditFixtureSql 'DROP TRIGGER fixture_reject_audit_insert;' | Out-Null
                        $auditFaultActive = $false
                        $reply = 'restore-audit OK'
                    }
                    'audit-status' {
                        $counts = @(Invoke-AuditFixtureSql "SELECT COUNT(*) FROM audit_logs; SELECT setting_value FROM system_settings WHERE setting_key='MAIL_PAGE_SIZE';")
                        if ($counts.Count -ne 2 -or $counts[0] -notmatch '^\d+$' -or $counts[1] -notmatch '^\d+$') { throw 'Unexpected audit aggregate.' }
                        $reply = "audit-status OK rows=$($counts[0]) mailPageSize=$($counts[1]) rejecting=$auditFaultActive"
                    }
                    'reject-intent' {
                        if ($intentFaultActive) { throw 'Intent fault already active.' }
                        Invoke-AuditFixtureSql "CREATE TRIGGER fixture_reject_intent BEFORE INSERT ON mail_audit_outbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fixture intent insert rejected';" | Out-Null
                        $intentFaultActive = $true
                        $reply = 'reject-intent OK'
                    }
                    'restore-intent' {
                        if (!$intentFaultActive) { throw 'Intent fault not active.' }
                        Invoke-AuditFixtureSql 'DROP TRIGGER fixture_reject_intent;' | Out-Null
                        $intentFaultActive = $false
                        $reply = 'restore-intent OK'
                    }
                    'reject-outcome' {
                        if ($outcomeFaultActive) { throw 'Outcome fault already active.' }
                        Invoke-AuditFixtureSql "CREATE TRIGGER fixture_reject_outcome BEFORE UPDATE ON mail_audit_outbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fixture outcome update rejected';" | Out-Null
                        $outcomeFaultActive = $true
                        $reply = 'reject-outcome OK'
                    }
                    'restore-outcome' {
                        if (!$outcomeFaultActive) { throw 'Outcome fault not active.' }
                        Invoke-AuditFixtureSql 'DROP TRIGGER fixture_reject_outcome;' | Out-Null
                        $outcomeFaultActive = $false
                        $reply = 'restore-outcome OK'
                    }
                    'reject-drain' {
                        if ($drainFaultActive) { throw 'Drain fault already active.' }
                        Invoke-AuditFixtureSql "CREATE TRIGGER fixture_reject_drain BEFORE DELETE ON mail_audit_outbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Fixture outbox delete rejected';" | Out-Null
                        $drainFaultActive = $true
                        $reply = 'reject-drain OK'
                    }
                    'restore-drain' {
                        if (!$drainFaultActive) { throw 'Drain fault not active.' }
                        Invoke-AuditFixtureSql 'DROP TRIGGER fixture_reject_drain;' | Out-Null
                        $drainFaultActive = $false
                        $reply = 'restore-drain OK'
                    }
                    'outbox-status' {
                        $counts = @(Invoke-AuditFixtureSql "SELECT COUNT(*) FROM mail_audit_outbox WHERE outcome='PREPARED'; SELECT COUNT(*) FROM mail_audit_outbox WHERE outcome='CONFIRMED'; SELECT COUNT(*) FROM mail_audit_outbox WHERE outcome='UNCERTAIN'; SELECT COUNT(*) FROM audit_logs WHERE action='SEND_MAIL'; SELECT COUNT(*) FROM audit_logs WHERE action='MAIL_DELETE'; SELECT COUNT(*) FROM audit_logs WHERE action='MAIL_FLAG_CHANGE'; SELECT COUNT(*) FROM audit_logs WHERE action='MAIL_OPERATION_UNCERTAIN';")
                        if ($counts.Count -ne 7 -or @($counts | Where-Object { $_ -notmatch '^\d+$' }).Count) { throw 'Unexpected outbox aggregate.' }
                        $reply = "outbox-status OK prepared=$($counts[0]) confirmed=$($counts[1]) uncertain=$($counts[2]) sent=$($counts[3]) deleted=$($counts[4]) flags=$($counts[5]) ambiguous=$($counts[6])"
                    }
                    'restart-server' {
                        if ($restartedForAudit -or !$auditFaultActive -or $process.HasExited -or !(@(Get-FixtureListenerPid $port) -contains $process.Id) -or !(Test-OwnedMySqlListener)) { throw 'Restart requires owned server and active audit fault.' }
                        # Deliberate crash of only the fixture Server; preserve
                        # its DB and mail listeners, config and existing sessions.
                        Stop-Process -Id $process.Id
                        if (!$process.WaitForExit(10000)) { throw 'Owned server did not stop.' }
                        foreach ($suffix in @('stdout','stderr')) {
                            $source = Join-Path $tempDir "server.$suffix.log"
                            $target = Join-Path $tempDir "server-before-restart.$suffix.log"
                            if (Test-Path -LiteralPath $target) { throw 'Unexpected restart log destination.' }
                            Move-Item -LiteralPath $source -Destination $target
                        }
                        $process = Start-Process -FilePath $java -ArgumentList $serverArgs -WorkingDirectory $tempDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $tempDir 'server.stdout.log') -RedirectStandardError (Join-Path $tempDir 'server.stderr.log')
                        $ready = $false
                        for ($i = 0; $i -lt 60; $i++) {
                            if ($process.HasExited) { throw 'Restarted fixture exited.' }
                            if (@(Get-FixtureListenerPid $port) -contains $process.Id) {
                                & curl.exe --noproxy '*' --cacert $pem -f -s --max-time 2 "$url/actuator/health" -o NUL
                                if ($LASTEXITCODE -eq 0) { $ready = $true; break }
                            }
                            Start-Sleep -Seconds 1
                        }
                        if (!$ready) { throw 'Restarted fixture health unavailable.' }
                        $restartedForAudit = $true
                        $reply = 'restart-server OK'
                    }
                    'privacy-status' {
                        # Only this run's fresh server stdout/stderr, fixed public
                        # synthetic canaries, numeric aggregates; never return
                        # a log line, search a credential or inspect private files.
                        if ($process.HasExited -or !(@(Get-FixtureListenerPid $port) -contains $process.Id)) { throw 'Lost owned server fixture.' }
                        $patterns = @('B-audit-store-fault-marker','B-audit-body-canary-7de3','audit-privacy-canary-B-7de3','Fixture audit insert rejected','AUDIT_WRITE_FAILED','[AUDIT] Action=SEND_MAIL')
                        $hits = @(0,0,0,0,0,0)
                        foreach ($name in @('server.stdout.log','server.stderr.log')) {
                            $logPath = Join-Path $tempDir $name
                            $item = Get-Item -LiteralPath $logPath
                            if ($item.Length -gt 16777216 -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Invalid disposable log.' }
                            $stream = [IO.File]::Open($logPath,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
                            $reader = [IO.StreamReader]::new($stream,[Text.Encoding]::UTF8)
                            try {
                                while ($null -ne ($line = $reader.ReadLine())) {
                                    for ($k=0; $k -lt $patterns.Count; $k++) {
                                        if ($line.Contains($patterns[$k])) { $hits[$k]++ }
                                    }
                                }
                            } finally { $reader.Dispose(); $stream.Dispose() }
                        }
                        $reply = "privacy-status OK subject=$($hits[0]) body=$($hits[1]) path=$($hits[2]) sql=$($hits[3]) auditFailures=$($hits[4]) sendAuditSuccess=$($hits[5])"
                    }
                    'recovery-privacy-status' {
                        if ($process.HasExited -or !(@(Get-FixtureListenerPid $port) -contains $process.Id)) { throw 'Lost owned server fixture.' }
                        # Fixed synthetic mail subject/body/recipient/file/path and
                        # failure canaries. Never return any raw log or DB row.
                        $patterns = @('C-audit-subject','C-audit-body','c-audit-recipient@test.local','C-audit-file.txt','C-audit-path','Fixture intent insert rejected','Fixture outcome update rejected','Fixture outbox delete rejected','Fixture audit insert rejected')
                        $hits = @(0,0,0,0,0,0,0,0,0)
                        $names = @('server.stdout.log','server.stderr.log')
                        if ($restartedForAudit) { $names += @('server-before-restart.stdout.log','server-before-restart.stderr.log') }
                        foreach ($name in $names) {
                            $logPath = Join-Path $tempDir $name
                            $item = Get-Item -LiteralPath $logPath
                            if ($item.Length -gt 16777216 -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Invalid disposable log.' }
                            $stream = [IO.File]::Open($logPath,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
                            $reader = [IO.StreamReader]::new($stream,[Text.Encoding]::UTF8)
                            try {
                                while ($null -ne ($line = $reader.ReadLine())) {
                                    for ($k=0; $k -lt $patterns.Count; $k++) {
                                        if ($line.Contains($patterns[$k])) { $hits[$k]++ }
                                    }
                                }
                            } finally { $reader.Dispose(); $stream.Dispose() }
                        }
                        $counts = @(Invoke-AuditFixtureSql "SELECT COUNT(*) FROM mail_audit_outbox WHERE detail LIKE '%C-audit-subject%' OR detail LIKE '%C-audit-body%' OR detail LIKE '%c-audit-recipient@test.local%' OR detail LIKE '%C-audit-file.txt%'; SELECT COUNT(*) FROM audit_logs WHERE detail LIKE '%C-audit-subject%' OR detail LIKE '%C-audit-body%' OR detail LIKE '%c-audit-recipient@test.local%' OR detail LIKE '%C-audit-file.txt%';")
                        if ($counts.Count -ne 2 -or @($counts | Where-Object {$_ -notmatch '^\d+$'}).Count) { throw 'Unexpected recovery privacy aggregate.' }
                        $reply = "recovery-privacy-status OK subject=$($hits[0]) body=$($hits[1]) recipient=$($hits[2]) filename=$($hits[3]) path=$($hits[4]) sql=$($hits[5]+$hits[6]+$hits[7]+$hits[8]) outbox=$($counts[0]) audit=$($counts[1])"
                    }
                    'provider-error-privacy' {
                        if (!$ManualFaults -or $process.HasExited -or !(@(Get-FixtureListenerPid $port) -contains $process.Id)) { throw 'Owned provider fault fixture required.' }
                        # Return counts of fixed, non-secret synthetic markers only.
                        $patterns = @('F-provider-error-detail-canary','F-ambiguous-subject','F-ambiguous-body','F-auth-denied-subject','F-auth-denied-body','Synthetic provider post-delivery fault')
                        $hits = 0
                        foreach ($name in @('server.stdout.log','server.stderr.log')) {
                            $logPath = Join-Path $tempDir $name
                            $item = Get-Item -LiteralPath $logPath
                            if ($item.Length -gt 16777216 -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Invalid disposable log.' }
                            $stream = [IO.File]::Open($logPath,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
                            $reader = [IO.StreamReader]::new($stream,[Text.Encoding]::UTF8)
                            try {
                                while ($null -ne ($line = $reader.ReadLine())) {
                                    foreach ($marker in $patterns) { if ($line.Contains($marker)) { $hits++ } }
                                }
                            } finally { $reader.Dispose(); $stream.Dispose() }
                        }
                        $counts = @(Invoke-AuditFixtureSql "SELECT COUNT(*) FROM mail_audit_outbox WHERE detail LIKE '%F-ambiguous-%' OR detail LIKE '%F-auth-denied-%' OR detail LIKE '%F-provider-error-detail-canary%'; SELECT COUNT(*) FROM audit_logs WHERE detail LIKE '%F-ambiguous-%' OR detail LIKE '%F-auth-denied-%' OR detail LIKE '%F-provider-error-detail-canary%';")
                        if ($counts.Count -ne 2 -or @($counts | Where-Object {$_ -notmatch '^\d+$'}).Count) { throw 'Unexpected provider privacy aggregate.' }
                        $reply = "provider-error-privacy OK log=$hits outbox=$($counts[0]) audit=$($counts[1])"
                    }
                    default { throw 'Unknown audit fixture action.' }
                }
                [IO.File]::WriteAllText($auditAck, $reply, [Text.Encoding]::ASCII)
            }
            Start-Sleep -Milliseconds 500
        }
        } finally {
            if ($roleDelayActive) { Invoke-AuditFixtureSql 'DROP TRIGGER fixture_delay_role;' | Out-Null }
            if ($auditFaultActive) { Invoke-AuditFixtureSql 'DROP TRIGGER fixture_reject_audit_insert;' | Out-Null }
            if ($intentFaultActive) { Invoke-AuditFixtureSql 'DROP TRIGGER fixture_reject_intent;' | Out-Null }
            if ($outcomeFaultActive) { Invoke-AuditFixtureSql 'DROP TRIGGER fixture_reject_outcome;' | Out-Null }
            if ($drainFaultActive) { Invoke-AuditFixtureSql 'DROP TRIGGER fixture_reject_drain;' | Out-Null }
        }
        # Only redacted agent-observed HTTP records are copied to build output.
        $observed = Join-Path $tempDir 'manual-http-observed.ndjson'
        if (Test-Path -LiteralPath $observed) {
            Copy-Item -LiteralPath $observed -Destination (Join-Path (Split-Path -Parent $manualPointer) 'manual-http-observed.ndjson') -Force
        }
        Write-Host 'Manual session stopped; cleaning isolated data and processes.'
    } elseif (!$VerifyDatabaseOnly) {
        Write-Host 'Running Client acceptance.'
        Push-Location $clientDir
        try {
            $testArgs = @('test','--rerun-tasks','--no-daemon','--max-workers=1','--console=plain')
            foreach ($selector in $ClientTests) { $testArgs += @('--tests', $selector) }
            & .\gradlew.bat @testArgs
            if ($LASTEXITCODE -ne 0) { throw 'Client tests failed.' }
        } finally { Pop-Location }
        if ($RunPostman) {
            if (!(@(Get-FixtureListenerPid $port) -contains $process.Id)) { throw 'Lost ownership of disposable HTTPS listener.' }
            Write-Host 'Running Postman collection against the disposable server.'
            & node.exe (Join-Path $serverDir 'scripts\run-disposable-postman.cjs') (Join-Path $newmanDir 'node_modules\newman') (Join-Path $serverDir 'docs\http\acceptance.postman_collection.json') $pem (Join-Path $serverDir 'build\acceptance\postman-summary.json')
            if ($LASTEXITCODE -ne 0) { throw 'Newman acceptance failed.' }
            if (!(@(Get-FixtureListenerPid $oauthPort) -contains $oauthProcess.Id)) { throw 'Lost OAuth fixture listener ownership.' }
            $oauthStats = (& curl.exe --noproxy '*' -f -s --max-time 5 "http://127.0.0.1:$oauthPort/status") | ConvertFrom-Json
            if ($LASTEXITCODE -ne 0 -or $oauthStats.exchanges -ne 4 -or $oauthStats.userinfo -ne 4 -or $oauthStats.profiles -ne 4 -or $oauthStats.revokes -ne 2) {
                throw 'Unexpected local OAuth exchange/profile/revoke counts.'
            }
            Write-Host ('Verified local provider PKCE, UserInfo, Gmail profile and revoke: ' + ($oauthStats | ConvertTo-Json -Compress))
            $oauthStats | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $serverDir 'build\acceptance\oauth-summary.json') -Encoding UTF8
        }
    } else { Write-Host 'Database-only mode: Client and Newman not run.' }
    if ($DatabaseEngine -eq 'MySQL') {
        # Read-only verification, still scoped to the owned fresh instance.
        if (!(Test-OwnedMySqlListener)) {
            throw 'Lost ownership of disposable MySQL listener.'
        }
        $env:MYSQL_PWD = $dbPassword
        try {
            $sql = "SELECT VERSION(); SELECT DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='mailapp_db'; SELECT version,success FROM flyway_schema_history ORDER BY installed_rank; SELECT COUNT(*) FROM system_settings; SELECT COUNT(*) FROM users WHERE role='ADMIN'; SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='mailapp_db' AND TABLE_NAME IN ('users','gmail_accounts','system_settings','audit_logs','mail_audit_outbox');"
            $verified = @(& $mysql --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$mysqlPort" "--user=$dbUser" --database=mailapp_db --batch --skip-column-names --execute=$sql)
            if ($LASTEXITCODE -ne 0) { throw 'Disposable MySQL metadata verification failed.' }
            $allMigrations = @($verified | Where-Object { $_ -match '^\d+\t[01]$' })
            $expectedMigrations = @(1..9 | ForEach-Object { "$_`t1" })
            if ($verified -notcontains "utf8mb4`tutf8mb4_unicode_ci" -or ($allMigrations -join ',') -ne ($expectedMigrations -join ',') -or $verified[-3] -ne '9' -or [int]$verified[-2] -lt 1 -or $verified[-1] -ne '5') {
                throw 'Unexpected disposable MySQL schema/seed metadata.'
            }
            Write-Host 'Verified MySQL version, schema charset/collation, successful migrations, nine setting rows, admin, four domain tables and mail audit outbox:'
            $verified | ForEach-Object { Write-Host $_ }
            # Metadata and aggregate-only evidence, not application records or secrets.
            $metadataSql = @'
SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mailapp_db' AND TABLE_NAME IN ('users','gmail_accounts','system_settings','audit_logs','mail_audit_outbox') ORDER BY TABLE_NAME,ORDINAL_POSITION;
SELECT TABLE_NAME,CONSTRAINT_NAME,CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='mailapp_db' AND TABLE_NAME IN ('users','gmail_accounts','system_settings','audit_logs','mail_audit_outbox') ORDER BY TABLE_NAME,CONSTRAINT_NAME;
SELECT 'admin_hash_shape_count',COUNT(*) FROM users WHERE role='ADMIN' AND CHAR_LENGTH(password_hash)=60 AND LEFT(password_hash,2)='$2';
SELECT 'linked_app_encrypted_count',COUNT(*) FROM gmail_accounts WHERE app_password_encrypted IS NOT NULL AND CHAR_LENGTH(app_password_encrypted)>0;
'@
            $metadata = @(& $mysql --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$mysqlPort" "--user=$dbUser" --database=mailapp_db --batch --skip-column-names --execute=$metadataSql)
            if ($LASTEXITCODE -ne 0) { throw 'Disposable MySQL aggregate/schema inspection failed.' }
            $evidenceDir = Join-Path $serverDir 'build\acceptance'
            $null = New-Item -ItemType Directory -Path $evidenceDir -Force
            $name = if ($ManualSession) { 'manual-mysql-metadata.tsv' } else { 'mysql-metadata.tsv' }
            @('Version/charset/migrations/settings/admin/tables:'; $verified; 'Column/constraint metadata and aggregate hash/encryption shape counts:'; $metadata) | Set-Content -LiteralPath (Join-Path $evidenceDir $name) -Encoding UTF8
            Write-Host 'Saved schema metadata and aggregate-only hash/encryption shape counts.'
        } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    }
    $failed = $false
} finally {
    if ($listener) { $listener.Stop() }
    if ($imapListener) { $imapListener.Stop() }
    if ($smtpListener) { $smtpListener.Stop() }
    if ($mysqlListener) { $mysqlListener.Stop() }
    if ($oauthListener) { $oauthListener.Stop() }
    if ($ManualSession -and (Test-Path -LiteralPath $manualPointer)) {
        $pointer = Get-Content -LiteralPath $manualPointer -Raw | ConvertFrom-Json
        if ($pointer.root -eq $tempDir -and $pointer.pid -eq $PID) {
            Remove-Item -LiteralPath $manualPointer -Force
        }
    }
    if ($process -and !$process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue; $process.WaitForExit() }
    if ($manualMailProcess -and !$manualMailProcess.HasExited) { Stop-Process -Id $manualMailProcess.Id -ErrorAction SilentlyContinue; $manualMailProcess.WaitForExit() }
    if ($oauthProcess -and !$oauthProcess.HasExited) { Stop-Process -Id $oauthProcess.Id -ErrorAction SilentlyContinue; $oauthProcess.WaitForExit() }
    if ($mysqlProcess) {
        # Stop the monitor before its child to prevent respawn; never stop any
        # process that isn't using this run's unique disposable datadir.
        foreach ($owned in @(Get-OwnedMySqlProcesses | Sort-Object @{Expression={if ($_.ProcessId -eq $mysqlProcess.Id) { 0 } else { 1 }}})) {
            $stillOwned = @(Get-OwnedMySqlProcesses | Where-Object ProcessId -eq $owned.ProcessId)
            if ($stillOwned.Count -eq 1) {
                $running = Get-Process -Id $owned.ProcessId -ErrorAction SilentlyContinue
                if ($running) { Stop-Process -Id $running.Id -ErrorAction SilentlyContinue; $null = $running.WaitForExit(10000) }
            }
        }
        if (@(Get-OwnedMySqlProcesses).Count -gt 0) { throw 'Disposable MySQL did not stop; preserving its isolated directory.' }
    }
    if ($failed) {
        foreach ($log in @('server.stdout.log','server.stderr.log')) {
            $file = Join-Path $tempDir $log
            if (Test-Path -LiteralPath $file) {
                Select-String -LiteralPath $file -Pattern 'Flyway|Migrating schema|HikariPool.*Added connection|Schema-validation|APPLICATION FAILED' | Select-Object -First 20
                # Avoid exposing generated development passwords, request bodies or tokens.
                Select-String -LiteralPath $file -Pattern 'Schema-validation|APPLICATION FAILED|Caused by:|Exception:| ERROR ' |
                    Where-Object { $_.Line -notmatch '(?i)password|token|secret|credential|authorization|api_key' } |
                    Select-Object -Last 12
            }
        }
        # Do not dump MySQL initialization SQL or credential-bearing statements.
        foreach ($log in @('mysql-init.err','mysql.err')) {
            $file = Join-Path $tempDir $log
            if (Test-Path -LiteralPath $file) {
                Select-String -LiteralPath $file -Pattern '\[ERROR\]|ready for connections' | ForEach-Object {
                    if ($_.Line -notmatch 'PASSWORD|IDENTIFIED|ALTER USER|CREATE USER') { Write-Host $_.Line }
                } | Select-Object -Last 15
            }
        }
    }
    # Only delete the unique temp directory allocated by this script.
    if (Test-Path -LiteralPath $tempDir) {
        $resolvedTemp = (Resolve-Path -LiteralPath $tempDir).Path
        $expectedParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
        if ((Split-Path -Parent $resolvedTemp) -ne $expectedParent -or (Split-Path -Leaf $resolvedTemp) -notmatch '^mailapp-e2e-[0-9a-f]{32}$') {
            throw 'Refusing cleanup outside the allocated fixture directory.'
        }
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
    }
}
