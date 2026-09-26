@echo off
cd /d "%~dp0"
call run-wpilib.cmd --desktop %*
exit /b %errorlevel%
