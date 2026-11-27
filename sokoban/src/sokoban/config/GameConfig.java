package sokoban.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 설정값 (FR-5.4, UC-19). 하드코딩하지 않고 설정 파일에서 읽는다.
 * 파일이 없거나 값 형식이 틀리면 기본값으로 대체하고 경고를 남긴다.
 */
public final class GameConfig {
    /** 되돌리기 1회당 점수 페널티 κ */
    public final int kappa;
    /** ★★★ 기준 비율 */
    public final double star3Ratio;
    /** ★★ 기준 비율 */
    public final double star2Ratio;
    /** 난이도 계수 */
    public final double alpha, beta, gamma, delta;
    /** 인접 레벨 난이도 급등 한계 */
    public final double maxDeltaD;
    /** 기믹별 가중치 w_g (키: 기믹 이름) */
    public final Map<String, Double> gimmickWeights;
    /** 연쇄 밀기 최대 길이. 연쇄 기믹 도입 시 사용 (현재 미사용). */
    public final int maxChain;
    /** 설정 읽기 중 생긴 경고 */
    public final List<String> warnings;

    private GameConfig(Properties p, List<String> warnings) {
        this.warnings = warnings;
        kappa = intOf(p, "kappa", 10, 0, 100000);
        star3Ratio = doubleOf(p, "star3Ratio", 1.2, 1.0, 100);
        star2Ratio = doubleOf(p, "star2Ratio", 1.6, 1.0, 100);
        alpha = doubleOf(p, "alpha", 1.0, 0, 1e6);
        beta = doubleOf(p, "beta", 2.0, 0, 1e6);
        gamma = doubleOf(p, "gamma", 3.0, 0, 1e6);
        delta = doubleOf(p, "delta", 2.0, 0, 1e6);
        maxDeltaD = doubleOf(p, "maxDeltaD", 15.0, 0, 1e9);
        maxChain = intOf(p, "maxChain", 3, 1, 100);
        Map<String, Double> w = new LinkedHashMap<>();
        double[] defaults = {1.0, 1.0, 1.5, 1.5, 1.5, 2.0, 2.0};
        String[] names = {"direction", "stairs", "gravity", "chain", "lift", "portal", "sequence_goal"};
        for (int i = 0; i < names.length; i++) {
            w.put(names[i], doubleOf(p, "weight." + names[i], defaults[i], 0, 1e6));
        }
        gimmickWeights = Map.copyOf(w);
        if (star2Ratio < star3Ratio) {
            warnings.add("star2Ratio(" + star2Ratio + ") < star3Ratio(" + star3Ratio + "): 별 기준이 뒤집혀 있다.");
        }
    }

    public static GameConfig defaults() {
        return new GameConfig(new Properties(), new ArrayList<>());
    }

    public static GameConfig load(Path file) {
        Properties p = new Properties();
        List<String> warnings = new ArrayList<>();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException | IllegalArgumentException e) {
                warnings.add("설정 파일을 읽지 못해 기본값을 쓴다: " + file + " (" + e.getMessage() + ")");
                p.clear();
            }
        } else {
            warnings.add("설정 파일이 없어 기본값을 쓴다: " + file);
        }
        return new GameConfig(p, warnings);
    }

    public double weightOf(String gimmick) {
        return gimmickWeights.getOrDefault(gimmick, 1.0);
    }

    private int intOf(Properties p, String key, int def, int min, int max) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            int n = Integer.parseInt(v.trim());
            if (n < min || n > max) throw new NumberFormatException("범위 밖");
            return n;
        } catch (NumberFormatException e) {
            warnings.add("설정 '" + key + "' 값 '" + v + "'이 올바르지 않아 기본값 " + def + "을 쓴다.");
            return def;
        }
    }

    private double doubleOf(Properties p, String key, double def, double min, double max) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            double n = Double.parseDouble(v.trim());
            if (Double.isNaN(n) || n < min || n > max) throw new NumberFormatException("범위 밖");
            return n;
        } catch (NumberFormatException e) {
            warnings.add("설정 '" + key + "' 값 '" + v + "'이 올바르지 않아 기본값 " + def + "을 쓴다.");
            return def;
        }
    }
}
