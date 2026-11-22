@echo off
chcp 65001 >nul
cd /d "%~dp0"
call build-editor.bat
if errorlevel 1 (pause & exit /b 1)
java -Dfile.encoding=UTF-8 -cp out-editor sokoban.Main
