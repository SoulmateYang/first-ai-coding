@echo off
REM FirstCode Windows launcher.
REM Usage: firstcode.bat         (start REPL from project root)
REM        firstcode.bat --help  (forward args to java)
REM Requires: target\firstcode-0.1.0-all.jar must exist
REM           (run `mvn package` first).

REM Switch console to UTF-8 so Chinese in banner / REPL output renders correctly.
chcp 65001 >nul

setlocal

set SCRIPT_DIR=%~dp0
set JAR=%SCRIPT_DIR%target\firstcode-0.1.0-all.jar

if not exist "%JAR%" (
    echo ERROR: fat-jar not found at "%JAR%". 1>&2
    echo Run `mvn package` first. 1>&2
    exit /b 1
)

java -Dfile.encoding=UTF-8 -jar "%JAR%" %*
exit /b %ERRORLEVEL%
