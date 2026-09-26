@echo off
setlocal
cd /d "%~dp0"
if not exist "robot-wpilib\target\robot-wpilib-0.2.0.jar" (
  echo WPILib backend is not built. Run: mvn -Pwpilib clean verify
  exit /b 2
)
set "PATH=%CD%\robot-wpilib\target\native;%PATH%"
if "%~1"=="" (
  java "-Djava.library.path=%CD%\robot-wpilib\target\native" -cp "robot-wpilib\target\robot-wpilib-0.2.0.jar;robot-wpilib\target\lib\*" com.ubor.wpilib.WpiMain --desktop
) else (
  java "-Djava.library.path=%CD%\robot-wpilib\target\native" -cp "robot-wpilib\target\robot-wpilib-0.2.0.jar;robot-wpilib\target\lib\*" com.ubor.wpilib.WpiMain %*
)
exit /b %errorlevel%
