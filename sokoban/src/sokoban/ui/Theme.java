package sokoban.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;

/** 색과 글꼴. 외부 이미지 없이 Graphics2D 도형만 쓴다 (FR-6.6). */
final class Theme {
    private Theme() {
    }

    static final Color BG_TOP = new Color(0x1d2540);
    static final Color BG_BOTTOM = new Color(0x0e1224);
    static final Color TEXT = new Color(0xeef1fb);
    static final Color TEXT_DIM = new Color(0x9aa3c4);
    static final Color ACCENT = new Color(0xffc857);
    static final Color PANEL = new Color(0x28305a);
    static final Color PANEL_SEL = new Color(0x3b4784);
    static final Color PANEL_LOCK = new Color(0x1a2040);
    static final Color GOOD = new Color(0x6fdc8c);

    // 월드 (미니멀: 단색 면 + 가는 윤곽선)
    static final Color GRID = new Color(0x8fa0d8);
    static final Color WALL = new Color(0x7d8bb3);
    static final Color BLOCK = new Color(0xe8a24a);
    static final Color BLOCK_ON_GOAL = new Color(0x5fd08a);
    static final Color GOAL = new Color(0xffd45a);
    static final Color HOLE = new Color(0x2a1f3d);
    // 새 타일 색: 블록(주황)·플레이어(청록)·목표(노랑)와 겹치지 않게 잡았다
    static final Color ICE = new Color(0xe3f4ff);
    static final Color PORTAL_A = new Color(0x8f6bff);
    static final Color PORTAL_B = new Color(0xff5fa8);
    static final Color PLAYER = new Color(0x52e0ff);
    static final Color PREVIEW_PLANAR = new Color(0x52e0ff);
    static final Color PREVIEW_VERTICAL = new Color(0xc4a4ff);

    private static final String FAMILY = pickFamily();

    private static String pickFamily() {
        String[] want = {"Malgun Gothic", "맑은 고딕", "Apple SD Gothic Neo", "NanumGothic", "Noto Sans CJK KR"};
        var have = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String f : want) {
            if (have.contains(f)) return f;
        }
        return Font.DIALOG;
    }

    static Font font(int style, float size) {
        return new Font(FAMILY, style, 12).deriveFont(size);
    }

    static Color shade(Color c, double f) {
        return new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f), c.getAlpha());
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
