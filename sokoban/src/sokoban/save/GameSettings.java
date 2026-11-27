package sokoban.save;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 사용자 설정(Esc 설정 창). 진행 기록과 다른 파일(settings.properties)에 저장한다.
 * 파일이 없거나 손상되면 기본값으로 시작하며, 잘못된 값 하나는 그 항목만 기본값으로 대체한다.
 */
public final class GameSettings {
    public static final String[] SPEED_NAMES = {"느리게", "보통", "빠르게"};
    private static final long[] SPEED_MS = {170, 110, 70};

    private final Path file;
    private int speed = 1;
    private boolean blockFlash = true;
    private boolean layerFocusDefault = false;

    public GameSettings(Path file) {
        this.file = file;
        load();
    }

    /** 진행 기록 파일과 같은 폴더의 settings.properties. */
    public static Path defaultFile() {
        String override = System.getProperty("sokoban.settings");
        if (override != null) return Path.of(override);
        return SaveStore.defaultFile().resolveSibling("settings.properties");
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | IllegalArgumentException e) {
            return;
        }
        try {
            int s = Integer.parseInt(p.getProperty("animSpeed", "1").trim());
            if (s >= 0 && s < SPEED_MS.length) speed = s;
        } catch (NumberFormatException ignored) {
            // 기본값 유지
        }
        blockFlash = !"false".equalsIgnoreCase(p.getProperty("blockFlash", "true").trim());
        layerFocusDefault = "true".equalsIgnoreCase(p.getProperty("layerFocusDefault", "false").trim());
    }

    public int speed() {
        return speed;
    }

    /** 이동 한 칸 애니메이션 시간(ms). */
    public long animMs() {
        return SPEED_MS[speed];
    }

    public boolean blockFlash() {
        return blockFlash;
    }

    public boolean layerFocusDefault() {
        return layerFocusDefault;
    }

    public void cycleSpeed() {
        speed = (speed + 1) % SPEED_MS.length;
    }

    public void toggleBlockFlash() {
        blockFlash = !blockFlash;
    }

    public void toggleLayerFocusDefault() {
        layerFocusDefault = !layerFocusDefault;
    }

    /** 기본값으로 되돌린다(저장은 save()). */
    public void resetToDefaults() {
        speed = 1;
        blockFlash = true;
        layerFocusDefault = false;
    }

    public void save() throws IOException {
        if (file == null) return;
        Properties p = new Properties();
        p.setProperty("animSpeed", String.valueOf(speed));
        p.setProperty("blockFlash", String.valueOf(blockFlash));
        p.setProperty("layerFocusDefault", String.valueOf(layerFocusDefault));
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            p.store(w, "AstroDeck settings");
        }
    }
}
