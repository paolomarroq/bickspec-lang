param(
    [string]$JarPath = "app/target/bickspec-compiler-1.0.0.jar",
    [string]$InputPath = "testing"
)

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$jarFullPath = Join-Path $repoRoot $JarPath
$inputFullPath = Join-Path $repoRoot $InputPath

if (-not (Test-Path $jarFullPath -PathType Leaf)) {
    Write-Error "Jar not found at '$jarFullPath'. Build first with: mvn -f app/pom.xml package"
    exit 1
}

if (-not (Test-Path $inputFullPath)) {
    Write-Error "Input path not found: '$inputFullPath'"
    exit 1
}

& java -jar $jarFullPath $inputFullPath
exit $LASTEXITCODE
