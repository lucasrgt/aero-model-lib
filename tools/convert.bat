@echo off
REM ─────────────────────────────────────────────────────────────────────
REM AeroModelLib — convert.bat
REM Converts Blockbench .bbmodel files to AeroModelLib .anim.json format
REM
REM Usage:
REM   tools\convert.bat MyMachine.bbmodel
REM   tools\convert.bat MyMachine.bbmodel output.anim.json
REM
REM Requires: JDK 8+ (the script compiles Aero_Convert.java on first run).
REM
REM by lucasrgt — aerocoding.dev
REM ─────────────────────────────────────────────────────────────────────

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"

if "%~1"=="" (
    echo AeroModelLib Converter
    echo.
    echo Usage: convert.bat ^<input.bbmodel^> [output.anim.json]
    echo.
    echo Converts Blockbench .bbmodel files to .anim.json format
    echo for use with AeroModelLib's animation system.
    echo.
    echo The OBJ model must be exported manually from Blockbench:
    echo   File ^> Export ^> Export OBJ Model
    echo.
    echo See README.md for the full workflow.
    exit /b 1
)

REM Compile if .class is missing or outdated
if not exist "%SCRIPT_DIR%Aero_Convert.class" (
    javac "%SCRIPT_DIR%Aero_Convert.java"
)

java -cp "%SCRIPT_DIR%." Aero_Convert %*

endlocal
