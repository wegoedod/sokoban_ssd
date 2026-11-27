@echo off
chcp 65001 >nul
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp out sokoban.Main
