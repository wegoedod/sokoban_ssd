package sokoban.level;

import sokoban.config.GameConfig;
import sokoban.core.Level;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** 난이도순으로 읽되 campaign.order가 있으면 행성별 캠페인 순서를 유지한다. */
public final class LevelRepository {
    public final List<Level> levels;
    public final List<Double> difficulty;
    public final List<String> errors;

    private LevelRepository(List<Level> levels, List<Double> difficulty, List<String> errors) {
        this.levels = levels;
        this.difficulty = difficulty;
        this.errors = errors;
    }

    public static LevelRepository load(Path dir, GameConfig cfg) {
        List<String> errors = new ArrayList<>();
        List<Level> loaded = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".txt")).sorted().forEach(files::add);
            } catch (IOException e) {
                errors.add(dir + ": 레벨 폴더를 읽지 못했다 (" + e.getMessage() + ")");
            }
        } else {
            errors.add(dir + ": 레벨 폴더가 없다.");
        }
        Set<String> ids = new HashSet<>();
        for (Path f : files) {
            String name = f.getFileName().toString();
            try {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                Level lv = LevelParser.parse(name, lines);
                if (lv.optimalMoves < 0) {
                    throw new LevelFormatException(name, 0, "optimalMoves가 없다. LevelTool로 검증하기 전에는 출고할 수 없다.");
                }
                if (!ids.add(lv.id)) {
                    throw new LevelFormatException(name, 0, "레벨 id '" + lv.id + "'가 중복되었다.");
                }
                loaded.add(lv);
            } catch (LevelFormatException e) {
                errors.add(e.getMessage());
            } catch (IOException e) {
                errors.add(name + ": 파일을 읽지 못했다 (" + e.getMessage() + ")");
            }
        }
        // D 오름차순, 같으면 파일 이름 순서(안정 정렬)
        List<Level> sorted = new ArrayList<>(loaded);
        sorted.sort(Comparator.comparingDouble(l -> Difficulty.score(l, cfg)));
        applyCampaignOrder(dir.resolve("campaign.order"), sorted, errors);
        List<Double> ds = new ArrayList<>();
        for (Level l : sorted) ds.add(Difficulty.score(l, cfg));
        return new LevelRepository(List.copyOf(sorted), List.copyOf(ds), List.copyOf(errors));
    }

    /** 맵 교체로 난이도가 달라져도 기존 행성과 진행 기록의 연결이 바뀌지 않는다. */
    private static void applyCampaignOrder(Path file, List<Level> levels, List<String> errors) {
        if (!Files.isRegularFile(file)) return;
        try {
            Set<String> seen = new HashSet<>();
            List<Level> ordered = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String id = line.trim();
                if (id.isEmpty() || id.startsWith("#")) continue;
                if (!seen.add(id)) throw new IOException("중복 레벨 id: " + id);
                Level level = levels.stream().filter(l -> l.id.equals(id)).findFirst().orElse(null);
                if (level == null) throw new IOException("없는 레벨 id: " + id);
                ordered.add(level);
            }
            // 새 사용자 레벨은 기존 캠페인 뒤에 난이도순으로 이어 붙인다.
            for (Level level : levels) if (!seen.contains(level.id)) ordered.add(level);
            levels.clear();
            levels.addAll(ordered);
        } catch (IOException e) {
            errors.add(file + ": 캠페인 순서를 읽지 못했다 (" + e.getMessage() + ")");
        }
    }
}
