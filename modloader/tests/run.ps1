$ErrorActionPreference = "Stop"

$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Lib = Resolve-Path (Join-Path $Here "../..")
$Core = Join-Path $Lib "core"
$Tests = Join-Path $Here "aero/modellib"
$Out = Join-Path $Here "out"
$Junit = Join-Path $Here "libs/junit-4.13.2.jar"
$Hamcrest = Join-Path $Here "libs/hamcrest-core-1.3.jar"

Write-Host "=== Compiling core/ + tests/ ==="
if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force $Out | Out-Null

$CoreFiles = @(Get-ChildItem $Core -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$TestFiles = @(Get-ChildItem $Tests -Recurse -Filter *.java | ForEach-Object { $_.FullName })

if ($TestFiles.Count -eq 0) {
    Write-Host "No test files found in $Tests."
    exit 0
}

$CompileArgs = @(
    "-source", "1.8",
    "-target", "1.8",
    "-cp", "$Junit;$Hamcrest",
    "-d", $Out
) + $CoreFiles + $TestFiles

& javac $CompileArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== Running tests ==="
$TestClasses = @(
    Get-ChildItem $Tests -Recurse -Filter *Test.java |
        ForEach-Object { "aero.modellib." + [IO.Path]::GetFileNameWithoutExtension($_.Name) }
)

$RunArgs = @(
    "-cp", "$Out;$Junit;$Hamcrest",
    "org.junit.runner.JUnitCore"
) + $TestClasses

& java $RunArgs
exit $LASTEXITCODE
