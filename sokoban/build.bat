@echo off
chcp 65001 >nul
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
set "build_status=%errorlevel%"
del /q sources.txt
if not "%build_status%"=="0" exit /b %build_status%
echo build ok
