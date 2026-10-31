@echo off
rem Builds the CLI, then runs the scan with the arguments given to this script.
rem
rem   dfs.bat F:\Media --format=text --output=C:\reports\media.txt
rem
rem Progress is drawn live on this console (stderr). With --output=<file> the report
rem goes to that file as UTF-8 and the console shows only progress and the final
rem "Report written to <path>" line; without it the report is printed here.
setlocal

set "ROOT=%~dp0"
set "JAR=%ROOT%cli\build\libs\cli-1.0-SNAPSHOT.jar"

if "%~1"=="" (
    echo usage: %~nx0 ^<folder^> [^<folder^> ...] [--format=jsonl^|text] [--output=^<file^>] [--progress=live^|log^|off] [--min-size=^<bytes^>] [--no-default-excludes]
    exit /b 2
)

echo Building the CLI...
call "%ROOT%gradlew.bat" -q -p "%ROOT%." :cli:bootJar
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA=java"
)

rem UTF-8 console, so non-ASCII paths print correctly when the report goes to stdout.
chcp 65001 >nul

"%JAVA%" --enable-native-access=ALL-UNNAMED -jar "%JAR%" %*
exit /b %errorlevel%
