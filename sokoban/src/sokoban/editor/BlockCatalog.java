package sokoban.editor;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Palette, placement and inspector fields come from data, not UI switches. */
public final class BlockCatalog {
    public record Block(String id, String name, String category, boolean entity, Color color,
                        String icon, String glyph, String description, String symbol,
                        boolean supportsEntity, boolean unique, Map<String,String> defaults) {
        public boolean exportable() { return !symbol.isEmpty(); }
    }
    private final LinkedHashMap<String,Block> blocks = new LinkedHashMap<>();
    public BlockCatalog(Path file) throws IOException {
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); }
        for (String id : p.getProperty("blocks", "").split(",")) {
            id = id.trim();
            if (id.isEmpty() || blocks.containsKey(id)) throw new IOException("중복되거나 빈 블록 ID: " + id);
            String prefix = id + ".";
            Map<String,String> defaults = new LinkedHashMap<>();
            for (String field : p.getProperty(prefix + "fields", "").split(",")) {
                field = field.trim();
                if (!field.isEmpty()) defaults.put(field, p.getProperty(prefix + "default." + field, ""));
            }
            try {
                blocks.put(id, new Block(id, p.getProperty(prefix+"name", id),
                        p.getProperty(prefix+"category", "기본"), p.getProperty(prefix+"kind", "terrain").equals("entity"),
                        Color.decode(p.getProperty(prefix+"color", "#8899aa")), p.getProperty(prefix+"icon", "cube"),
                        p.getProperty(prefix+"glyph", ""), p.getProperty(prefix+"description", ""),
                        p.getProperty(prefix+"symbol", ""), Boolean.parseBoolean(p.getProperty(prefix+"supportsEntity", "false")),
                        Boolean.parseBoolean(p.getProperty(prefix+"unique", "false")), Collections.unmodifiableMap(defaults)));
            } catch (RuntimeException ex) { throw new IOException("블록 정의 오류: " + id, ex); }
        }
        if (!blocks.containsKey("empty") || !blocks.containsKey("player")) throw new IOException("empty와 player 정의가 필요합니다.");
    }
    public List<Block> all() { return List.copyOf(blocks.values()); }
    public Block get(String id) { return blocks.get(id); }
    public Block display(String id) {
        Block b = get(id);
        return b != null ? b : new Block(id, "알 수 없음: " + id, "미등록", false, Color.MAGENTA,
                "rune", "?", "블록 정의를 다시 불러오세요. 배치 데이터는 보존됩니다.", "", false, false, Map.of());
    }
}
