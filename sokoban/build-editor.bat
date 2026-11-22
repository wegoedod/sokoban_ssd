@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"
if not exist out-editor mkdir out-editor
(for /r src %%f in (*.java) do (
    set "editor_source=%%f"
    echo "!editor_source:\=/!"
)) > editor-sources.txt
javac -encoding UTF-8 --release 17 -d out-editor @editor-sources.txt
set "editor_build_status=%errorlevel%"
del /q editor-sources.txt
exit /b %editor_build_status%
