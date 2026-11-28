package sokoban;

import java.nio.file.*;
import java.util.List;
import sokoban.config.GameConfig;
import sokoban.core.*;
import sokoban.level.LevelRepository;
import static sokoban.Check.*;

final class AndromedaTest {
    static void register() {
        test("안드로메다: 17번째 행성·수직 흡인·전원 해제·되돌리기", AndromedaTest::campaign);
        test("캠페인 순서: 지정 파일·기본 난이도 정렬·오류 시 원복", AndromedaTest::ordering);
    }

    private static void campaign() {
        var repo = LevelRepository.load(Path.of("levels"), GameConfig.defaults());
        check(repo.errors.isEmpty(), repo.errors.toString());
        Level level = repo.levels.get(16);
        eq("L17", level.id);
        eq("안드로메다 관문", level.name);
        eq(3, level.h);
        eq(1L, repo.levels.stream().filter(Level::hasMagnets).count());
        var session = new Session(level, GameConfig.defaults());
        StepResult on = session.move(Direction.RIGHT);
        notNull(on);
        check(on.state().magnetOn);
        check(on.state().isIron(level.idx(3, 2, 1)));
        check(!on.state().isIron(level.idx(3, 2, 2)));
        isNull(session.move(Direction.RIGHT)); // 붙은 철 상자는 전원을 끄기 전까지 이동 불가
        notNull(session.move(Direction.LEFT));
        StepResult off = session.move(Direction.RIGHT);
        notNull(off);
        check(!off.state().magnetOn);
        check(session.undo());
        check(session.state().magnetOn);
        check(session.redo());
        check(!session.state().magnetOn);
        session.reset();
        eq(level.initial, session.state());
    }

    private static void ordering() throws Exception {
        Path dir = Files.createTempDirectory("andromeda-order-");
        try {
            Files.writeString(dir.resolve("a.txt"), "id: A\noptimalMoves: 2\nlayer 0\n@$-.\n");
            Files.writeString(dir.resolve("b.txt"), "id: B\noptimalMoves: 4\nlayer 0\n@$---.\n");
            eq(List.of("A", "B"), ids(dir));
            Files.writeString(dir.resolve("campaign.order"), "B\nA\n");
            eq(List.of("B", "A"), ids(dir));
            for (String invalid : List.of("B\nB\n", "B\nMISSING\n")) {
                Files.writeString(dir.resolve("campaign.order"), invalid);
                var broken = LevelRepository.load(dir, GameConfig.defaults());
                check(!broken.errors.isEmpty());
                eq(List.of("A", "B"), broken.levels.stream().map(l -> l.id).toList());
            }
        } finally {
            for (String name : List.of("a.txt", "b.txt", "campaign.order")) Files.deleteIfExists(dir.resolve(name));
            Files.deleteIfExists(dir);
        }
    }

    private static List<String> ids(Path dir) {
        var repo = LevelRepository.load(dir, GameConfig.defaults());
        check(repo.errors.isEmpty(), repo.errors.toString());
        return repo.levels.stream().map(l -> l.id).toList();
    }
}
