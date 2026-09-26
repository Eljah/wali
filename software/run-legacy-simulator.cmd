@echo off
cd /d "%~dp0"
java -jar dist\ubor-sim.jar --desktop
if errorlevel 1 pause
