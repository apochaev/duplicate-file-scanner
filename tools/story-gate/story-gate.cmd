@echo off
rem Launcher for the story gates (cmd and PowerShell): runs StoryGate.java with a JDK 22 or later.
rem Multi-file source launch needs JDK 22+, and the java first on PATH here may be older, so JAVA_HOME wins.
rem Usage: tools\story-gate\story-gate.cmd <check> <ID> [options]   (see StoryGate.java)
setlocal

set "DIR=%~dp0"
set "JAVA=java"
set "VIA=PATH"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"
if defined JAVA_HOME set "VIA=JAVA_HOME"

set "VERSION="
for /f "tokens=1-3" %%a in ('"%JAVA%" -version 2^>^&1') do if "%%b"=="version" if not defined VERSION set "VERSION=%%~c"
if not defined VERSION goto unknown

set "MAJOR="
for /f "delims=.-_+ tokens=1" %%m in ("%VERSION%") do set "MAJOR=%%m"
if not defined MAJOR goto unknown
if %MAJOR% LSS 22 goto old

"%JAVA%" "%DIR%StoryGate.java" %*
exit /b %ERRORLEVEL%

:unknown
>&2 echo story-gate: cannot tell which Java version is in use (%VIA%). Set JAVA_HOME to a JDK 22 or later.
exit /b 2

:old
>&2 echo story-gate: JDK 22 or later is required (found %VERSION% via %VIA%). Set JAVA_HOME to a JDK 22 or later.
exit /b 2
