#!/usr/bin/env bash
# 컴파일: out/ 에 클래스 생성 (build.bat과 동일)
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac -encoding UTF-8 -d out $(find src -name '*.java')
echo "build ok"
