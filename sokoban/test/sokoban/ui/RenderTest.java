package sokoban.ui;

import sokoban.core.Level;
import sokoban.level.LevelFormatException;
import sokoban.level.LevelParser;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** 렌더링 결과를 픽셀로 확인하는 테스트 (headless). 층 강조. */
public final class RenderTest {
    private static final int W = 800, H = 600;
    private static final int BG = 0x101830;

    private RenderTest() {
    }

    private static void check(boolean c, String msg) {
        if (!c) throw new AssertionError(msg);
    }

    /** 층을 '/'로 구분한 문자열로 레벨을 만든다. 첫 인자가 맨 아래 층(z=0), 층 안의 행은 ','로 구분. */
    private static Level level(String... layers) {
        List<String> lines = new ArrayList<>();
        lines.add("id: R");
        for (int z = 0; z < layers.length; z++) {
            lines.add("layer " + z);
            lines.addAll(List.of(layers[z].split(",")));
        }
        try {
            return LevelParser.parse("render-test", lines);
        } catch (LevelFormatException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    private static BufferedImage render(Level lv, boolean layerFocus) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(BG));
        g.fillRect(0, 0, W, H);
        Camera cam = new Camera();
        if (lv.h == 1) cam.snapToTopView(); else cam.snapToDefaultView();
        new IsoRenderer().render(g, lv, lv.initial, null, new Rectangle(20, 20, W - 40, H - 40), cam, layerFocus);
        g.dispose();
        return img;
    }

    private static int differingPixels(BufferedImage a, BufferedImage b) {
        int n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (a.getRGB(x, y) != b.getRGB(x, y)) n++;
        return n;
    }

    /** 맨 위층에 서 있어도 Tab 층 강조를 켜면 화면이 달라진다(아래층이 흐려진다). */
    public static void layerFocusChangesEvenOnTopLayer() {
        Level lv = level("---,-.-,---", "---,-$-,---", "---,-@-,---");   // 플레이어: 맨 위층(z=2)
        int diff = differingPixels(render(lv, false), render(lv, true));
        check(diff > 300, "맨 위층에서 층 강조를 켜도 화면이 거의 안 변한다: 달라진 픽셀 " + diff);
    }

    /** 맨 아래층에 서 있으면 위층이 흐려져 달라진다. 강조를 끄면 원래대로 돌아온다. */
    public static void layerFocusDimsUpperLayersAndRestores() {
        Level lv = level("---,-@-,---", "---,-$-,---", "---,-.-,---");   // 플레이어: 맨 아래층(z=0)
        BufferedImage off = render(lv, false);
        int diff = differingPixels(off, render(lv, true));
        check(diff > 300, "맨 아래층에서 층 강조가 위층을 흐리게 하지 않는다: " + diff);
        check(differingPixels(off, render(lv, false)) == 0, "강조를 끄면 처음과 같아야 한다");
    }

    /** 한 층짜리 레벨은 흐려질 층이 없어 층 강조가 화면을 바꾸지 않는다(HUD 배지도 2D에서는 안 띄운다). */
    public static void layerFocusDoesNothingOnFlatLevel() {
        Level lv = level("---,-@-,-$.");
        check(differingPixels(render(lv, false), render(lv, true)) == 0, "2D 레벨에서 층 강조는 영향이 없어야 한다");
    }
}
