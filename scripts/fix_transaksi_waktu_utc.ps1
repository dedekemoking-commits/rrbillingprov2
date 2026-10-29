param(
    [switch]$Apply,
    [string[]]$ExcludeUsernames = @("opencode1793036267", "_6", "_3")
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$supabaseFile = Join-Path $repoRoot "app/src/main/java/com/billingps/aptv/cloud/SupabaseRepository.kt"
$source = Get-Content -LiteralPath $supabaseFile -Raw
$baseUrl = [regex]::Match($source, 'BASE_URL\s*=\s*"([^"]+)"').Groups[1].Value.TrimEnd('/')
$apiKey = [regex]::Match($source, 'API_KEY\s*=\s*"([^"]+)"').Groups[1].Value

if (-not $baseUrl -or -not $apiKey) {
    throw "Supabase BASE_URL/API_KEY tidak ditemukan di $supabaseFile"
}

$headers = @{
    apikey = $apiKey
    Authorization = "Bearer $apiKey"
}

$rows = New-Object System.Collections.Generic.List[object]
$offset = 0
$limit = 1000
while ($true) {
    $uri = "$baseUrl/rest/v1/transaksi?select=id,username,waktu,created_at,total,paket,kota&order=id.asc&limit=$limit&offset=$offset"
    $page = Invoke-RestMethod -Uri $uri -Headers $headers -Method Get
    if ($null -eq $page) { break }
    $page = @($page)
    if ($page.Count -eq 1 -and $page[0] -is [System.Array]) { $page = $page[0] }
    if ($page.Count -eq 0) { break }
    foreach ($row in $page) { $rows.Add($row) }
    if ($page.Count -lt $limit) { break }
    $offset += $limit
}

Write-Host "Total row: $($rows.Count)"
$backupPath = Join-Path $env:TEMP ("billingps_transaksi_backup_" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".json")
$rows | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $backupPath -Encoding UTF8
Write-Host "Backup: $backupPath"

$fiveMinutes = [TimeSpan]::FromMinutes(5).TotalMilliseconds
$sixHours45 = [TimeSpan]::FromMinutes(405).TotalMilliseconds
$sevenHours15 = [TimeSpan]::FromMinutes(435).TotalMilliseconds
$twoHours = [TimeSpan]::FromHours(2).TotalMilliseconds

function Get-IdEpochMillis([string]$id) {
    $match = [regex]::Match($id, '^(?:TRX-|tx_)(\d{12,})$')
    if (-not $match.Success) { return $null }
    return [long]$match.Groups[1].Value
}

$shift = New-Object System.Collections.Generic.List[object]
$normalize = New-Object System.Collections.Generic.List[object]
$keep = 0
$excluded = 0
$review = New-Object System.Collections.Generic.List[object]

foreach ($row in $rows) {
    if ($ExcludeUsernames -contains [string]$row.username) {
        $excluded++
        continue
    }
    if ([string]::IsNullOrWhiteSpace($row.waktu)) {
        $review.Add([pscustomobject]@{ id = $row.id; waktu = $row.waktu; created_at = $row.created_at; reason = "waktu kosong" })
        continue
    }
    try {
        $waktu = [DateTimeOffset]::Parse($row.waktu, [Globalization.CultureInfo]::InvariantCulture)
    } catch {
        $review.Add([pscustomobject]@{ id = $row.id; waktu = $row.waktu; created_at = $row.created_at; reason = "waktu tidak bisa diparse" })
        continue
    }

    $epoch = Get-IdEpochMillis $row.id
    $action = "review"
    $reason = ""

    if ($null -ne $epoch) {
        $diff = $waktu.ToUnixTimeMilliseconds() - $epoch
        if ($diff -ge $sixHours45 -and $diff -le $sevenHours15) {
            $action = "shift"
            $reason = "ID epoch +7 jam"
        } elseif ([Math]::Abs($diff) -le $fiveMinutes) {
            $action = "keep"
        } else {
            $reason = "ID epoch selisih $([Math]::Round($diff / 60000.0, 1)) menit"
        }
    } else {
        try {
            $created = [DateTimeOffset]::Parse($row.created_at, [Globalization.CultureInfo]::InvariantCulture)
            $diff = ($waktu - $created).TotalMilliseconds
            if ($diff -ge $sixHours45 -and $diff -le $sevenHours15) {
                $action = "shift"
                $reason = "created_at +7 jam"
            } elseif ([Math]::Abs($diff) -le $twoHours) {
                $action = "keep"
            } else {
                $reason = "created_at selisih $([Math]::Round($diff / 60000.0, 1)) menit"
            }
        } catch {
            $reason = "created_at tidak bisa diparse"
        }
    }

    $utcValue = $waktu.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'", [Globalization.CultureInfo]::InvariantCulture)
    if ($action -eq "shift") {
        $shift.Add([pscustomobject]@{
            id = $row.id
            old = $row.waktu
            new = $waktu.ToUniversalTime().AddHours(-7).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'", [Globalization.CultureInfo]::InvariantCulture)
            reason = $reason
        })
    } elseif ($action -eq "keep") {
        if ($row.waktu -ne $utcValue) {
            $normalize.Add([pscustomobject]@{ id = $row.id; old = $row.waktu; new = $utcValue; reason = "format UTC" })
        } else {
            $keep++
        }
    } else {
        $review.Add([pscustomobject]@{ id = $row.id; waktu = $row.waktu; created_at = $row.created_at; reason = $reason })
    }
}

Write-Host "Dikecualikan (test) : $excluded"
Write-Host "Akan digeser -7 jam : $($shift.Count)"
Write-Host "Akan dinormalisasi  : $($normalize.Count)"
Write-Host "Sudah UTC / tetap   : $keep"
Write-Host "Perlu review manual : $($review.Count)"

if ($review.Count -gt 0) {
    Write-Host "`nReview manual:"
    $review | Format-Table -AutoSize | Out-String | Write-Host
}

if ($shift.Count -gt 0) {
    Write-Host "`nContoh shift:"
    $shift | Select-Object -First 10 | Format-Table -AutoSize | Out-String | Write-Host
}

if (-not $Apply) {
    Write-Host "`nDry run. Jalankan dengan -Apply untuk menulis perubahan."
    exit 0
}

$updates = New-Object System.Collections.Generic.List[object]
foreach ($item in $shift) { $updates.Add($item) }
foreach ($item in $normalize) { $updates.Add($item) }
$ok = 0
$failed = New-Object System.Collections.Generic.List[object]

foreach ($item in $updates) {
    $encodedId = [uri]::EscapeDataString([string]$item.id)
    $uri = "$baseUrl/rest/v1/transaksi?id=eq.$encodedId"
    $body = @{ waktu = $item.new } | ConvertTo-Json -Compress
    $attempt = 0
    $done = $false
    while (-not $done -and $attempt -lt 3) {
        $attempt++
        try {
            Invoke-RestMethod -Uri $uri -Headers $headers -Method Patch -ContentType "application/json" -Body $body -TimeoutSec 30 | Out-Null
            $ok++
            $done = $true
            if (($ok % 50) -eq 0) { Write-Host "Update $ok/$($updates.Count)..." }
        } catch {
            if ($attempt -ge 3) {
                $failed.Add([pscustomobject]@{ id = $item.id; error = $_.Exception.Message })
            } else {
                Start-Sleep -Milliseconds (500 * $attempt)
            }
        }
    }
}

Write-Host "`nSelesai. Berhasil: $ok, gagal: $($failed.Count)"
if ($failed.Count -gt 0) {
    $failed | Format-Table -AutoSize | Out-String | Write-Host
    exit 1
}
