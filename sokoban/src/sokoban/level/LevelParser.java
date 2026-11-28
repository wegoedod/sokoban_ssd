package sokoban.level;

import sokoban.core.Direction;
import sokoban.core.GameState;
import sokoban.core.Level;
import sokoban.core.LevelTheme;
import sokoban.core.Magnet;
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
 * theme: station          (선택, planet|wormhole|station. 없으면 기믹으로 고른다)
 * layer 0
 * @$-.
 * </pre>
 *
 * 문자: {@code #}벽 {@code -}(또는 공백)빈 공간 {@code .}목표 {@code O}구멍 {@code =}메워진 구멍(처음부터 평지)
 * {@code ~}빙판 {@code A}{@code B}포탈 {@code $}블록 {@code *}목표 위 블록
 * {@code @}플레이어 {@code +}목표 위 플레이어, {@code 1}~{@code 9}순서 목표(번호).
 * {@code I}철 상자 {@code J}목표 위 철 상자 {@code S}자기장 스위치 {@code M}자기장 장치.
 * 장치 속성: {@code magnet.x,y,z: +X,3}. 좌표는 0부터, 방향은 ±X/±Y/±Z, 범위는 1~32.
 * 층은 {@code layer N}으로 나눈다(0부터 연속, 0이 맨 아래). 격자 밖은 고체다.
 *
 * <p>포탈은 문자마다 정확히 두 칸이 있어야 한다(레벨 디자인 M-04). 두 칸은 서로의 짝이며 같은 층일 필요는 없다.
 * 레벨 에디터가 내보내는 2D 맵은 {@code layer 0} 한 층짜리 레벨로 그대로 읽힌다.
 *
 * <p>순서 목표: 숫자 칸은 목표({@link TileType#GOAL})이면서 순서 번호를 가진다. 번호는 1부터 빠짐없이 이어져야 하고
 * (예: 1, 2, 3) 중복될 수 없다. 1번 목표에 상자를 넣어야 2번이 열리므로, 순서 목표는 처음부터 상자나 플레이어가
 * 올라간 채로 시작할 수 없다. 숫자 기호에는 상자·플레이어를 겹쳐 쓰는 표기가 없으므로({@code *}·{@code +}는
 * 번호 없는 일반 목표다) 이런 시작 상태는 형식 자체로 표현되지 않는다.
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
        LevelTheme theme = null;
        if (meta.containsKey("theme")) {
            theme = LevelTheme.parse(meta.get("theme"));
            if (theme == null) {
                throw new LevelFormatException(source, metaLine.get("theme"),
                        "'theme'는 planet, wormhole, station 중 하나여야 한다: '" + meta.get("theme") + "'");
            }
        }
        Set<String> known = Set.of("id", "name", "optimalMoves", "solution", "star3", "star2", "pull", "theme");
        for (String k : meta.keySet()) {
            if (!known.contains(k) && !k.startsWith("magnet.")) {
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
        List<Integer> iron = new ArrayList<>();
        int player = -1;
        int goalCount = 0;
        // 순서 목표: 셀 인덱스별 번호(0=일반 목표 또는 목표 아님) / 번호 → 처음 나온 줄(중복 보고용)
        int[] goalOrder = new int[tiles.length];
        Map<Integer, Integer> orderLine = new HashMap<>();
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
                        case 'I' -> { t = TileType.EMPTY; block = true; iron.add(idx); }
                        case 'J' -> { t = TileType.GOAL; block = true; iron.add(idx); }
                        case 'S' -> t = TileType.MAGNET_SWITCH;
                        case 'M' -> t = TileType.MAGNET;
                        case '@' -> { t = TileType.EMPTY; pl = true; }
                        case '+' -> { t = TileType.GOAL; pl = true; }
                        // 숫자는 번호가 붙은 목표(순서 목표)다. 지형은 목표 칸이고, 순서만 따로 기록한다.
                        case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> { t = TileType.GOAL; goalOrder[idx] = c - '0'; }
                        default -> throw new LevelFormatException(source, ln,
                                "알 수 없는 문자 '" + c + "' (x=" + x + ", y=" + y + ", layer " + z + ")");
                    }
                    tiles[idx] = t;
                    if (t == TileType.GOAL) goalCount++;
                    if (goalOrder[idx] > 0) {
                        Integer prev = orderLine.putIfAbsent(goalOrder[idx], ln);
                        if (prev != null) {
                            throw new LevelFormatException(source, ln, "순서 목표 " + goalOrder[idx]
                                    + "번이 두 번 나왔다 (처음: " + prev + "번째 줄).");
                        }
                    }
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
        if (goalCount == 0) throw new LevelFormatException(source, 0, "목표가 없다('.' 또는 '1'~'9').");
        for (int o = 1; o <= orderLine.size(); o++) {
            if (!orderLine.containsKey(o)) {
                throw new LevelFormatException(source, 0, "순서 목표 번호는 1부터 "
                        + orderLine.size() + "까지 빠짐없이 이어져야 한다 (없는 번호 " + o + ").");
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

        GameState init = GameState.of(player, b, hs, iron.stream().mapToInt(Integer::intValue).toArray(), false);
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
        // 난이도 D(FR-5.3)의 기믹 항: 포탈은 쌍의 수, 순서 목표는 목표 칸 수를 인스턴스 수로 센다
        Map<String, Integer> gimmicks = new LinkedHashMap<>();
        if (!portalCells.isEmpty()) gimmicks.put("portal", portalCells.size());
        if (!orderLine.isEmpty()) gimmicks.put("sequence_goal", orderLine.size());
        List<Magnet> magnets = new ArrayList<>();
        Map<Integer, Magnet> configured = new HashMap<>();
        for (String key : meta.keySet()) {
            if (!key.startsWith("magnet.")) continue;
            try {
                String[] xyz = key.substring(7).split(",", -1);
                String[] settings = meta.get(key).split(",", -1);
                if (xyz.length != 3 || settings.length != 2) throw new IllegalArgumentException();
                int x = Integer.parseInt(xyz[0].trim()), y = Integer.parseInt(xyz[1].trim()), z = Integer.parseInt(xyz[2].trim());
                if (x < 0 || x >= w || y < 0 || y >= d || z < 0 || z >= h) throw new IllegalArgumentException();
                int cell = (z * d + y) * w + x;
                if (tiles[cell] != TileType.MAGNET || configured.containsKey(cell)) throw new IllegalArgumentException();
                configured.put(cell, new Magnet(cell, Magnet.direction(settings[0]), Integer.parseInt(settings[1].trim())));
            } catch (IllegalArgumentException ex) {
                throw new LevelFormatException(source, metaLine.get(key),
                        "자기장 설정은 'magnet.x,y,z: +X,3' 형식입니다. 좌표는 M 칸, 방향은 ±X/±Y/±Z, 범위는 1~32여야 합니다.");
            }
        }
        int magnetSwitches = 0;
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == TileType.MAGNET) magnets.add(configured.getOrDefault(i, new Magnet(i, Direction.RIGHT, 3)));
            if (tiles[i] == TileType.MAGNET_SWITCH) magnetSwitches++;
        }
        if (!magnets.isEmpty() || magnetSwitches > 0) gimmicks.put("magnet", magnets.size() + magnetSwitches);
        int[] orders = orderLine.isEmpty() ? null : goalOrder;
        return new Level(id, name, w, d, h, tiles, init, opt, solution, s3, s2, gimmicks, source,
                pull, partner, portalChar, orders, magnets, theme);
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
