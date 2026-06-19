# ngrok-start.ps1
# Starts ngrok tunnel for local mobile testing.
# Reads NGROK_URL from .env.local (static domain) or fetches a dynamic URL.
# Writes the URL back to .env.local so the next run remembers it.
#
# Usage: .\ngrok-start.ps1

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$envLocal = Join-Path $root ".env.local"

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

$existingUrl = Get-EnvValue $envLocal "NGROK_URL"

# Kill any running ngrok
$running = Get-Process -Name ngrok -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "Stopping existing ngrok..." -ForegroundColor Gray
    $running | Stop-Process -Force
    Start-Sleep 1
}

# Start ngrok
if ($existingUrl) {
    $domain = ([System.Uri]$existingUrl).Host
    Write-Host "Starting ngrok with static domain: $domain" -ForegroundColor Cyan
    Start-Process ngrok -ArgumentList "http --domain=$domain 5173" -WindowStyle Minimized
} else {
    Write-Host "Starting ngrok (dynamic URL)..." -ForegroundColor Cyan
    Start-Process ngrok -ArgumentList "http 5173" -WindowStyle Minimized
}

# Wait for ngrok API
Write-Host "Waiting for tunnel..." -ForegroundColor Gray
$url = $null
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep 1
    try {
        $resp = Invoke-WebRequest "http://localhost:4040/api/tunnels" -UseBasicParsing -ErrorAction Stop
        $tunnels = ($resp.Content | ConvertFrom-Json).tunnels
        $url = ($tunnels | Where-Object { $_.proto -eq "https" }).public_url
        if ($url) { break }
    } catch {}
}

if (-not $url) {
    Write-Host ""
    Write-Host "ERROR: Could not get ngrok URL. Check http://localhost:4040" -ForegroundColor Red
    exit 1
}

# Save URL to .env.local
Set-EnvValue $envLocal "NGROK_URL" $url

$sep = "=" * 56
$redirect = "$url/login/oauth2/code/google"

Write-Host ""
Write-Host $sep -ForegroundColor Green
Write-Host "  NGROK URL: $url" -ForegroundColor Yellow
Write-Host $sep -ForegroundColor Green
Write-Host ""

Write-Host "NEXT STEPS:" -ForegroundColor Cyan
Write-Host ""
Write-Host "  1. Restart Vite (reads NGROK_URL from .env.local automatically):" -ForegroundColor White
Write-Host "     npm run dev" -ForegroundColor Yellow
Write-Host ""
Write-Host "  2. Restart Spring Boot with env variable:" -ForegroundColor White
Write-Host "     FRONTEND_URL=$url" -ForegroundColor Yellow
Write-Host "     (IntelliJ: Run > Edit Configurations > Environment variables)" -ForegroundColor Gray
Write-Host ""
Write-Host "  3. Google Cloud Console (once per URL):" -ForegroundColor White
Write-Host "     https://console.cloud.google.com/apis/credentials" -ForegroundColor Gray
Write-Host "     Credentials > OAuth 2.0 Client > Authorized redirect URIs > Add:" -ForegroundColor Gray
Write-Host "     $redirect" -ForegroundColor Yellow
Write-Host ""
Write-Host "  4. Open on mobile: $url" -ForegroundColor White
Write-Host ""

if (-not $existingUrl) {
    Write-Host $sep -ForegroundColor DarkYellow
    Write-Host "  TIP: get a free static ngrok domain so this URL never changes" -ForegroundColor DarkYellow
    Write-Host "  and you only add the redirect URI to Google Console once." -ForegroundColor DarkYellow
    Write-Host "  https://dashboard.ngrok.com/domains" -ForegroundColor DarkYellow
    Write-Host $sep -ForegroundColor DarkYellow
    Write-Host ""
}

Write-Host "Ngrok inspector: http://localhost:4040" -ForegroundColor Gray
