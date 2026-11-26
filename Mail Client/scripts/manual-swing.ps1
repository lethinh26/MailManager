# Single-action operator console for the disposable manual fixture.
[CmdletBinding()]
param(
    [ValidateSet('start','inspect','click','value','secret','select','enter','pointer','key','focus','snapshot','close','captureSecret')]
    [string]$Mode = 'inspect',
    [ValidatePattern('^manual-ui-[a-zA-Z0-9_-]{1,48}$')][string]$Channel = 'manual-ui-user',
    [int]$Target = -1,
    [string]$Value,
    [string]$Name,
    [int]$Index = -1,
    [int]$X = -1,
    [int]$Y = -1,
    [int]$Row = -1,
    [int]$Code = -1,
    [switch]$Right,
    [switch]$RightClick,
    [switch]$Double,
    [switch]$Ctrl,
    [switch]$Shift,
    [string]$Java21Home
)
$ErrorActionPreference = 'Stop'
$client = Split-Path -Parent $PSScriptRoot
$repo = Split-Path -Parent $client
$pointer = Join-Path $repo 'Mail Server\build\acceptance\manual-session.json'
if (!(Test-Path -LiteralPath $pointer -PathType Leaf)) { throw 'No active disposable manual fixture pointer.' }
$p = Get-Content -LiteralPath $pointer -Raw -Encoding UTF8 | ConvertFrom-Json
$root = [IO.Path]::GetFullPath([string]$p.root).TrimEnd('\')
$temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
if (!$root.StartsWith($temp + '\mailapp-', [StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected fixture root.' }
function FixturePath([string]$path) {
    if ([string]::IsNullOrWhiteSpace($path)) { throw 'Missing fixture path.' }
    $full = [IO.Path]::GetFullPath($path)
    if (!$full.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Path outside fixture.' }
    $full
}
$manifest = FixturePath ([string]$p.manifest)
if (!(Test-Path -LiteralPath $manifest -PathType Leaf)) { throw 'Missing private manifest.' }
$f = Get-Content -LiteralPath $manifest -Raw -Encoding UTF8 | ConvertFrom-Json
if ([IO.Path]::GetFullPath([string]$f.root).TrimEnd('\') -ne $root) { throw 'Manifest root mismatch.' }
$stop = FixturePath ([string]$f.stopFile)
if (Test-Path -LiteralPath $stop) { throw 'Fixture is stopping.' }
$uri = [uri]$f.baseUrl
if ($uri.Scheme -ne 'https' -or $uri.Host -ne 'localhost' -or $uri.Port -le 1024 -or $uri.Port -eq 8443) { throw 'Not fixture HTTPS.' }
if (!(Test-Path -LiteralPath (FixturePath ([string]$f.cert)) -PathType Leaf)) { throw 'Missing disposable cert.' }
$dir = FixturePath (Join-Path $root $Channel)
function Scrub([string]$text) {
    $values = @([string]$f.adminInitialPassword,[string]$f.adminPassword)
    foreach ($u in $f.users) { $values += @([string]$u.password,[string]$u.appPassword) }
    foreach ($v in $values) { if (![string]::IsNullOrWhiteSpace($v)) { $text = $text.Replace($v,'[fixture secret]') } }
    $text -replace '(?i)Bearer\s+[A-Za-z0-9._~-]+','Bearer [redacted]' -replace '\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b','[JWT redacted]'
}
if ($Mode -eq 'start') {
    if (![Environment]::UserInteractive -or (Get-Process -Id $PID).SessionId -eq 0) { throw 'Not an interactive Windows desktop session.' }
    if (Test-Path -LiteralPath $dir) { throw 'Channel already exists; choose a new name.' }
    $listener = @(Get-NetTCPConnection -State Listen -LocalPort $uri.Port -ErrorAction Stop | Select-Object -ExpandProperty OwningProcess -Unique)
    if ($listener.Count -ne 1) { throw 'Cannot identify fixture HTTPS listener.' }
    $server = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener[0])"
    if (!$f.disposable -or !$server -or !$server.CommandLine -or $server.CommandLine -notlike '*mail.gmail.local-test-transport=true*' -or $server.CommandLine -notlike '*-jar*' -or [int]$f.serverPid -ne [int]$server.ProcessId -or [int]$p.pid -ne [int]$server.ParentProcessId) { throw 'HTTPS listener is not the disposable local-mail JAR.' }
    if (!$Java21Home) {
        $Java21Home = @(Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.jdks') -Directory | Where-Object { (Test-Path -LiteralPath (Join-Path $_.FullName 'release')) -and ((Get-Content -LiteralPath (Join-Path $_.FullName 'release') -Raw) -match 'JAVA_VERSION="21\.') } | Select-Object -ExpandProperty FullName -First 1)[0]
    }
    if (!$Java21Home -or ((Get-Content -LiteralPath (Join-Path $Java21Home 'release') -Raw) -notmatch 'JAVA_VERSION="21\.')) { throw 'Java 21 required.' }
    $java = Join-Path $Java21Home 'bin\java.exe'
    $classes = Join-Path $client 'build\classes\java\main'
    $resources = Join-Path $client 'build\resources\main'
    if (!(Test-Path -LiteralPath (Join-Path $classes 'com\mailapp\client\Main.class'))) { throw 'No built Client classes; coordinate with prime.' }
    $cache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
    $jars = @()
    foreach ($spec in @('com.fasterxml.jackson.core|jackson-annotations','com.fasterxml.jackson.core|jackson-core','com.fasterxml.jackson.core|jackson-databind','com.fasterxml.jackson.datatype|jackson-datatype-jsr310')) {
        $group,$artifact = $spec.Split('|')
        $base = Join-Path $cache "$group\$artifact\2.18.12"
        $jar = @(Get-ChildItem -LiteralPath $base -Recurse -File -Filter "$artifact-2.18.12.jar" | Select-Object -ExpandProperty FullName -First 1)
        if ($jar.Count -ne 1) { throw "Missing cached dependency $artifact 2.18.12." }
        $jars += $jar[0]
    }
    $cp = (@($classes,$resources)+$jars) -join ';'
    $src = Join-Path $PSScriptRoot 'ManualSwingConsole.java'
    $stdout = Join-Path $root "$Channel.stdout.log"
    $stderr = Join-Path $root "$Channel.stderr.log"
    $args = @('-Dfile.encoding=UTF-8','-Djava.awt.headless=false',('"-Duser.home=' + $root + '"'),'-cp',('"' + $cp + '"'),('"' + $src + '"'),('"' + $manifest + '"'),$Channel)
    $child = Start-Process -FilePath $java -ArgumentList ($args -join ' ') -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $ready = Join-Path $dir 'ready.json'
    for ($i=0;$i -lt 180;$i++) {
        if (Test-Path -LiteralPath $ready) {
            Get-Content -LiteralPath $ready -Raw -Encoding UTF8
            return
        }
        if ($child.HasExited) { throw (Scrub ((Get-Content -LiteralPath $stderr -Tail 15 -Encoding UTF8) -join [Environment]::NewLine)) }
        Start-Sleep -Milliseconds 100
    }
    throw 'Fixture Client console startup timed out.'
}
$ready = Join-Path $dir 'ready.json'
if (!(Test-Path -LiteralPath $ready -PathType Leaf)) { throw 'Manual console is not ready.' }
$info = Get-Content -LiteralPath $ready -Raw -Encoding UTF8 | ConvertFrom-Json
$proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($info.pid)"
if (!$proc -or $proc.CommandLine -notlike '*ManualSwingConsole.java*' -or $proc.CommandLine -notlike "*$manifest*" -or $proc.CommandLine -notlike "*$Channel*") { throw 'Console PID ownership mismatch.' }
$in = Join-Path $dir 'request.json'
$err = Join-Path $dir 'action-error.json'
if (Test-Path -LiteralPath $in) { throw 'Previous command still pending.' }
if (Test-Path -LiteralPath $err) {
    $prev = Get-Content -LiteralPath $err -Raw -Encoding UTF8
    Remove-Item -LiteralPath $err -Force
    Write-Output ('Previous action result: ' + (Scrub $prev))
}
$id = [guid]::NewGuid().ToString('N')
$out = Join-Path $dir "response-$id.json"
$q = @{id=$id;op=$Mode;target=$Target;value=$Value;name=$Name;index=$Index;x=$X;y=$Y;code=$Code;right=[bool]$Right;rightClick=[bool]$RightClick;double=[bool]$Double;ctrl=[bool]$Ctrl;shift=[bool]$Shift}
if ($Row -ge 0) { $q.row = $Row }
$tmp = Join-Path $dir 'request.next'
[IO.File]::WriteAllText($tmp,($q | ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
Move-Item -LiteralPath $tmp -Destination $in
for ($i=0;$i -lt 100;$i++) {
    if (Test-Path -LiteralPath $out) {
        try { $r=Get-Content -LiteralPath $out -Raw -Encoding UTF8 | ConvertFrom-Json }
        catch [System.IO.IOException] { Start-Sleep -Milliseconds 100; continue }
        if ($r.id -eq $id) {
            Remove-Item -LiteralPath $out -Force -ErrorAction SilentlyContinue
            if ($r.error) { throw (Scrub ([string]$r.error)) }
            Write-Output (Scrub ([string]$r.observation))
            return
        }
    }
    Start-Sleep -Milliseconds 100
}
throw 'Manual console did not respond; do not blindly retry a potentially dispatched action.'
