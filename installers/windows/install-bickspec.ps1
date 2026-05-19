$ErrorActionPreference = "Stop"

$jarName = "bickspec-compiler-1.0.0.jar"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoJar = Resolve-Path (Join-Path $scriptRoot "..\..\vscode-extension\media\compiler\$jarName") -ErrorAction SilentlyContinue
$installRoot = Join-Path $env:LOCALAPPDATA "BickSpec"
$compilerDir = Join-Path $installRoot "compiler"
$binDir = Join-Path $installRoot "bin"
$targetJar = Join-Path $compilerDir $jarName
$wrapperPath = Join-Path $binDir "bickspec.cmd"

if (-not $repoJar) {
  Write-Error "Bundled compiler JAR not found. Expected vscode-extension\media\compiler\$jarName next to this repository."
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
  Write-Error "Java is required before installing the BickSpec CLI wrapper."
}

New-Item -ItemType Directory -Force -Path $compilerDir | Out-Null
New-Item -ItemType Directory -Force -Path $binDir | Out-Null
Copy-Item -LiteralPath $repoJar.Path -Destination $targetJar -Force

$wrapperContent = "@echo off`r`njava -jar `"%LOCALAPPDATA%\BickSpec\compiler\$jarName`" %*`r`n"
Set-Content -LiteralPath $wrapperPath -Value $wrapperContent -Encoding ASCII

$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$pathParts = @()
if ($userPath) {
  $pathParts = $userPath.Split(";") | Where-Object { $_ }
}
if ($pathParts -notcontains $binDir) {
  $newPath = if ($userPath) { "$userPath;$binDir" } else { $binDir }
  [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
  Write-Host "Added to user PATH: $binDir"
} else {
  Write-Host "User PATH already contains: $binDir"
}

Write-Host "Verifying Java..."
& java -version

Write-Host "Verifying installed compiler..."
& cmd /c "`"$wrapperPath`" `"$targetJar`""
if ($LASTEXITCODE -ne 0) {
  Write-Host "Wrapper created. BickSpec returned a non-zero status during no-argument validation, which is acceptable if it printed usage or an input error."
}

Write-Host ""
Write-Host "Installed:"
Write-Host "  JAR: $targetJar"
Write-Host "  Wrapper: $wrapperPath"
Write-Host "Open a new terminal, then run:"
Write-Host "  bickspec path\to\program.bks"
