# AstroDeck — 3D 소코반 (sokoban_ssd)

소프트웨어시스템설계 팀 프로젝트. 고전 퍼즐 게임 소코반을 **3차원 격자 + 아이소메트릭(2.5D) 화면**으로 만든 Java 게임과, 그 설계 문서(요구사항·UML)를 담은 저장소다.

## 소개

- 플레이어와 상자가 x, y, z **6방향**으로 움직인다. 중력·바닥이 없어 모든 빈 칸이 공중이다.
- 상자를 목표 칸에 모두 올리면 클리어. 구멍 칸에 상자를 밀어 넣으면 상자와 구멍이 함께 사라진다.
- 8방향(45° 단위) 회전 카메라, 층 강조 보기, 되돌리기/다시 하기/리셋, 점수와 별 3단계, 기록 저장을 지원한다.
- 레벨 20개(난이도 순 해금). 레벨은 텍스트 파일이라 추가가 쉽고, BFS 솔버가 풀이 가능 여부와 최적해를 검증한다.
- Java 17+ / Swing, **외부 라이브러리 없음**.
- 포탈 이동을 구현했으며 `level07z-portal.txt`와 `level09z-portal-box.txt`에서 플레이어·상자의 포탈 이동을 체험할 수 있다. 연쇄 밀기, 계단, 리프트, 중력은 아직 구현하지 않았다.

## 폴더 구조

| 경로 | 내용 |
|---|---|
| `sokoban/` | 게임 소스·레벨·빌드/실행 스크립트 (자세한 내용은 [sokoban/README.md](sokoban/README.md)) |
| `doc/` | UMLet 다이어그램(`.uxf`), PNG(`doc/diagram/`), 생성 스크립트(`doc/tools/`), [다이어그램 목록](doc/다이어그램_목록.md) |
| `2.5D 소코반 요구사항 명세서 ... .docx` | 요구사항 명세서 + 유스케이스 |
| `UMLet_다이어그램_작업_가이드.md` | 다이어그램 작업 방법 |

## 실행 방법

### 준비물

- JDK 17 이상 (`javac`, `java`가 PATH에 있어야 함)

### 실행

```bash
git clone https://github.com/wegoedod/sokoban_ssd.git
cd sokoban_ssd/sokoban
```

| 목적 | Windows | bash (Linux/macOS/Git Bash) |
|---|---|---|
| 컴파일 | `build.bat` | `./build.sh` |
| 실행 | `run.bat` | `./run.sh` |
| 단위 테스트 | – | `./test.sh` |
| 레벨 검증·최적해 기록 | `java -cp out sokoban.tool.LevelTool` | 〃 |

`run`은 `out/`이 없으면 자동으로 컴파일한다. 반드시 `sokoban/` 폴더에서 실행해야 `levels/`·`config/`를 찾는다
(다른 위치에서 실행하면 `-Dsokoban.home=폴더`로 지정).
기록은 `~/.sokoban25d/save.properties`에 저장된다(`-Dsokoban.save=경로`로 변경).

## 조작

| 키 | 동작 |
|---|---|
| `W` `A` `S` `D` | 평면 이동 (화면 기준: W 오른쪽 위, D 오른쪽 아래, S 왼쪽 아래, A 왼쪽 위) |
| `↑` / `↓` | 위(+z) / 아래(−z) |
| `←` / `→` | 카메라 회전 (45°씩, 8방향) |
| Z / Y | 되돌리기 / 다시 하기 |
| R | 레벨 리셋 |
| Esc | 일시정지 |
| Tab | 층 강조 |
| V | 시점 초기화 |

마우스는 메뉴 선택·호버에만 쓰인다(시점은 마우스로 돌리지 않는다).
카메라를 돌려도 이동 키는 항상 화면 기준이다.

## 문서

- 구현된 UC: UC-01 ~ UC-04, UC-10 ~ UC-19
- 클래스 다이어그램 5종, 시퀀스 다이어그램 13종 → [doc/다이어그램_목록.md](doc/다이어그램_목록.md)
- 다이어그램 재생성: `python doc/tools/gen_uc.py`, `gen_cd.py`, `gen_sd.py` 후 `python doc/tools/umlet_export.py doc/diagram doc/*.uxf`
