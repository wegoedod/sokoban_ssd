package sokoban.ui;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

/** 화면 하나(메인 메뉴, 레벨 선택, 플레이). 모든 호출은 EDT에서 일어난다. */
interface Screen {
    void paint(Graphics2D g, int w, int h);

    default void tick(long dtMillis) {
    }

    default void keyPressed(KeyEvent e) {
    }

    default void mouseMoved(int x, int y) {
    }

    default void mouseClicked(int x, int y) {
    }

    default void mouseExited() {
    }

    default void mousePressed(int x, int y) {
    }

    default void mouseDragged(int x, int y) {
    }

    default void mouseReleased(int x, int y) {
    }
}
