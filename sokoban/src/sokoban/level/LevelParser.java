package sokoban.level;

import sokoban.core.Direction;
import sokoban.core.GameState;
import sokoban.core.Level;
import sokoban.core.TileType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 레벨 텍스트 파서 (FR-5.1).
 *
 * <pre>
 * // 주석
 * id: 01
 * name: 첫 걸음
 * optimalMoves: 2        (LevelTool이 기록)
 * solution: RR           (LevelTool이 기록)
 * star3: 1.2             (선택, 별 기준 덮어쓰기)
 * star2: 1.6             (선택)
 * pull: true              (선택, 상자 당기기 허용. 기본 false)
 * layer 0
 * @$-.
 * </pre>
 *
 * 문자: {@code #}벽 {@code -}(또는 공백)빈 공간 {@code .}목표 {@code O}구멍 {@code =}메워진 구멍(처음부터 평지)
 * {@code ~}빙판 {@code A}{@code B}포탈 {@code $}블록 {@code *}목표 위 블록
 * {@code @}플레이어 {@code +}목표 위 플레이어, {@code 1}~{@code 9}순서 열쇠(번호), {@code K}순서 열쇠 1(단일 열쇠).
 * 층은 {@code layer N}으로 나눈다(0부터 연속, 0이 맨 아래). 격자 밖은 고체다.
 *
 * <p>포탈은 문자마다 정확히 두 칸이 있어야 한다(레벨 디자인 M-04). 두 칸은 서로의 짝이며 같은 층일 필요는 없다.
 * 레벨 에디터가 내보내는 2D 맵은 {@code layer 0} 한 층짜리 레벨로 그대로 읽힌다.
 *
 * <p>순서 열쇠: 번호는 1부터 빠짐없이 이어져야 하고(예: 1, 2, 3) 중복될 수 없다. 열쇠가 있는 레벨은
 * 모든 열쇠를 순서대로 얻기 전까지 목표가 잠기므로, 목표 위에 플레이어({@code +})나 블록({@code *})을 두고
 * 시작할 수 없다. {@code K}는 {@code 1}과 같은 열쇠다.
 */
public final class LevelParser {
    private LevelParser() {
    }

    public static Level parse(String source, List<String> lines) throws LevelFormatException {
        Map<String, String> meta = new HashMap<>();
        Map<String, Integer> metaLine = new HashMap<>();
        List<List<String>> layerRows = new ArrayList<>();
        List<Integer> layerFirstLine = new ArrayList<>();
        List<String> cur = null;

        for (int i = 0; i < lines.size(); i++) {
            int ln = i + 1;
            String raw = stripCr(lines.get(i));
            if (raw.startsWith("//")) continue;
            if (raw.startsWith("layer")) {
                String num = raw.substring(5).trim();
                int expect = layerRows.size();
                int n;
                try {
                    n = Integer.parseInt(num);
                } catch (NumberFormatException e) {
                    throw new LevelFormatException(source, ln, "layer 번호가 올바르지 않다: '" + raw + "'");
                }
                if (n != expect) {
                    throw new LevelFormatException(source, ln, "layer 번호는 0부터 연속이어야 한다 (기대 " + expect + ", 실제 " + n + ")");
                }
                cur = new ArrayList<>();
                layerRows.add(cur);
                layerFirstLine.add(ln);
                continue;
            }
            if (cur == null) {
                if (raw.isBlank()) continue;
                int c = raw.indexOf(':');
                if (c <= 0) {
                    throw new LevelFormatException(source, ln, "메타데이터는 'key: value' 형식이어야 한다: '" + raw + "'");
                }
                String key = raw.substring(0, c).trim();
                if (meta.containsKey(key)) {
                    throw new LevelFormatException(source, ln, "메타데이터 '" + key + "'가 중복되었다.");
                }
                meta.put(key, raw.substring(c + 1).trim());
                metaLine.put(key, ln);
            } else {
                cur.add(raw);
                // 줄 번호 보존을 위해 빈 줄도 그대로 보관
            }
        }
        if (layerRows.isEmpty()) {
            throw new LevelFormatException(source, 0, "layer 0 격자가 없다.");
        }
        // 층 끝의 빈 줄 제거
        for (List<String> rows : layerRows) {
            while (!rows.isEmpty() && rows.get(rows.size() - 1).isBlank()) rows.remove(rows.size() - 1);
        }

        String id = meta.get("id");
        if (id == null || id.isEmpty()) throw new LevelFormatException(source, 0, "필수 메타데이터 'id'가 없다.");
        String name = meta.getOrDefault("name", id);

        int opt = -1;
        if (meta.containsKey("optimalMoves")) {
            opt = intMeta(source, meta, metaLine, "optimalMoves", 1);
        }
        String solution = meta.getOrDefault("solution", "");
        for (int i = 0; i < solution.length(); i++) {
            if (Direction.symbols().indexOf(solution.charAt(i)) < 0) {
                throw new LevelFormatException(source, metaLine.get("solution"), "solution에 U/R/D/L/T/B 외 문자가 있다: '" + solution.charAt(i) + "'");
            }
        }
        Double s3 = ratioMeta(source, meta, metaLine, "star3");
        Double s2 = ratioMeta(source, meta, metaLine, "star2");
        if (s3 != null && s2 != null && s2 < s3) {
            throw new LevelFormatException(source, metaLine.get("star2"), "star2는 star3 이상이어야 한다.");
        }
        boolean pull = boolMeta(source, meta, metaLine, "pull", false);
        Set<String> known = Set.of("id", "name", "optimalMoves", "solution", "star3", "star2", "pull");
        for (String k : meta.keySet()) {
            if (!known.contains(k)) {
                throw new LevelFormatException(source, metaLine.get(k), "알 수 없는 메타데이터 '" + k + "'");
            }
        }

        int h = layerRows.size();
        int d = 0, w = 0;
        for (List<String> rows : layerRows) {
            d = Math.max(d, rows.size());
            for (String r : rows) w = Math.max(w, r.length());
        }
        if (w == 0 || d == 0) throw new LevelFormatException(source, 0, "격자가 비어 있다.");

        TileType[] tiles = new TileType[w * d * h];
        java.util.Arrays.fill(tiles, TileType.EMPTY);
        List<Integer> blocks = new ArrayList<>();
        List<Integer> holes = new ArrayList<>();
        int player = -1;
        int goalCount = 0;
        // 순서 열쇠: 셀 인덱스별 번호(0=열쇠 아님) / 번호 → 처음 나온 줄(중복·빠진 번호 보고용)
        int[] keyOrder = new int[tiles.length];
        Map<Integer, Integer> keyLine = new HashMap<>();
        int blockOnGoalLine = -1, playerOnGoalLine = -1;
        // 포탈: 문자 → 셀 목록 / 처음 나온 줄 번호(오류 보고용)
        Map<Character, List<Integer>> portalCells = new TreeMap<>();
        Map<Character, Integer> portalFirstLine = new HashMap<>();

        for (int z = 0; z < h; z++) {
            List<String> rows = layerRows.get(z);
            int firstLine = layerFirstLine.get(z);
            for (int y = 0; y < rows.size(); y++) {
                String row = rows.get(y);
                int ln = firstLine + 1 + y;
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    int idx = (z * d + y) * w + x;
                    TileType t;
                    boolean block = false, pl = false, filledHole = false;
                    switch (c) {
                        case ' ', '-' -> t = TileType.EMPTY;
                        case '#' -> t = TileType.WALL;
                        case '.' -> t = TileType.GOAL;
                        case 'O' -> t = TileType.HOLE;
                        case '=' -> { t = TileType.HOLE; filledHole = true; }
                        case '~' -> t = TileType.ICE;
                        case 'A', 'B' -> t = TileType.PORTAL;
                        case '$' -> { t = TileType.EMPTY; block = true; }
                        case '*' -> { t = TileType.GOAL; block = true; }
                        case '@' -> { t = TileType.EMPTY; pl = true; }
                        case '+' -> { t = TileType.GOAL; pl = true; }
                        case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> { t = TileType.EMPTY; keyOrder[idx] = c - '0'; }
                        case 'K' -> { t = TileType.EMPTY; keyOrder[idx] = 1; }
                        default -> throw new LevelFormatException(source, ln,
                                "알 수 없는 문자 '" + c + "' (x=" + x + ", y=" + y + ", layer " + z + ")");
                    }
                    tiles[idx] = t;
                    if (t == TileType.GOAL) goalCount++;
                    if (keyOrder[idx] > 0) {
                        Integer prev = keyLine.putIfAbsent(keyOrder[idx], ln);
                        if (prev != null) {
                            throw new LevelFormatException(source, ln, "순서 열쇠 " + keyOrder[idx]
                                    + "번이 두 번 나왔다 (처음: " + prev + "번째 줄). 'K'는 1번 열쇠와 같다.");
                        }
                    }
                    if (t == TileType.GOAL && block) blockOnGoalLine = ln;
                    if (t == TileType.GOAL && pl) playerOnGoalLine = ln;
                    if (t == TileType.HOLE && !filledHole) holes.add(idx);
                    if (t == TileType.PORTAL) {
                        portalCells.computeIfAbsent(c, k -> new ArrayList<>()).add(idx);
                        portalFirstLine.putIfAbsent(c, ln);
                    }
                    if (block) blocks.add(idx);
                    if (pl) {
                        if (player >= 0) {
                            throw new LevelFormatException(source, ln, "플레이어가 둘 이상이다.");
                        }
                        player = idx;
                    }
                }
            }
        }
        if (player < 0) throw new LevelFormatException(source, 0, "플레이어('@' 또는 '+')가 없다.");
        if (goalCount == 0) throw new LevelFormatException(source, 0, "목표 지점('.')이 없다.");
        if (!keyLine.isEmpty()) {
            for (int o = 1; o <= keyLine.size(); o++) {
                if (!keyLine.containsKey(o)) {
                    throw new LevelFormatException(source, 0, "순서 열쇠 번호는 1부터 "
                            + keyLine.size() + "까지 빠짐없이 이어져야 한다 (없는 번호 " + o + ").");
                }
            }
            if (blockOnGoalLine > 0) {
                throw new LevelFormatException(source, blockOnGoalLine,
                        "순서 열쇠가 있는 레벨에서는 목표 위에 블록('*')을 두고 시작할 수 없다. 열쇠를 다 모아야 목표가 열린다.");
            }
            if (playerOnGoalLine > 0) {
                throw new LevelFormatException(source, playerOnGoalLine,
                        "순서 열쇠가 있는 레벨에서는 플레이어('+')가 목표 위에서 시작할 수 없다. 열쇠를 다 모아야 목표가 열린다.");
            }
        }
        for (Map.Entry<Character, List<Integer>> e : portalCells.entrySet()) {
            if (e.getValue().size() != 2) {
                throw new LevelFormatException(source, portalFirstLine.get(e.getKey()),
                        "포탈 '" + e.getKey() + "'는 정확히 두 칸이어야 한다 (현재 " + e.getValue().size() + "칸).");
            }
        }
        if (blocks.size() < goalCount + holes.size()) {
            throw new LevelFormatException(source, 0, "블록 수(" + blocks.size() + ")가 목표 수(" + goalCount
                    + ") + 구멍 수(" + holes.size() + ")보다 적어 풀 수 없다.");
        }

        int[] b = blocks.stream().mapToInt(Integer::intValue).toArray();
        int[] hs = holes.stream().mapToInt(Integer::intValue).toArray();
        Set<Integer> bs = new HashSet<>(blocks);
        if (bs.size() != b.length) throw new LevelFormatException(source, 0, "블록이 겹쳐 있다.");

        GameState init = GameState.of(player, b, hs);
        for (int hIdx : hs) {
            if (bs.contains(hIdx)) throw new LevelFormatException(source, 0, "구멍 위에 블록이 놓여 있다.");
        }

        int[] partner = null;
        char[] portalChar = null;
        if (!portalCells.isEmpty()) {
            partner = new int[tiles.length];
            portalChar = new char[tiles.length];
            java.util.Arrays.fill(partner, -1);
            for (Map.Entry<Character, List<Integer>> e : portalCells.entrySet()) {
                int first = e.getValue().get(0), second = e.getValue().get(1);
                partner[first] = second;
                partner[second] = first;
                portalChar[first] = e.getKey();
                portalChar[second] = e.getKey();
            }
        }
        // 난이도 D(FR-5.3)의 기믹 항: 포탈은 쌍의 수, 순서 열쇠는 열쇠 수를 인스턴스 수로 센다
        Map<String, Integer> gimmicks = new LinkedHashMap<>();
        if (!portalCells.isEmpty()) gimmicks.put("portal", portalCells.size());
        if (!keyLine.isEmpty()) gimmicks.put("sequence_key", keyLine.size());
        int[] keys = keyLine.isEmpty() ? null : keyOrder;
        return new Level(id, name, w, d, h, tiles, init, opt, solution, s3, s2, gimmicks, source,
                pull, partner, portalChar, keys);
    }

    private static boolean boolMeta(String source, Map<String, String> meta, Map<String, Integer> metaLine,
                                    String key, boolean def) throws LevelFormatException {
        String v = meta.get(key);
        if (v == null) return def;
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        throw new LevelFormatException(source, metaLine.get(key),
                "'" + key + "'는 true 또는 false여야 한다: '" + v + "'");
    }

    private static int intMeta(String source, Map<String, String> meta, Map<String, Integer> metaLine,
                               String key, int min) throws LevelFormatException {
        try {
            int n = Integer.parseInt(meta.get(key));
            if (n < min) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            throw new LevelFormatException(source, metaLine.get(key),
                    "'" + key + "'는 " + min + " 이상의 정수여야 한다: '" + meta.get(key) + "'");
        }
    }

    private static Double ratioMeta(String source, Map<String, String> meta, Map<String, Integer> metaLine,
                                    String key) throws LevelFormatException {
        if (!meta.containsKey(key)) return null;
        try {
            double v = Double.parseDouble(meta.get(key));
            if (Double.isNaN(v) || v < 1.0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new LevelFormatException(source, metaLine.get(key),
                    "'" + key + "'는 1.0 이상의 실수여야 한다: '" + meta.get(key) + "'");
        }
    }

    private static String stripCr(String s) {
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }
}
