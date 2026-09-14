$ErrorActionPreference = "Stop"

$KeystoreFile = Join-Path $PWD "kiosk-release.jks"
$Alias = "matylda-house"

if (Test-Path $KeystoreFile) {
    throw "The file already exists: $KeystoreFile`nMove it or delete it intentionally before generating another key."
}

$keytool = Get-Command keytool.exe -ErrorAction SilentlyContinue

if (-not $keytool) {
    $AndroidStudioKeytool = Join-Path $env:ProgramFiles "Android\Android Studio\jbr\bin\keytool.exe"

    if (Test-Path $AndroidStudioKeytool) {
        $KeytoolPath = $AndroidStudioKeytool
    }
    else {
        throw @"
keytool.exe was not found.

Install Android Studio or JDK 17, then restart PowerShell.
Android Studio normally provides it here:
$AndroidStudioKeytool
"@
    }
}
else {
    $KeytoolPath = $keytool.Source
}

Write-Host "Generating the Matylda House signing key..." -ForegroundColor Cyan
Write-Host "You will be asked for a keystore password and certificate details."
Write-Host "Remember the password: GitHub Actions will need it." -ForegroundColor Yellow

& $KeytoolPath `
    -genkeypair `
    -v `
    -keystore $KeystoreFile `
    -alias $Alias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000

if ($LASTEXITCODE -ne 0) {
    throw "keytool failed with exit code $LASTEXITCODE."
}

$Base64 = [Convert]::ToBase64String(
    [System.IO.File]::ReadAllBytes($KeystoreFile)
)

$Base64File = Join-Path $PWD "kiosk-release.jks.base64.txt"
[System.IO.File]::WriteAllText($Base64File, $Base64)

Set-Clipboard -Value $Base64

Write-Host ""
Write-Host "Signing key generated successfully." -ForegroundColor Green
Write-Host "Keystore:   $KeystoreFile"
Write-Host "Base64 file: $Base64File"
Write-Host ""
Write-Host "The Base64 value is also copied to your clipboard." -ForegroundColor Green
Write-Host ""
Write-Host "Create these GitHub Actions secrets:"
Write-Host "KIOSK_KEYSTORE_BASE64  = paste from clipboard"
Write-Host "KIOSK_KEYSTORE_PASSWORD = the password you entered"
Write-Host "KIOSK_KEY_ALIAS         = $Alias"
Write-Host "KIOSK_KEY_PASSWORD      = the key password you entered"
Write-Host ""
Write-Host "Back up kiosk-release.jks securely. Do not commit it to Git." -ForegroundColor Yellow