@echo off
rem A stand-in for the Gradle wrapper: fresh reports appear only when the run starts with cleanTest.
echo %* | findstr /c:"cleanTest" >nul
if errorlevel 1 exit /b 1
del /q mod\build\test-results\test\*.xml
copy /y fresh\*.xml mod\build\test-results\test\ >nul
exit /b 1
