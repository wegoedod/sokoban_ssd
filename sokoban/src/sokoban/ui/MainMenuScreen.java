package sokoban.ui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

final class MainMenuScreen implements Screen {
    private final App app;
    private final MenuList menu = new MenuList("시작", "레벨 디자이너", "종료");

    MainMenuScreen(App app) {
        this.app = app;
    }

    @Override
    public void paint(Graphics2D g, int w, int h) {
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, Math.max(36f, h * 0.10f)));
        Gfx.centered(g, "2.5D 소코반", w / 2, (int) (h * 0.26));
        g.setColor(Theme.TEXT_DIM);
        g.setFont(Theme.font(Font.PLAIN, 18f));
        Gfx.centered(g, "블록을 밀어 모든 목표 지점을 채우세요", w / 2, (int) (h * 0.26) + 36);

        int cleared = 0;
        for (var l : app.repo.levels) if (app.save.cleared(l.id)) cleared++;
        g.setFont(Theme.font(Font.PLAIN, 16f));
        Gfx.centered(g, "클리어 " + cleared + " / " + app.repo.levels.size(), w / 2, (int) (h * 0.26) + 66);

        menu.paint(g, w / 2, (int) (h * 0.46), 280, 54, 14);
        g.setColor(Theme.TEXT_DIM);
        g.setFont(Theme.font(Font.PLAIN, 14f));
        Gfx.centered(g, "게임 중: 인접 칸 클릭 · W A S D 이동 · ↑↓ 위·아래 · ←→ 시점 회전", w / 2, h - 46);
        Gfx.centered(g, "↑↓ 선택   Enter 확인", w / 2, h - 24);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        activate(menu.key(e));
    }

    @Override
    public void mouseMoved(int x, int y) {
        menu.hover(x, y);
    }

    @Override
    public void mouseClicked(int x, int y) {
        activate(menu.click(x, y));
    }

    private void activate(int i) {
        if (i == 0) {
            if (app.repo.levels.isEmpty()) {
                app.notify("불러온 레벨이 없다. levels 폴더를 확인하세요.");
            } else {
                app.showLevelSelect(app.firstOpenLevel());
            }
        } else if (i == 1) {
            app.showLevelDesigner();
        } else if (i == 2) {
            System.exit(0);
        }
    }
}
