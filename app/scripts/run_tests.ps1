param(
    [string]$JarPath = "app/target/bickspec-lexer-runner-1.0.0.jar",
    [string]$InputPath = "testing",
    [switch]$SaveOutput,
    [string]$OutputPath = "testing/outputs/phase2_parse_results.txt"
)

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$jarFullPath = Join-Path $repoRoot $JarPath
$inputFullPath = Join-Path $repoRoot $InputPath

if (-not (Test-Path $jarFullPath -PathType Leaf)) {
    Write-Error "Jar not found at '$jarFullPath'. Build first in IntelliJ: Maven Tool Window -> app -> Lifecycle -> package."
    exit 1
}

if (-not (Test-Path $inputFullPath)) {
    Write-Error "Input path not found: '$inputFullPath'"
    exit 1
}

if ($SaveOutput) {
    $outputFullPath = Join-Path $repoRoot $OutputPath
    $outputDirectory = Split-Path -Parent $outputFullPath
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

    & java -cp $jarFullPath com.bickspec.app.ParseRunner $inputFullPath | Tee-Object -FilePath $outputFullPath
}

& java -cp $jarFullPath com.bickspec.app.ParseRunner $inputFullPath
