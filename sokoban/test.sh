#!/usr/bin/env bash
# 테스트 컴파일 + 실행 (test.bat과 동일 흐름, 외부 라이브러리 없이 자체 러너 사용)
set -e
cd "$(dirname "$0")"
SEP=":"; case "$(uname)" in MINGW*|MSYS*|CYGWIN*) SEP=";";; esac
./build.sh
rm -rf out-test && mkdir -p out-test
javac -encoding UTF-8 -cp out -d out-test $(find test -name '*.java')

echo "[1/3] sokoban.AllTests"
java -Dfile.encoding=UTF-8 -cp "out${SEP}out-test" sokoban.AllTests

echo "[2/3] sokoban.editor.EditorSmokeTest"
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "out${SEP}out-test" sokoban.editor.EditorSmokeTest

echo "[3/3] sokoban.ui.MouseMovesTest"
java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "out${SEP}out-test" sokoban.ui.MouseMovesTest

echo "All tests passed."
