package sokoban.save;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 레벨별 기록 저장 (FR-4.6, FR-7.1, FR-7.2, UC-15).
 * 최고 점수 / 최다 별 / 최소 이동 횟수를 각각 갱신될 때만 덮어쓴다.
 * 파일이 없거나 손상되어도 빈 기록으로 시작한다. 저장은 임시 파일에 쓴 뒤 교체한다.
 */
public final class SaveStore {
    public record Record(int bestScore, int bestStars, int bestMoves) {
        static final Record NONE = new Record(0, 0, 0);
    }

    private final Path file;
    private final Map<String, Record> records = new HashMap<>();
    private String loadWarning;

    public SaveStore(Path file) {
        this.file = file;
        load();
    }

    public static Path defaultFile() {
        String override = System.getProperty("sokoban.save");
        if (override != null) return Path.of(override);
        return Path.of(System.getProperty("user.home"), ".sokoban25d", "save.properties");
    }

    /** 불러오기 중 문제가 있었으면 메시지, 없으면 null. */
    public String loadWarning() {
        return loadWarning;
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | IllegalArgumentException e) {
            loadWarning = "저장 파일이 손상되어 초기 상태로 시작한다: " + e.getMessage();
            return;
        }
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith("level.") || !key.endsWith(".stars")) continue;
            String id = key.substring(6, key.length() - 6);
            try {
                int stars = Integer.parseInt(p.getProperty("level." + id + ".stars").trim());
                int score = Integer.parseInt(p.getProperty("level." + id + ".score", "0").trim());
                int moves = Integer.parseInt(p.getProperty("level." + id + ".moves", "0").trim());
                if (stars < 0 || stars > 3 || score < 0 || moves < 0) throw new NumberFormatException();
                records.put(id, new Record(score, stars, moves));
            } catch (NumberFormatException e) {
                loadWarning = "저장 파일의 일부 기록이 올바르지 않아 무시했다: " + id;
            }
        }
    }

    public Record get(String levelId) {
        return records.getOrDefault(levelId, Record.NONE);
    }

    public boolean cleared(String levelId) {
        return get(levelId).bestStars() >= 1;
    }

    /**
     * 클리어 기록을 반영한다.
     *
     * @return 하나라도 갱신되었으면 true (신기록)
     */
    public boolean update(String levelId, int score, int stars, int moves) {
        Record old = get(levelId);
        boolean first = old.bestStars() == 0;
        int s = Math.max(old.bestScore(), score);
        int st = Math.max(old.bestStars(), stars);
        int m = first ? moves : Math.min(old.bestMoves(), moves);
        Record now = new Record(s, st, m);
        if (now.equals(old)) return false;
        records.put(levelId, now);
        return true;
    }

    /** 모든 레벨 기록을 지운다(저장은 save()). 설정 창의 "진행 기록 초기화"가 쓴다. */
    public void clearAll() {
        records.clear();
    }

    /** 임시 파일에 쓴 뒤 교체한다. 실패하면 예외 (게임 진행은 막지 않는다). */
    public void save() throws IOException {
        Properties p = new Properties();
        for (Map.Entry<String, Record> e : records.entrySet()) {
            p.setProperty("level." + e.getKey() + ".stars", String.valueOf(e.getValue().bestStars()));
            p.setProperty("level." + e.getKey() + ".score", String.valueOf(e.getValue().bestScore()));
            p.setProperty("level." + e.getKey() + ".moves", String.valueOf(e.getValue().bestMoves()));
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            p.store(w, "2.5D Sokoban save");
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
