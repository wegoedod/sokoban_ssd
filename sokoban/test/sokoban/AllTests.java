package sokoban;

import sokoban.config.GameConfig;
import sokoban.core.BlockReason;
import sokoban.core.Direction;
import sokoban.core.Engine;
import sokoban.core.GameState;
import sokoban.core.Level;
import sokoban.core.Scoring;
import sokoban.core.Session;
import sokoban.core.StepResult;
import sokoban.core.TileType;
import sokoban.level.Difficulty;
import sokoban.level.LevelFormatException;
import sokoban.level.LevelParser;
import sokoban.level.LevelRepository;
import sokoban.save.SaveStore;
import sokoban.solver.Solver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static sokoban.Check.*;

/** 외부 라이브러리 없이 돌리는 규칙 단위 테스트 (NFR-7). 실패하면 종료 코드 1. */
public final class AllTests {
    static final GameConfig CFG = GameConfig.defaults();

    public static void main(String[] args) throws Exception {
        test("직선 이동", AllTests::plainMove);
        test("벽에 막히면 변화 없음", AllTests::wallBlocks);
        test("격자 밖은 고체 (x, y, z 모두)", AllTests::gridEdgeIsSolid);
        test("플레이어가 z축으로 이동", AllTests::playerMovesVertically);
        test("블록을 위로 밀기", AllTests::pushUp);
        test("블록을 아래로 밀기", AllTests::pushDown);
        test("중력 없음: 공중 블록은 떨어지지 않음", AllTests::noGravity);
        test("z축으로도 막힘 (벽·블록·천장)", AllTests::verticalBlocking);
        test("블록을 z축으로 밀어 구멍 메우기", AllTests::verticalHoleFill);
        test("3축을 모두 쓰는 최적해와 풀이 재생", AllTests::threeAxisSolution);
        test("3D 되돌리기 복원", AllTests::undoVertical);
        test("블록 밀기", AllTests::pushBlock);
        test("블록 뒤가 벽이면 막힘", AllTests::pushIntoWall);
        test("블록 뒤에 블록이 있으면 막힘(연쇄 없음)", AllTests::noChainPush);
        test("플레이어는 구멍에 진입 불가", AllTests::playerCannotEnterHole);
        test("블록이 구멍을 메우면 소멸하고 평지가 됨", AllTests::holeFill);
        test("메워진 구멍은 걸을 수 있고 블록도 지나감", AllTests::filledHoleIsFloor);
        test("모든 목표가 차면 클리어", AllTests::clearDetection);
        test("목표 위에 이미 블록이 있는 시작", AllTests::startOnGoal);
        test("막힌 입력은 이동 횟수에 포함하지 않음", AllTests::blockedNotCounted);
        test("되돌리기 복원(밀기·구멍 포함)와 횟수", AllTests::undoRestores);
        test("다시 하기와 새 이동 시 Redo 폐기", AllTests::redoAndDiscard);
        test("처음 상태에서 되돌리기는 무시", AllTests::undoAtStart);
        test("리셋은 상태와 카운터를 초기화", AllTests::resetClears);
        test("클리어 후 입력 차단", AllTests::inputBlockedAfterClear);
        test("결정성: 같은 입력은 같은 결과", AllTests::deterministic);
        test("점수 산식 경계", AllTests::scoreBoundaries);
        test("별 기준 경계", AllTests::starBoundaries);
        test("별 기준 레벨별 덮어쓰기", AllTests::starOverride);
        test("상태 equals/hashCode", AllTests::stateEquality);
        test("솔버: 최적해와 풀이 불가", AllTests::solverBasics);
        test("솔버: 탐색 한도 초과", AllTests::solverLimit);
        test("파서: 형식 오류 위치 보고", AllTests::parserErrors);
        test("파서: 블록 수 부족 검증", AllTests::parserBlockCount);
        test("파서: 다층 격자", AllTests::parserLayers);
        test("파서: 메워진 구멍(=)은 처음부터 평지", AllTests::parserFilledHole);
        test("파서: 빙판(~) 타일", AllTests::parserIce);
        test("파서: 포탈 짝 연결(같은 층·다른 층)", AllTests::parserPortalPairs);
        test("파서: 포탈 짝이 안 맞으면 위치 보고", AllTests::parserPortalErrors);
        test("파서: pull 메타데이터", AllTests::parserPullMeta);
        test("레벨: withSolution이 포탈·pull을 보존", AllTests::withSolutionKeepsExtras);
        test("파서: 레벨 디자이너 에디터 예시 맵(2D)을 그대로 읽음", AllTests::parserDesignerSample);
        test("난이도 점수 D 계산", AllTests::difficultyScore);
        test("설정: 잘못된 값은 기본값 + 경고", AllTests::configFallback);
        test("저장: 신기록만 덮어씀", AllTests::saveOnlyImproves);
        test("저장: 파일 왕복", AllTests::saveRoundTrip);
        test("저장: 손상 파일은 초기 상태", AllTests::saveCorrupt);
        test("카메라: 기본 시점 입력 매핑", sokoban.ui.CameraTest::defaultViewIsIdentity);
        test("카메라: 8방향(45°) 시점 전부에서 키 4개가 서로 다른 방향", sokoban.ui.CameraTest::mappingIsBijectionForEveryEighthTurn);
        test("카메라: 모든 각도에서 키 4개가 서로 다른 방향", sokoban.ui.CameraTest::mappingIsBijectionAtEveryAngle);
        test("카메라: 90° 회전 시 입력이 화면 기준으로 회전", sokoban.ui.CameraTest::quarterTurnRotatesMapping);
        test("카메라: 45° 반 걸음 시점은 격자 축이 화면 축과 나란", sokoban.ui.CameraTest::eighthTurnMappingFollowsScreen);
        test("카메라: 45° 시점에서도 키와 화면 방향이 한 사분면 안쪽", sokoban.ui.CameraTest::eighthTurnMappingStaysWithinOneQuadrant);
        test("카메라: 위/아래 키는 시점과 무관", sokoban.ui.CameraTest::verticalKeysIgnoreCamera);
        test("카메라: 45° 단위 회전 애니메이션·시점 초기화", sokoban.ui.CameraTest::cameraStepsByEighthTurnAndAnimates);
        test("카메라: 탑뷰는 격자가 정사각 타일로 투영", sokoban.ui.CameraTest::topViewProjectsSquareTiles);
        test("카메라: 탑뷰에서 W/A/S/D가 화면 방향 그대로", sokoban.ui.CameraTest::topViewKeyMappingFollowsScreen);
        test("카메라: 탑뷰·기본 시점 자세 고정값", sokoban.ui.CameraTest::topViewPoseConstants);
        test("카메라: 탑뷰→3D 확장 연출 수렴", sokoban.ui.CameraTest::expansionFromTopViewAnimatesAndConverges);
        test("카메라: 시점 초기화가 기울기까지 되돌림", sokoban.ui.CameraTest::resetViewRestoresDefaultTilt);
        test("한글 입력기: 자모 키를 영문 단축키로 보정", sokoban.ui.CameraTest::koreanImeKeysMapToPhysicalKeys);
        test("출고 레벨: 1~3단계는 2D, 4단계부터 3D", AllTests::shippedLevelStructure);
        test("렌더: 맨 위층에서도 층 강조가 화면을 바꿈", sokoban.ui.RenderTest::layerFocusChangesEvenOnTopLayer);
        test("렌더: 층 강조는 위층을 흐리게 하고 끄면 복원", sokoban.ui.RenderTest::layerFocusDimsUpperLayersAndRestores);
        test("렌더: 2D 레벨은 층 강조 영향 없음", sokoban.ui.RenderTest::layerFocusDoesNothingOnFlatLevel);
        test("우주 지도: 행성이 위·아래 지그재그, 간격 일정", sokoban.ui.SpaceMapTest::zigzagLayout);
        test("우주 지도: 로켓은 목표까지 한 행성씩만 이동", sokoban.ui.SpaceMapTest::rocketMovesOneNodeAtATime);
        test("우주 지도: 이동 중 목표가 바뀌면 되돌아 뜀", sokoban.ui.SpaceMapTest::rocketReversesMidHop);
        test("우주 지도: 먼 이동은 빠르게", sokoban.ui.SpaceMapTest::farTravelIsFaster);
        test("우주 지도: 클릭 판정", sokoban.ui.SpaceMapTest::hitTest);
        test("우주 지도: 카메라 수렴(프레임 속도 무관)", sokoban.ui.SpaceMapTest::cameraConverges);
        test("우주 지도: 행성 이름(지구 시작, 모자라면 자동)", sokoban.ui.SpaceMapTest::planetNames);
        test("우주 지도: 그린 뒤 변환·클립 보존(회귀)", sokoban.ui.SpaceMapTest::drawingKeepsGraphicsState);
        test("막힘 사유: 사유별 종류와 막은 칸 좌표", AllTests::blockReasonKinds);
        test("막힘 사유: step()이 null일 때만 사유가 있다(출고 레벨 랜덤 이동)", AllTests::blockReasonMatchesStep);
        test("출고 레벨: 모두 검증 완료·풀이 재생·곡선", AllTests::shippedLevels);
        test("탑뷰 인접 칸 클릭과 무시할 영역", sokoban.ui.MouseMovesTest::flatNeighbourClicksAndIgnoredCells);
        test("8개 시점의 상하 포함 6방향 클릭", sokoban.ui.MouseMovesTest::allSixCellsAtEveryCameraAngle);
        test("막힌 칸과 상하 밀기 프리뷰", sokoban.ui.MouseMovesTest::blockedClicksAndVerticalPushes);
        test("창 크기·회전·초기화 후 클릭 영역", sokoban.ui.MouseMovesTest::resizeAnimationAndClearedTargets);
        finish();
    }

    // ---------- 도우미 ----------

    static Level lv(String... rows) {
        List<String> lines = new ArrayList<>();
        lines.add("id: T");
        lines.add("layer 0");
        lines.addAll(List.of(rows));
        try {
            return LevelParser.parse("test", lines);
        } catch (LevelFormatException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    static int at(Level l, int x, int y) {
        return l.idx(x, y, 0);
    }

    static GameState play(Level l, String keys) {
        GameState s = l.initial;
        for (char c : keys.toCharArray()) {
            StepResult r = Engine.step(l, s, Direction.fromSymbol(c));
            if (r != null) s = r.state();
        }
        return s;
    }

    // ---------- 테스트 ----------

    static void plainMove() {
        Level l = lv("#####", "#@-.#", "#-$-#", "#####");
        StepResult r = Engine.step(l, l.initial, Direction.RIGHT);
        eq(at(l, 2, 1), r.state().player);
        check(!r.pushed());
    }

    static void wallBlocks() {
        Level l = lv("####", "#@.#", "#$-#", "####");
        isNull(Engine.step(l, l.initial, Direction.UP));
        isNull(Engine.step(l, l.initial, Direction.LEFT));
    }

    /** 여러 층을 가진 레벨. 첫 인자가 맨 아래 층(z=0). 층 안의 행은 '/'로 구분. */
    static Level lvz(String... layers) {
        List<String> lines = new ArrayList<>();
        lines.add("id: T");
        for (int z = 0; z < layers.length; z++) {
            lines.add("layer " + z);
            lines.addAll(List.of(layers[z].split("/")));
        }
        try {
            return LevelParser.parse("test", lines);
        } catch (LevelFormatException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    static int at3(Level l, int x, int y, int z) {
        return l.idx(x, y, z);
    }

    static void gridEdgeIsSolid() {
        Level l = lv("@$.");
        isNull(Engine.step(l, l.initial, Direction.LEFT));
        isNull(Engine.step(l, l.initial, Direction.UP));
        isNull(Engine.step(l, l.initial, Direction.DOWN));
        isNull(Engine.step(l, l.initial, Direction.BELOW));
        isNull(Engine.step(l, l.initial, Direction.ABOVE)); // 1층짜리 격자의 천장
        // 블록 뒤가 격자 밖이면 막힘
        Level m = lv(".@$");
        isNull(Engine.step(m, m.initial, Direction.RIGHT));
    }

    static void playerMovesVertically() {
        Level l = lvz("@$.", "---", "---");
        StepResult up = Engine.step(l, l.initial, Direction.ABOVE);
        eq(at3(l, 0, 0, 1), up.state().player);
        check(!up.pushed());
        GameState top = play(l, "TT");
        eq(at3(l, 0, 0, 2), top.player);
        eq(at3(l, 0, 0, 1), play2(l, top, "B").player);
    }

    static void pushUp() {
        Level l = lvz("@", "$", "-", ".");
        StepResult r = Engine.step(l, l.initial, Direction.ABOVE);
        check(r.pushed());
        eq(at3(l, 0, 0, 1), r.state().player);
        check(r.state().hasBlock(at3(l, 0, 0, 2)));
        StepResult r2 = Engine.step(l, r.state(), Direction.ABOVE);
        check(r2.cleared());
    }

    static void pushDown() {
        Level l = lvz(".", "-", "$", "@");
        GameState s = play(l, "BB");
        check(Engine.isCleared(l, s));
        eq(at3(l, 0, 0, 1), s.player);
    }

    static void noGravity() {
        // 공중의 블록은 아무것도 받치지 않아도 떨어지지 않는다. 플레이어도 공중에 머문다.
        Level l = lvz("-.-", "---", "$@-");
        GameState s = play(l, "R");
        eq(at3(l, 2, 0, 2), s.player);
        check(s.hasBlock(at3(l, 0, 0, 2)));
        GameState t = play(l, "B");
        eq(at3(l, 1, 0, 1), t.player);   // 아래 칸이 비어 있으면 그냥 내려간다
        check(t.hasBlock(at3(l, 0, 0, 2)));
    }

    static void verticalBlocking() {
        // 블록 위가 벽
        Level a = lvz("@", "$", "#", ".");
        isNull(Engine.step(a, a.initial, Direction.ABOVE));
        // 블록 위가 다른 블록 (연쇄 없음)
        Level b = lvz("@", "$", "$", "-", ".", ".");
        isNull(Engine.step(b, b.initial, Direction.ABOVE));
        // 블록이 천장에 닿음
        Level c = lvz("@", "$", ".");
        check(Engine.step(c, c.initial, Direction.ABOVE).cleared());
        Level d = lvz("@.", "$-");
        isNull(Engine.step(d, d.initial, Direction.ABOVE));
        // 벽 속으로는 못 들어감
        Level e = lvz("@$.", "#--");
        isNull(Engine.step(e, e.initial, Direction.ABOVE));
    }

    static void verticalHoleFill() {
        Level l = lvz("@", "$", "O", ".", "$", "-");
        // 블록 둘 + 구멍 하나 + 목표 하나. 위쪽으로 첫 블록을 구멍에 밀어 넣는다.
        StepResult r = Engine.step(l, l.initial, Direction.ABOVE);
        check(r.pushed() && r.blockVanished());
        eq(0, r.state().holeCount());
        // 메워진 칸은 이제 지나다닐 수 있다
        StepResult r2 = Engine.step(l, r.state(), Direction.ABOVE);
        eq(at3(l, 0, 0, 2), r2.state().player);
    }

    static void threeAxisSolution() {
        Level l = lvz("@--", "-$-", "--.");
        Solver.Result r = Solver.solve(l, 100000, 5000);
        eq(Solver.Status.SOLVED, r.status());
        eq(5, r.moves());
        GameState s = l.initial;
        for (char c : r.path().toCharArray()) s = play2(l, s, String.valueOf(c));
        check(Engine.isCleared(l, s));
        check(r.path().contains("T"));
        check(r.path().contains("R"));
    }

    static void undoVertical() {
        Level l = lvz("@", "$", "-", ".");
        Session s = new Session(l, CFG);
        s.move(Direction.ABOVE);
        GameState one = s.state();
        s.move(Direction.ABOVE);
        check(s.isCleared());
        // 클리어 후에는 되돌릴 수 없다. 새 세션에서 확인
        Session t = new Session(l, CFG);
        t.move(Direction.ABOVE);
        t.undo();
        eq(l.initial, t.state());
        eq(0, t.moves());
        eq(1, t.undoCount());
        check(!one.equals(l.initial));
    }

    static void pushBlock() {
        Level l = lv("######", "#@$-.#", "######");
        StepResult r = Engine.step(l, l.initial, Direction.RIGHT);
        check(r.pushed());
        eq(at(l, 2, 1), r.state().player);
        check(r.state().hasBlock(at(l, 3, 1)));
        check(!r.state().hasBlock(at(l, 2, 1)));
    }

    static void pushIntoWall() {
        Level l = lv("#####", "#@$#.", "#####");
        isNull(Engine.step(l, l.initial, Direction.RIGHT));
    }

    static void noChainPush() {
        Level l = lv("#######", "#@$$-.#", "#######");
        isNull(Engine.step(l, l.initial, Direction.RIGHT));
    }

    static void playerCannotEnterHole() {
        Level l = lv("#####", "#@O.#", "#$$-#", "#####");
        isNull(Engine.step(l, l.initial, Direction.RIGHT));
    }

    static void holeFill() {
        Level l = lv("######", "#@$O.#", "#-$--#", "######");
        StepResult r = Engine.step(l, l.initial, Direction.RIGHT);
        check(r.blockVanished());
        eq(0, r.state().holeCount());
        eq(1, r.state().blockCount());
        eq(at(l, 2, 1), r.state().player);
        check(!r.state().hasBlock(at(l, 3, 1)));
    }

    static void filledHoleIsFloor() {
        Level l = lv("#######", "#@$O-.#", "#--$---#", "#######");
        GameState s = play(l, "R");               // 블록이 구멍을 메움
        eq(0, s.holeCount());
        s = play2(l, s, "R");                      // 메운 칸으로 걸어 들어감
        eq(at(l, 3, 1), s.player);
        // 메운 칸을 지나 다른 블록을 밀 수 있다
        Level l2 = lv("########", "#@$O$-.#", "########");
        GameState t = play(l2, "R");
        eq(0, t.holeCount());
        t = play2(l2, t, "RRR");
        check(t.hasBlock(at(l2, 6, 1)));
    }

    static GameState play2(Level l, GameState from, String keys) {
        GameState s = from;
        for (char c : keys.toCharArray()) {
            StepResult r = Engine.step(l, s, Direction.fromSymbol(c));
            if (r != null) s = r.state();
        }
        return s;
    }

    static void clearDetection() {
        Level l = lv("######", "#@$-.#", "######");
        StepResult r1 = Engine.step(l, l.initial, Direction.RIGHT);
        check(!r1.cleared());
        StepResult r2 = Engine.step(l, r1.state(), Direction.RIGHT);
        check(r2.cleared());
    }

    static void startOnGoal() {
        Level l = lv("#####", "#@*.#", "#-$-#", "#####");
        check(!Engine.isCleared(l, l.initial));
        eq(2, l.goals.length);
    }

    static void blockedNotCounted() {
        Level l = lv("####", "#@$.#", "####");
        Session s = new Session(l, CFG);
        isNull(s.move(Direction.UP));
        isNull(s.move(Direction.LEFT));
        eq(0, s.moves());
        notNull(s.move(Direction.RIGHT));
        eq(1, s.moves());
    }

    static void undoRestores() {
        Level l = lv("#######", "#@$O.-#", "#-$---#", "#-----#", "#######");
        Session s = new Session(l, CFG);
        s.move(Direction.RIGHT); // 블록이 구멍을 메움
        s.move(Direction.DOWN);  // 두 번째 블록을 아래로 밂
        GameState afterTwo = s.state();
        s.move(Direction.RIGHT); // 평범한 이동
        GameState afterThree = s.state();
        eq(3, s.moves());
        check(s.undo());
        eq(afterTwo, s.state());
        eq(2, s.moves());
        eq(1, s.undoCount());
        check(s.undo());
        check(s.undo());
        eq(l.initial, s.state());
        eq(0, s.moves());
        eq(3, s.undoCount());
        check(!afterThree.equals(afterTwo));
    }

    static void redoAndDiscard() {
        Level l = lv("#######", "#@-$-.#", "#######");
        Session s = new Session(l, CFG);
        s.move(Direction.RIGHT);
        s.move(Direction.RIGHT);
        GameState two = s.state();
        s.undo();
        check(s.canRedo());
        check(s.redo());
        eq(two, s.state());
        eq(2, s.moves());
        s.undo();
        check(s.canRedo());
        s.move(Direction.LEFT); // 새 이동 → Redo 폐기
        check(!s.canRedo());
        check(!s.redo());
    }

    static void undoAtStart() {
        Level l = lv("####", "#@$.#", "####");
        Session s = new Session(l, CFG);
        check(!s.undo());
        eq(0, s.undoCount());
    }

    static void resetClears() {
        Level l = lv("#####", "#@$-.#", "#####");
        Session s = new Session(l, CFG);
        s.move(Direction.RIGHT);
        s.undo();
        s.move(Direction.RIGHT);
        s.reset();
        eq(l.initial, s.state());
        eq(0, s.moves());
        eq(0, s.undoCount());
        check(!s.canUndo());
        check(!s.canRedo());
    }

    static void inputBlockedAfterClear() {
        Level l = lv("####", "#@$.#", "####");
        Session s = new Session(l, CFG);
        check(s.move(Direction.RIGHT).cleared());
        check(s.isCleared());
        isNull(s.move(Direction.LEFT));
        check(!s.undo());
    }

    static void deterministic() {
        Level l = lv("########", "#--$-O.#", "#@-$---#", "#--$---#", "########");
        String keys = "RRUURRDDLLURRDRR";
        eq(play(l, keys), play(l, keys));
    }

    static void scoreBoundaries() {
        eq(1000, Scoring.score(10, 10, 0, 10));
        eq(1000, Scoring.score(10, 5, 0, 10));   // 최적보다 적어도 1000 상한
        eq(500, Scoring.score(10, 20, 0, 10));
        eq(480, Scoring.score(10, 20, 2, 10));
        eq(0, Scoring.score(10, 1000, 50, 10));  // 음수는 0
        eq(667, Scoring.score(2, 3, 0, 10));     // round(666.67)
    }

    static void starBoundaries() {
        // optimal 10: ★★★ <= 12, ★★ <= 16
        eq(3, Scoring.stars(10, 10, 1.2, 1.6));
        eq(3, Scoring.stars(10, 12, 1.2, 1.6));
        eq(2, Scoring.stars(10, 13, 1.2, 1.6));
        eq(2, Scoring.stars(10, 16, 1.2, 1.6));
        eq(1, Scoring.stars(10, 17, 1.2, 1.6));
        // 올림: optimal 7 → 8.4 → 9
        eq(9, Scoring.limit(7, 1.2));
        eq(12, Scoring.limit(10, 1.2)); // 부동소수점 오차로 13이 되면 안 됨
        eq(3, Scoring.limit(3, 1.0));
    }

    static void starOverride() {
        List<String> lines = List.of("id: X", "star3: 1.0", "star2: 2.0", "layer 0", "####", "#@$.#", "####");
        Level l;
        try {
            l = LevelParser.parse("x", lines).withSolution(2, "RR");
        } catch (LevelFormatException e) {
            throw new AssertionError(e.getMessage());
        }
        // optimal 2, moves 3 → 기본(1.2→3)이면 ★★★이지만 덮어쓰기(1.0→2)로는 ★★
        Session s = new Session(l, CFG);
        s.move(Direction.UP);          // 막힘
        s.move(Direction.RIGHT);
        check(s.isCleared());
        eq(1, s.moves());
        eq(3, s.stars());
        Level l2 = lv("######", "#@-$.#", "######").withSolution(3, "RRR");
        eq(3, Scoring.stars(l2.optimalMoves, 3, CFG.star3Ratio, CFG.star2Ratio));
    }

    static void stateEquality() {
        GameState a = GameState.of(1, new int[]{5, 3}, new int[]{9});
        GameState b = GameState.of(1, new int[]{3, 5}, new int[]{9});
        eq(a, b);
        eq(a.hashCode(), b.hashCode());
        check(!a.equals(GameState.of(2, new int[]{3, 5}, new int[]{9})));
        check(!a.equals(GameState.of(1, new int[]{3, 5}, new int[]{})));
    }

    static void solverBasics() {
        Level l = lv("#######", "#@$-.-#", "#######");
        Solver.Result r = Solver.solve(l, 10000, 5000);
        eq(Solver.Status.SOLVED, r.status());
        eq(2, r.moves());
        eq("RR", r.path());
        // 풀이 불가: 블록이 모서리에 박혀 있다
        Level bad = lv("#####", "#$-.#", "#-@-#", "#####");
        Solver.Result r2 = Solver.solve(bad, 10000, 5000);
        eq(Solver.Status.UNSOLVABLE, r2.status());
    }

    static void solverLimit() {
        Level l = lv("#########", "#-------#", "#-.-.-.-#", "#-------#", "#-$-$-$-#", "#---@---#", "#########");
        Solver.Result r = Solver.solve(l, 50, 5000);
        eq(Solver.Status.LIMIT_EXCEEDED, r.status());
    }

    static void parserErrors() {
        expectError("알 수 없는 문자", 3, "id: A", "layer 0", "#@x.#");
        expectError("플레이어", 0, "id: A", "layer 0", "#$.#");
        expectError("목표", 0, "id: A", "layer 0", "#@$#");
        expectError("id", 0, "layer 0", "#@$.#");
        expectError("layer 번호", 2, "id: A", "layer 1", "#@$.#");
        expectError("optimalMoves", 2, "id: A", "optimalMoves: abc", "layer 0", "#@$.#");
        expectError("둘 이상", 3, "id: A", "layer 0", "#@@.#", "#$$#");
        expectError("알 수 없는 메타데이터", 2, "id: A", "foo: 1", "layer 0", "#@$.#");
    }

    static void expectError(String contains, int line, String... lines) {
        try {
            LevelParser.parse("bad.txt", List.of(lines));
            throw new AssertionError("오류가 나야 한다: " + contains);
        } catch (LevelFormatException e) {
            check(e.getMessage().contains("bad.txt"), "파일 이름 포함: " + e.getMessage());
            check(e.getMessage().contains(contains), "'" + contains + "' 포함: " + e.getMessage());
            if (line > 0) eq(line, e.line);
        }
    }

    static void parserBlockCount() {
        // 목표 1 + 구멍 1 = 2개 필요, 블록은 1개
        expectError("블록 수", 0, "id: A", "layer 0", "#@$O.#");
        // 구멍 위의 블록은 없다 (문법상 불가), 블록 겹침은 없음 — 정상 케이스
        lv("#@$O$.#");
    }

    static void parserLayers() {
        List<String> lines = List.of("id: M", "layer 0", "###", "#@#", "###", "layer 1", "#$.", "   ", "   ");
        try {
            Level l = LevelParser.parse("m", lines);
            eq(2, l.h);
            eq(3, l.w);
            eq(3, l.d);
            eq(2, l.usedLayers());
            check(l.initial.hasBlock(l.idx(1, 0, 1)));
        } catch (LevelFormatException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    static void difficultyScore() {
        Level l = lv("#####", "#@$.#", "#####").withSolution(1, "R");
        // α·1 + β·1 + γ·0 + δ·0
        near(CFG.alpha * 1 + CFG.beta * 1, Difficulty.score(l, CFG));
        List<String> lines = List.of("id: M", "layer 0", "####", "#@.#", "####", "layer 1", "#$-#", "####", "####");
        try {
            Level m = LevelParser.parse("m", lines).withSolution(5, "");
            near(CFG.alpha * 5 + CFG.beta * 1 + CFG.delta * 1, Difficulty.score(m, CFG));
        } catch (LevelFormatException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    // ---------- 타일 확장 (레벨 디자인: =, ~, A/B, pull) ----------

    static Level parseLines(String... lines) {
        try {
            return LevelParser.parse("test", List.of(lines));
        } catch (LevelFormatException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    static void parserFilledHole() {
        Level l = lv("######", "#@=$.#", "######");
        eq(TileType.HOLE, l.tile(at(l, 2, 1)));
        eq(0, l.holeCount());                       // '='는 남은 구멍이 아니다
        check(!l.initial.isHole(at(l, 2, 1)));
        StepResult r = Engine.step(l, l.initial, Direction.RIGHT);
        notNull(r, "플레이어는 메워진 구멍을 걷는다");
        eq(at(l, 2, 1), r.state().player);

        // 블록도 메워진 구멍을 지나간다(소멸하지 않는다)
        Level m = lv("######", "#@$=.#", "######");
        GameState s = play(m, "R");
        check(s.hasBlock(at(m, 3, 1)));
        eq(0, s.holeCount());
        check(Engine.isCleared(m, play(m, "RR")));

        // 열린 구멍('O')과 섞여 있어도 열린 것만 셌다
        Level mixed = lv("#######", "#@$O=$.#", "#######");
        eq(1, mixed.holeCount());
    }

    static void parserIce() {
        Level l = lv("######", "#@~$.#", "######");
        eq(TileType.ICE, l.tile(at(l, 2, 1)));
        // 플레이어는 빙판을 평범하게 걷는다 (미끄러짐은 상자만, 엔진 PR에서 구현)
        StepResult r = Engine.step(l, l.initial, Direction.RIGHT);
        notNull(r);
        eq(at(l, 2, 1), r.state().player);
    }

    static void parserPortalPairs() {
        Level l = lv("##########", "#@$A-A-.#", "##########");
        int a1 = at(l, 3, 1), a2 = at(l, 5, 1);
        eq(TileType.PORTAL, l.tile(a1));
        eq(TileType.PORTAL, l.tile(a2));
        eq(a2, l.portalPartner(a1));
        eq(a1, l.portalPartner(a2));
        eq('A', l.portalId(a1));
        eq(-1, l.portalPartner(at(l, 1, 1)));       // 포탈이 아닌 칸
        eq(0, (int) l.portalId(at(l, 1, 1)));

        // 레벨에 포탈이 없으면 모든 칸이 -1/0
        Level none = lv("#####", "#@$.#", "#####");
        eq(-1, none.portalPartner(at(none, 1, 1)));
        eq(0, (int) none.portalId(at(none, 1, 1)));

        // A와 B 두 쌍, 짝이 서로 다른 층에 있어도 된다
        Level z = lvz("#####/#@AB#/#$.##", "#####/#B--#/#A--#");
        int a0 = z.idx(2, 1, 0), aUp = z.idx(1, 2, 1);
        int b0 = z.idx(3, 1, 0), bUp = z.idx(1, 1, 1);
        eq(aUp, z.portalPartner(a0));
        eq(a0, z.portalPartner(aUp));
        eq(bUp, z.portalPartner(b0));
        eq(b0, z.portalPartner(bUp));
        eq('A', z.portalId(a0));
        eq('B', z.portalId(bUp));
    }

    static void parserPortalErrors() {
        // 한 칸뿐인 포탈: 그 칸이 있는 줄을 보고한
        expectError("포탈 'A'", 3, "id: A", "layer 0", "#@$A.#");
        // 세 칸: 현재 칸 수를 함께 보고
        expectError("현재 3칸", 3, "id: A", "layer 0", "#@$AAA.#");
        // A는 짝이 맞고 B만 한 칸: B를 탓한다
        expectError("포탈 'B'", 4, "id: A", "layer 0", "#@$A.A#", "#B###");
    }

    static void parserPullMeta() {
        eq(false, (Object) lv("#####", "#@$.#", "#####").pullAllowed);   // 기본값
        check(parseLines("id: A", "pull: true", "layer 0", "#@$.#").pullAllowed);
        check(parseLines("id: A", "pull: TRUE", "layer 0", "#@$.#").pullAllowed);
        check(!parseLines("id: A", "pull: false", "layer 0", "#@$.#").pullAllowed);
        expectError("pull", 2, "id: A", "pull: maybe", "layer 0", "#@$.#");
        expectError("중복", 3, "id: A", "pull: true", "pull: false", "layer 0", "#@$.#");
    }

    static void withSolutionKeepsExtras() {
        Level l = parseLines("id: A", "pull: true", "layer 0", "#@$A.A#")
                .withSolution(3, "RRR");
        check(l.pullAllowed);
        eq(l.idx(5, 0, 0), l.portalPartner(l.idx(3, 0, 0)));
        eq(l.idx(3, 0, 0), l.portalPartner(l.idx(5, 0, 0)));
        eq(3, l.optimalMoves);
    }

    /** 레벨 디자이너 문서의 에디터 변환 예시 맵이 그대로 읽히는지 (2D 맵 = layer 0 한 층). */
    static void parserDesignerSample() {
        Level l = lv(
                "###########",
                "###-$######",
                "###--######",
                "#@---~O~.-#",
                "###--######",
                "####$######",
                "###########");
        eq(11, l.w);
        eq(7, l.d);
        eq(1, l.h);
        eq(1, l.usedLayers());
        eq(at(l, 1, 3), l.initial.player);
        eq(2, l.blockCount());
        eq(1, l.holeCount());
        eq(1, l.goals.length);
        eq(TileType.ICE, l.tile(at(l, 5, 3)));
        eq(TileType.HOLE, l.tile(at(l, 6, 3)));
        eq(TileType.ICE, l.tile(at(l, 7, 3)));
        eq(TileType.GOAL, l.tile(at(l, 8, 3)));
    }

    static void configFallback() throws IOException {
        Path f = Files.createTempFile("cfg", ".properties");
        Files.writeString(f, "kappa=abc\nstar3Ratio=0.5\nalpha=2.5\n", StandardCharsets.UTF_8);
        GameConfig c = GameConfig.load(f);
        eq(10, c.kappa);
        near(1.2, c.star3Ratio);
        near(2.5, c.alpha);
        check(c.warnings.size() >= 2);
        GameConfig missing = GameConfig.load(f.resolveSibling("nope-" + System.nanoTime()));
        eq(10, missing.kappa);
        check(!missing.warnings.isEmpty());
        Files.delete(f);
    }

    static void saveOnlyImproves() throws IOException {
        Path dir = Files.createTempDirectory("sv");
        SaveStore s = new SaveStore(dir.resolve("s.properties"));
        check(!s.cleared("A"));
        check(s.update("A", 500, 2, 20));
        check(s.cleared("A"));
        check(!s.update("A", 400, 1, 25));            // 전부 나쁨 → 갱신 없음
        eq(500, s.get("A").bestScore());
        eq(2, s.get("A").bestStars());
        eq(20, s.get("A").bestMoves());
        check(s.update("A", 600, 2, 30));              // 점수만 좋음
        eq(600, s.get("A").bestScore());
        eq(20, s.get("A").bestMoves());                // 이동 횟수는 덮어쓰지 않음
        check(s.update("A", 0, 1, 15));                // 이동 횟수만 좋음
        eq(15, s.get("A").bestMoves());
        eq(600, s.get("A").bestScore());
    }

    static void saveRoundTrip() throws IOException {
        Path dir = Files.createTempDirectory("sv");
        Path f = dir.resolve("sub").resolve("s.properties");
        SaveStore s = new SaveStore(f);
        s.update("L01", 900, 3, 4);
        s.update("L02", 100, 1, 40);
        s.save();
        check(!Files.exists(f.resolveSibling("s.properties.tmp")), "임시 파일 정리");
        SaveStore t = new SaveStore(f);
        eq(900, t.get("L01").bestScore());
        eq(3, t.get("L01").bestStars());
        eq(40, t.get("L02").bestMoves());
        check(t.loadWarning() == null);
    }

    static void saveCorrupt() throws IOException {
        Path dir = Files.createTempDirectory("sv");
        Path f = dir.resolve("s.properties");
        Files.writeString(f, "level.A.stars=banana\nlevel.B.stars=2\nlevel.B.score=77\nlevel.B.moves=9\n\\uZZ garbage", StandardCharsets.UTF_8);
        SaveStore s = new SaveStore(f);    // 예외 없이 시작
        check(!s.cleared("A"));
        // 파일 전체를 못 읽는 경우도 예외 없음
        Files.write(f, new byte[]{(byte) 0xff, (byte) 0xfe, 0, 1, 2});
        SaveStore s2 = new SaveStore(f);
        check(!s2.cleared("B"));
    }

    /** 1~3단계는 2D(한 층) 탑뷰 레벨이고, 4단계부터 3D(여러 층) 레벨이다. 탑뷰→3D 확장 연출은 4단계에서 처음 나온다. */
    static void shippedLevelStructure() {
        GameConfig cfg = GameConfig.load(Path.of("config", "game.properties"));
        LevelRepository repo = LevelRepository.load(Path.of("levels"), cfg);
        check(repo.errors.isEmpty(), "레벨 오류: " + repo.errors);
        eq(18, repo.levels.size(), "출고 레벨 수");
        int first3D = -1;
        for (int i = 0; i < repo.levels.size(); i++) {
            Level l = repo.levels.get(i);
            eq(String.format("L%02d", i + 1), l.id, (i + 1) + "번째 레벨 id");
            if (i < 3) {
                eq(1, l.h, l.id + " 는 한 층(2D)이어야 한다");
                eq(1, l.usedLayers(), l.id + " 사용 층 수");
            } else {
                check(l.h > 1, l.id + " 는 여러 층(3D)이어야 한다");
                if (first3D < 0) first3D = i;
            }
            if (i > 0) {
                check(Difficulty.score(l, cfg) >= Difficulty.score(repo.levels.get(i - 1), cfg),
                        l.id + " 난이도 순서 (D 비감소)");
            }
        }
        eq(3, first3D, "첫 3D 레벨은 4단계");
    }

    // ---------- 막힘 사유 (UI가 막은 칸을 강조하는 데 쓴다) ----------

    static void assertReason(Level l, Direction d, BlockReason.Kind kind, int x, int y, int z) {
        isNull(Engine.step(l, l.initial, d));   // 실제로 막히는 입력에 대해서만 판단한다
        BlockReason r = Engine.whyBlocked(l, l.initial, d);
        notNull(r, d + " 사유가 있어야 한다");
        eq(kind, r.kind());
        eq(x, r.x(), "x");
        eq(y, r.y(), "y");
        eq(z, r.z(), "z");
    }

    static void blockReasonKinds() {
        // 벽
        Level wall = lv("######", "#@#$.#", "######");
        assertReason(wall, Direction.RIGHT, BlockReason.Kind.WALL, 2, 1, 0);
        assertReason(wall, Direction.UP, BlockReason.Kind.WALL, 1, 0, 0);
        // 격자 밖 (좌표는 격자 밖 칸): 한 층짜리에서 z 방향도 격자 밖이다
        Level edge = lv("@$.");
        assertReason(edge, Direction.LEFT, BlockReason.Kind.EDGE, -1, 0, 0);
        assertReason(edge, Direction.UP, BlockReason.Kind.EDGE, 0, -1, 0);
        assertReason(edge, Direction.ABOVE, BlockReason.Kind.EDGE, 0, 0, 1);
        assertReason(edge, Direction.BELOW, BlockReason.Kind.EDGE, 0, 0, -1);
        // 열린 구멍은 플레이어가 들어갈 수 없다
        Level hole = lv("@O$$.");
        assertReason(hole, Direction.RIGHT, BlockReason.Kind.HOLE, 1, 0, 0);
        // 상자 뒤가 벽 / 격자 밖 / 다른 상자
        assertReason(lv("@$#."), Direction.RIGHT, BlockReason.Kind.PUSH_WALL, 2, 0, 0);
        assertReason(lv("$@."), Direction.LEFT, BlockReason.Kind.PUSH_EDGE, -1, 0, 0);
        assertReason(lv("@$$."), Direction.RIGHT, BlockReason.Kind.PUSH_BOX, 2, 0, 0);
        // 막히지 않는 입력(빈 칸, 밀 수 있는 상자, 닫히지 않은 구멍에 밀기)은 사유가 없다
        Level free = lv("@$-.");
        notNull(Engine.step(free, free.initial, Direction.RIGHT));
        isNull(Engine.whyBlocked(free, free.initial, Direction.RIGHT));
        Level fill = lv("@$O$.");
        notNull(Engine.step(fill, fill.initial, Direction.RIGHT));
        isNull(Engine.whyBlocked(fill, fill.initial, Direction.RIGHT));
    }

    /**
     * UI 안내가 엔진 규칙과 어긋나지 않는지: 모든 출고 레벨에서 랜덤하게 움직이며 매 상태의 여섯 방향에 대해
     * step()이 null ⇔ whyBlocked()가 있음 이어야 한다. 새 기믹이 막힘 조건을 늘리면 여기서 잡힌다.
     */
    static void blockReasonMatchesStep() {
        GameConfig cfg = GameConfig.load(Path.of("config", "game.properties"));
        LevelRepository repo = LevelRepository.load(Path.of("levels"), cfg);
        Random rnd = new Random(20261123L);
        int checks = 0, blockedCount = 0, openCount = 0;
        for (Level l : repo.levels) {
            GameState s = l.initial;
            for (int step = 0; step < 400; step++) {
                for (Direction d : Direction.values()) {
                    boolean blocked = Engine.step(l, s, d) == null;
                    boolean hasReason = Engine.whyBlocked(l, s, d) != null;
                    eq(blocked, hasReason, l.id + " step=" + step + " " + d + " (state " + s + ")");
                    checks++;
                    if (blocked) blockedCount++; else openCount++;
                }
                Direction go = Direction.values()[rnd.nextInt(6)];
                StepResult r = Engine.step(l, s, go);
                if (r != null) s = r.state();
            }
        }
        check(checks >= 6 * 400 * repo.levels.size(), "검사 횟수 " + checks);
        check(blockedCount > 0 && openCount > 0, "막힘 " + blockedCount + " / 통과 " + openCount);
    }

    static void shippedLevels() {
        GameConfig cfg = GameConfig.load(Path.of("config", "game.properties"));
        LevelRepository repo = LevelRepository.load(Path.of("levels"), cfg);
        check(repo.errors.isEmpty(), "레벨 오류: " + repo.errors);
        check(repo.levels.size() >= 10, "레벨 수 " + repo.levels.size());
        double prev = -1;
        for (int i = 0; i < repo.levels.size(); i++) {
            Level l = repo.levels.get(i);
            // 저장된 풀이를 엔진으로 재생하면 정확히 optimalMoves 수로 클리어한다
            Session s = new Session(l, cfg);
            for (char c : l.solution.toCharArray()) {
                notNull(s.move(Direction.fromSymbol(c)), l.id + " 풀이 재생 중 막힘");
            }
            check(s.isCleared(), l.id + " 풀이로 클리어");
            eq(l.optimalMoves, s.moves(), l.id + " optimalMoves");
            eq(3, s.stars(), l.id + " 최적 풀이는 ★★★");
            eq(1000, s.score(), l.id + " 최적 풀이는 1000점");
            // 솔버가 같은 값을 낸다
            Solver.Result r = Solver.solve(l, 3_000_000, 60_000);
            eq(Solver.Status.SOLVED, r.status(), l.id);
            eq(l.optimalMoves, r.moves(), l.id + " 솔버 일치");
            // 난이도 순 정렬
            double d = repo.difficulty.get(i);
            check(d >= prev, "D 비감소: " + l.id);
            prev = d;
        }
    }
}
