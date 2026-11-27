@echo off
chcp 65001 >nul
cd /d "%~dp0"

call build.bat
if errorlevel 1 exit /b 1

if not exist out-test mkdir out-test
dir /s /b test\*.java > test-sources.txt
javac -encoding UTF-8 -cp out -d out-test @test-sources.txt
del test-sources.txt
if errorlevel 1 exit /b 1

echo [1/3] sokoban.AllTests
java -Dfile.encoding=UTF-8 -cp "out;out-test" sokoban.AllTests
if errorlevel 1 exit /b 1

echo [2/3] sokoban.editor.EditorSmokeTest
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "out;out-test" sokoban.editor.EditorSmokeTest
if errorlevel 1 exit /b 1

echo [3/3] sokoban.ui.MouseMovesTest
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "out;out-test" sokoban.ui.MouseMovesTest
if errorlevel 1 exit /b 1

echo All tests passed.
exit /b 0
