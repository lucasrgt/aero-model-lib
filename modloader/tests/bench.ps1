$ErrorActionPreference = "Stop"

$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Lib = Resolve-Path (Join-Path $Here "../..")
$Core = Join-Path $Lib "core"
$Tests = Join-Path $Here "aero/modellib"
$Out = Join-Path $Here "bench-out"

Write-Host "=== Compiling core benchmark ==="
if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force $Out | Out-Null

$CoreFiles = @(Get-ChildItem $Core -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$SupportFiles = @(
    (Join-Path $Tests "Aero_AnimationState.java"),
    (Join-Path $Tests "CoreBenchmark.java")
)

$CompileArgs = @(
    "-source", "1.8",
    "-target", "1.8",
    "-d", $Out
) + $CoreFiles + $SupportFiles

& javac $CompileArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== Running benchmark ==="
& java @("-cp", $Out, "aero.modellib.CoreBenchmark")
exit $LASTEXITCODE
