@echo off
rem A stand-in for the Gradle wrapper: the Vitest report appears only when the run asks for npmTest after cleanTest.
echo %* | findstr /r /c:"cleanTest.*npmTest" >nul
if errorlevel 1 (
  echo ^> Task :server:test
  exit /b 0
)
if not exist server\build\test-results\vitest mkdir server\build\test-results\vitest
del /q server\build\test-results\vitest\*.xml 2>nul
copy /y fresh\*.xml server\build\test-results\vitest\ >nul
echo ^> Task :server:npmTest FAILED
echo * What went wrong:
echo Execution failed for task ':server:npmTest'.
exit /b 1
