<#
.SYNOPSIS
    DMS Agent Installer — cai dat tu dong tu A toi Z.

.DESCRIPTION
    Script nay tu dong:
    1. Tao thu muc C:\DMS-Agent\
    2. Tai file dms-agent.exe tu server
    3. Cai lam Windows Service (chay ngam, tu dong khi boot)
    4. Khoi dong service ngay

.PARAMETER Server
    URL server DMS Monitor. Vi du: http://192.168.1.100:8000

.PARAMETER Token
    Secret token cua thiet bi (lay tu app khi tao thiet bi)

.PARAMETER DeviceId
    ID cua thiet bi (lay tu app khi tao thiet bi)

.PARAMETER InstallDir
    Thu muc cai dat (mac dinh: C:\DMS-Agent)

.EXAMPLE
    # Cach 1: Chay truc tiep tu URL (1 lenh duy nhat)
    irm http://192.168.1.100:8000/static/install.ps1 | iex

    # Nhung vi can tham so, nen dung cach nay:
    & ([scriptblock]::Create((irm http://192.168.1.100:8000/static/install.ps1))) -Server http://192.168.1.100:8000 -Token dms_xxx -DeviceId 15

    # Cach 2: Neu da tai script ve may
    .\install.ps1 -Server http://192.168.1.100:8000 -Token dms_AbCdEfGhIj123 -DeviceId 15
#>

param(
    [Parameter(Mandatory=$true, HelpMessage="URL server DMS Monitor (vi du: http://192.168.1.100:8000)")]
    [string]$Server,

    [Parameter(Mandatory=$true, HelpMessage="Secret token cua thiet bi (lay tu app)")]
    [string]$Token,

    [Parameter(Mandatory=$true, HelpMessage="Device ID (lay tu app)")]
    [int]$DeviceId,

    [Parameter(HelpMessage="Thu muc cai dat (mac dinh: C:\DMS-Agent)")]
    [string]$InstallDir = "C:\DMS-Agent"
)

$ErrorActionPreference = "Stop"
$ServiceName = "DMS-Agent"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  DMS Monitor Agent — Cai dat tu dong" -ForegroundColor Cyan
Write-Host "  Server : $Server" -ForegroundColor Cyan
Write-Host "  Device : #$DeviceId" -ForegroundColor Cyan
Write-Host "  Thu muc: $InstallDir" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Kiem tra quyen Admin
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "[LOI] Can chay voi quyen Administrator!" -ForegroundColor Red
    Write-Host "Huong dan: Click phai vao PowerShell -> 'Run as administrator'" -ForegroundColor Yellow
    exit 1
}

# Buoc 1: Tao thu muc cai dat
Write-Host "[1/5] Tao thu muc: $InstallDir" -ForegroundColor Green
if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
}

# Buoc 2: Tai file agent
$exePath = Join-Path $InstallDir "dms-agent.exe"
$downloadUrl = "$($Server.TrimEnd('/'))/static/dms-agent.exe"
Write-Host "[2/5] Tai agent tu: $downloadUrl" -ForegroundColor Green
try {
    Invoke-WebRequest -Uri $downloadUrl -OutFile $exePath -UseBasicParsing
    $sizeMB = [math]::Round((Get-Item $exePath).Length / 1MB, 1)
    Write-Host "      Da tai: $exePath ($sizeMB MB)" -ForegroundColor Gray
} catch {
    Write-Host "[LOI] Khong the tai agent: $_" -ForegroundColor Red
    Write-Host "      Kiem tra server co dang chay khong: $Server" -ForegroundColor Yellow
    exit 1
}

# Buoc 3: Cau hinh (tao config.json)
$configPath = Join-Path $InstallDir "config.json"
Write-Host "[3/5] Tao cau hinh: $configPath" -ForegroundColor Green

# Chuan hoa server URL
$serverUrl = $Server.TrimEnd('/')
if (-not $serverUrl.EndsWith('/api/v1')) {
    $serverUrl = "$serverUrl/api/v1"
}

$config = @{
    backend_url                 = $serverUrl
    secret_token                = $Token
    device_id                   = $DeviceId
    heartbeat_interval_seconds  = 10
    command_poll_interval_seconds = 5
    process_top_n               = 15
    log_level                   = "info"
}
$config | ConvertTo-Json | Set-Content -Path $configPath -Encoding UTF8
Write-Host "      backend_url: $serverUrl" -ForegroundColor Gray
Write-Host "      device_id  : $DeviceId" -ForegroundColor Gray

# Buoc 4: Go cai service cu neu co
$existingService = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($existingService) {
    Write-Host "[4/5] Xoa service cu truoc khi cai lai..." -ForegroundColor Yellow
    if ($existingService.Status -eq 'Running') {
        Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }
    & sc.exe delete $ServiceName | Out-Null
    Start-Sleep -Seconds 1
} else {
    Write-Host "[4/5] Cai Windows Service..." -ForegroundColor Green
}

# Buoc 5: Cai Windows Service
Write-Host "[5/5] Dang ky Windows Service '$ServiceName'..." -ForegroundColor Green

$binPath = "`"$exePath`""
& sc.exe create $ServiceName binPath= $binPath DisplayName= "DMS Monitor Agent" start= auto obj= LocalSystem | Out-Null
& sc.exe description $ServiceName "He thong giam sat may tinh tu xa - DMS Monitor Agent v1.1.0" | Out-Null

# Khoi dong service ngay
Write-Host "      Khoi dong service..." -ForegroundColor Gray
& sc.exe start $ServiceName | Out-Null
Start-Sleep -Seconds 3

# Kiem tra trang thai
$svc = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($svc -and $svc.Status -eq 'Running') {
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "  CAI DAT THANH CONG!" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "  Service : $ServiceName [RUNNING]" -ForegroundColor Green
    Write-Host "  Log     : $InstallDir\dms-agent.log" -ForegroundColor Green
    Write-Host "  Config  : $configPath" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Thiet bi se hien thi Online tren app trong vai giay." -ForegroundColor Cyan
    Write-Host "  Agent se tu dong chay lai khi may tinh khoi dong." -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  Go cai dat: sc stop $ServiceName; sc delete $ServiceName" -ForegroundColor Gray
    Write-Host "============================================" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "[CANH BAO] Service da cai nhung chua chay." -ForegroundColor Yellow
    Write-Host "           Kiem tra log: $InstallDir\dms-agent.log" -ForegroundColor Yellow
    Write-Host "           Chay thu: sc start $ServiceName" -ForegroundColor Yellow
}
