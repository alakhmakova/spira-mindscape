# tunnel-start.ps1
#
# Puts the locally-running app on a public HTTPS URL, so a phone can reach it from anywhere —
# mobile data included, no shared Wi-Fi and no port forwarding. The tunnel dials OUT from this
# machine to the provider's edge; the phone talks to the edge.
#
#   .\tunnel-start.ps1                      # cloudflared + the dev server (hot reload)
#   .\tunnel-start.ps1 -Build               # cloudflared + the built bundle  <-- use this one
#   .\tunnel-start.ps1 -Provider ngrok      # ngrok, using the static domain in .env.local
#   .\tunnel-start.ps1 -NoVite              # just the tunnel; start Vite yourself
#
# ── Why -Build is usually what you want ─────────────────────────────────────────────────────
#
# The Vite dev server sends **uncompressed ES modules, one request per file**. Measured on this
# project: ONE cold load of the dashboard is 6.17 MB over 103 requests, and a single React chunk
# is 1,005,279 bytes. That is what ate a 1 GB ngrok allowance in a couple of days (~165 page
# loads), and it is why free relays start returning 502 — they are not built for that shape of
# traffic. The built bundle is minified, code-split and a handful of requests, so -Build is an
# order of magnitude cheaper on the wire. You lose hot reload; you gain a tunnel that survives.
#
# ── Why the URL has to exist before Vite starts ─────────────────────────────────────────────
#
# Vite refuses requests whose Host header it does not know ("Blocked request. This host ... is not
# allowed"), and it reads the allow-list ONCE at startup from NGROK_URL in .env.local. A
# cloudflared quick tunnel gets a fresh random hostname every run, so the order is forced:
# tunnel first, write .env.local, then start Vite. This script does exactly that, which is the
# whole reason it exists.
#
# ── Read this before sharing the URL ────────────────────────────────────────────────────────
#
# The backend runs under the `local` profile, where LocalDevAuthFilter authenticates EVERY request
# as dev@local. There is no login. Anyone who has the URL is inside the app with full access to
# whatever is in your local database — including any real API key you have entered. A random
# hostname is obscurity, not authentication. Fine for an hour of testing; put Cloudflare Access in
# front of it if the link is going to live.

param(
    [ValidateSet("cloudflared", "ngrok")]
    [string]$Provider = "cloudflared",

    # Build the SPA and tunnel that instead of the dev server. See the note above.
    [switch]$Build,

    # Leave Vite alone — print the URL and stop. You then have to start Vite yourself AFTER
    # .env.local has been written, or the host check will block every request.
    [switch]$NoVite
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$envLocal = Join-Path $root ".env.local"
$port = if ($Build) { 4173 } else { 5173 }

function Get-EnvValue($file, $key) {
    if (-not (Test-Path $file)) { return $null }
    $line = Get-Content $file | Where-Object { $_ -match "^$key=" } | Select-Object -First 1
    if ($line) { return ($line -replace "^$key=", "").Trim() }
    return $null
}

function Set-EnvValue($file, $key, $value) {
    $lines = @()
    if (Test-Path $file) { $lines = [System.IO.File]::ReadAllLines($file) }
    $updated = @($lines | Where-Object { $_ -notmatch "^$key=" })
    $updated += "$key=$value"
    [System.IO.File]::WriteAllLines($file, $updated, [System.Text.UTF8Encoding]::new($false))
}

function Stop-ProcessOnPort($p) {
    $owner = (Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1).OwningProcess
    if ($owner) { Stop-Process -Id $owner -Force -ErrorAction SilentlyContinue; Start-Sleep 2 }
}

# ── The tunnel ──────────────────────────────────────────────────────────────────────────────

$url = $null

if ($Provider -eq "cloudflared") {
    # Installed as a plain signed .exe rather than the MSI: the installer needs an elevation
    # prompt, and this needs none.
    $cf = @(
        (Join-Path $env:LOCALAPPDATA "cloudflared\cloudflared.exe"),
        "C:\Program Files (x86)\cloudflared\cloudflared.exe",
        "C:\Program Files\cloudflared\cloudflared.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1

    if (-not $cf) {
        $cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
        if ($cmd) { $cf = $cmd.Source }
    }
    if (-not $cf) {
        Write-Host "cloudflared not found. Get the signed binary (no installer, no admin):" -ForegroundColor Red
        Write-Host "  curl -L -o `"$env:LOCALAPPDATA\cloudflared\cloudflared.exe`" ``" -ForegroundColor Yellow
        Write-Host "    https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" -ForegroundColor Yellow
        exit 1
    }

    Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep 1

    # The quick tunnel prints its hostname into the log, not to a queryable API the way ngrok
    # does, so the log is where we have to read it from.
    $log = Join-Path $root "cf.log"
    Remove-Item $log -ErrorAction SilentlyContinue
    Write-Host "Starting cloudflared quick tunnel -> localhost:$port ..." -ForegroundColor Cyan
    # **127.0.0.1, not localhost.** cloudflared resolves `localhost` to IPv6 `::1` first, and a
    # dev server bound only to IPv4 then refuses it — the tunnel comes up healthy and every request
    # fails with "Unable to reach the origin service". Naming the IPv4 address removes the guess.
    Start-Process $cf -ArgumentList "tunnel --url http://127.0.0.1:$port --logfile `"$log`"" -WindowStyle Hidden

    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep 1
        if (-not (Test-Path $log)) { continue }
        $hit = Select-String -Path $log -Pattern "https://[a-z0-9-]+\.trycloudflare\.com" -ErrorAction SilentlyContinue |
            Select-Object -Last 1
        if ($hit -and $hit.Matches.Count) { $url = $hit.Matches[0].Value; break }
    }
    if (-not $url) {
        Write-Host "ERROR: no tunnel URL after 40s. See $log" -ForegroundColor Red
        exit 1
    }
}
else {
    Get-Process ngrok -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep 1

    # ngrok's free tier is capped at 1 GB/month and this project burns it fast — see the -Build
    # note at the top. ERR_NGROK_725 in the browser means that cap, not a broken tunnel.
    $existing = Get-EnvValue $envLocal "NGROK_URL"
    $static = if ($existing -and $existing -match "ngrok") { ([System.Uri]$existing).Host } else { $null }
    if ($static) {
        Write-Host "Starting ngrok on the static domain $static -> localhost:$port ..." -ForegroundColor Cyan
        Start-Process ngrok -ArgumentList "http --domain=$static $port" -WindowStyle Minimized
    } else {
        Write-Host "Starting ngrok (dynamic URL) -> localhost:$port ..." -ForegroundColor Cyan
        Start-Process ngrok -ArgumentList "http $port" -WindowStyle Minimized
    }

    for ($i = 0; $i -lt 25; $i++) {
        Start-Sleep 1
        try {
            $resp = Invoke-WebRequest "http://localhost:4040/api/tunnels" -UseBasicParsing -ErrorAction Stop
            $url = (($resp.Content | ConvertFrom-Json).tunnels | Where-Object { $_.proto -eq "https" }).public_url
            if ($url) { break }
        } catch {}
    }
    if (-not $url) {
        Write-Host "ERROR: no ngrok URL. Check http://localhost:4040" -ForegroundColor Red
        exit 1
    }
}

# ── The allow-list, then the server ─────────────────────────────────────────────────────────

Set-EnvValue $envLocal "NGROK_URL" $url
Write-Host "Wrote NGROK_URL to .env.local" -ForegroundColor Gray

if (-not $NoVite) {
    Stop-ProcessOnPort $port
    if ($Build) {
        Write-Host "Building the SPA ..." -ForegroundColor Cyan
        # Through cmd, not directly: Windows PowerShell wraps a native command's stderr in
        # ErrorRecords, and Vite writes its build progress there — so with $ErrorActionPreference
        # = "Stop" a perfectly successful build aborts the script. The exit code is the honest
        # signal, so check that instead.
        cmd /c "npm run build > build.log 2>&1"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: the build failed. See build.log" -ForegroundColor Red
            exit 1
        }
        Write-Host "Serving the build on :$port ..." -ForegroundColor Cyan
        Start-Process cmd.exe -ArgumentList '/c', "npx vite preview > preview.log 2>&1" `
            -WorkingDirectory $root -WindowStyle Hidden
    } else {
        Write-Host "Starting the dev server on :$port ..." -ForegroundColor Cyan
        Start-Process cmd.exe -ArgumentList '/c', "npm run dev > vite.log 2>&1" `
            -WorkingDirectory $root -WindowStyle Hidden
    }

    # Prove the whole chain rather than announcing a URL nobody has tried.
    $ok = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep 2
        try {
            $r = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 10 `
                -Headers @{ "bypass-tunnel-reminder" = "1" } -ErrorAction Stop
            if ($r.StatusCode -eq 200) { $ok = $true; break }
        } catch {}
    }
    if (-not $ok) {
        Write-Host "WARNING: the tunnel is up but $url did not answer 200 yet." -ForegroundColor DarkYellow
        Write-Host "         Check vite.log / preview.log, then reload." -ForegroundColor DarkYellow
    }
}

$sep = "=" * 62
Write-Host ""
Write-Host $sep -ForegroundColor Green
Write-Host "  $url" -ForegroundColor Yellow
Write-Host $sep -ForegroundColor Green
Write-Host ""
if (-not $Build -and $Provider -eq "cloudflared") {
    Write-Host "  Serving the DEV server: ~6 MB and ~100 requests per page load." -ForegroundColor DarkYellow
    Write-Host "  Re-run with -Build to serve the bundle instead (far lighter)." -ForegroundColor DarkYellow
    Write-Host ""
}
Write-Host "  No login: the local profile signs every visitor in as dev@local." -ForegroundColor DarkYellow
Write-Host "  Anyone with this link is inside your local app. Share accordingly." -ForegroundColor DarkYellow
Write-Host ""
Write-Host "  The laptop has to stay awake, with Docker + the backend running." -ForegroundColor Gray
Write-Host "  Stop:  Get-Process cloudflared,ngrok -EA 0 | Stop-Process -Force" -ForegroundColor Gray
Write-Host ""
