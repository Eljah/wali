@echo off
setlocal
cd /d "%~dp0"
where mvn >nul 2>nul
if errorlevel 1 (echo Install Maven 3.9+ and Java 21, then rerun. & exit /b 2)
call mvn -Pwpilib clean verify %*
exit /b %errorlevel%
