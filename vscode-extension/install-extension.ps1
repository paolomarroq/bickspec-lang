$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$vsix = Get-ChildItem -LiteralPath $scriptRoot -Filter *.vsix | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $vsix) {
  Write-Error "No .vsix file was found in $scriptRoot. Run npm run package:vsix first."
}

if (-not (Get-Command code -ErrorAction SilentlyContinue)) {
  Write-Error "VS Code CLI 'code' was not found in PATH. Install VS Code and enable the shell command first."
}

& code --install-extension $vsix.FullName --force

if ($LASTEXITCODE -ne 0) {
  Write-Error "VSIX installation failed."
}

Write-Host "Installed VSIX: $($vsix.Name)"
Write-Host "Open VS Code normally, open a .bks file, and run 'BickSpec: Run Current File'."
