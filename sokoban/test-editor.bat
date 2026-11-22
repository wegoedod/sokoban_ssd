@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"
if not exist out-editor-test mkdir out-editor-test
(for /r src %%f in (*.java) do (
    set "editor_source=%%f"
    echo "!editor_source:\=/!"
)
for /r test %%f in (*.java) do (
    set "editor_source=%%f"
    echo "!editor_source:\=/!"
)) > editor-sources.txt
javac -encoding UTF-8 --release 17 -d out-editor-test @editor-sources.txt
set "editor_build_status=%errorlevel%"
del /q editor-sources.txt
if not "%editor_build_status%"=="0" exit /b %editor_build_status%
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp out-editor-test sokoban.editor.EditorSmokeTest
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp out-editor-test sokoban.ui.MouseMovesTest
exit /b %errorlevel%
