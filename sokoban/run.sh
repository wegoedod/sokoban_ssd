#!/usr/bin/env bash
# 빌드 후 실행 (run.bat과 동일: 매번 빌드하고 실패하면 실행하지 않음)
set -e
cd "$(dirname "$0")"
./build.sh
java -Dfile.encoding=UTF-8 -cp out sokoban.Main
