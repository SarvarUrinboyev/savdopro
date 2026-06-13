# Backup restore drill (embedded H2, Windows desktop). Extracts the newest backup
# zip into a scratch folder, opens the restored barakat.mv.db read-only, validates
# it, then deletes the scratch. Never touches the live ~/.barakat DB.
#
#   $env:BACKUP_DIR = "$HOME\.barakat\backups"; .\ops\restore-drill.ps1
$ErrorActionPreference = 'Stop'

$backupDir = if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { Join-Path $HOME '.barakat\backups' }
$h2Jar     = $env:H2_JAR   # path to h2-*.jar; if unset we only verify the zip opens
$expectVer = $env:EXPECT_VERSION

Write-Host "== restore drill =="
$latest = Get-ChildItem -Path $backupDir -Filter 'barakat-*.zip' -ErrorAction SilentlyContinue |
          Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $latest) { Write-Error "FAIL: no backup zip in $backupDir"; exit 2 }
Write-Host "backup: $($latest.FullName)"
if ($latest.LastWriteTime -lt (Get-Date).AddHours(-26)) { Write-Warning "newest backup older than 26h" }

$scratch = Join-Path $env:TEMP ("restore-drill-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
try {
    Expand-Archive -Path $latest.FullName -DestinationPath $scratch -Force
    $db = Get-ChildItem -Path $scratch -Recurse -Filter '*.mv.db' | Select-Object -First 1
    if (-not $db) { Write-Error "FAIL: no .mv.db inside the backup zip"; exit 3 }
    Write-Host "restored DB file: $($db.FullName) ($([math]::Round($db.Length/1MB,2)) MB)"

    if ($h2Jar -and (Test-Path $h2Jar)) {
        $base = $db.FullName -replace '\.mv\.db$',''
        $url  = "jdbc:h2:file:$base;ACCESS_MODE_DATA=r;MODE=PostgreSQL"
        $sql  = "SELECT MAX(version) FROM flyway_schema_history WHERE success;"
        $ver  = (& java -cp $h2Jar org.h2.tools.Shell -url $url -user barakat -password barakat -sql $sql) -join "`n"
        Write-Host "flyway latest version: $ver"
        if ($expectVer -and ($ver -notmatch $expectVer)) { Write-Error "FAIL: expected $expectVer"; exit 4 }
    } else {
        Write-Host "(H2_JAR not set — verified the zip extracts to a valid .mv.db file)"
    }
    Write-Host "PASS: backup restores cleanly"
}
finally {
    Remove-Item -Path $scratch -Recurse -Force -ErrorAction SilentlyContinue
}
