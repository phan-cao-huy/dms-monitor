# Run from PowerShell on the DMS server. Credentials stay in an ignored local file.
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$sender = (Read-Host 'Gmail address DMS should send from').Trim()
if ($sender -notmatch '^[a-zA-Z0-9._+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$') { throw 'Invalid email address' }
$secret = Read-Host 'Google app password (16 characters, hidden)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) -replace '\s',''
    if ($password -notmatch '^[a-zA-Z0-9]{16}$') { throw 'Enter the 16-character Google app password' }
    $file = Join-Path $project '.password-reset.env.local'
    if (!(Test-Path -LiteralPath $file)) { New-Item -ItemType File -Path $file | Out-Null }
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    icacls $file /inheritance:r /grant:r "*${sid}:F" '*S-1-5-18:F' '*S-1-5-32-544:F' | Out-Null
    if ($LASTEXITCODE) { throw 'Could not secure the configuration file' }
    [IO.File]::WriteAllLines($file, @("RESET_SMTP_USERNAME=$sender", "RESET_SMTP_PASSWORD=$password"), [Text.UTF8Encoding]::new($false))
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $password = $null
    $secret.Dispose()
}
Push-Location $project
try {
    docker compose up -d --no-deps --force-recreate backend
    if ($LASTEXITCODE) { throw 'Backend restart failed' }
    @"
import smtplib
import ssl
import sys
from app.services.password_recovery import mail_config
try:
    config = mail_config()
    with smtplib.SMTP(config["host"], 587, timeout=10) as smtp:
        smtp.starttls(context=ssl.create_default_context())
        smtp.login(config["username"], config["password"])
except Exception:
    sys.exit(1)
"@ | docker compose exec -T backend python -
    if ($LASTEXITCODE) { throw 'Could not sign in to SMTP. Check the address and app password.' }
    Write-Host 'Gmail sender connected. Try Forgot password with your DMS email.'
} finally { Pop-Location }
