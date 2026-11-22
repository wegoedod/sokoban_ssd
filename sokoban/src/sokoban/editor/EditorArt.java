package sokoban.editor;

import java.awt.*;
import java.awt.geom.*;

final class EditorArt {
    static final Color BG=new Color(0x0c1220), PANEL=new Color(0x141d2d), LINE=new Color(0x2c384c),
            TEXT=new Color(0xe7edf6), DIM=new Color(0x8e9eb6), ACCENT=new Color(0xb1a0ff), CYAN=new Color(0x63d9d0);
    static Font font(int style,float size) { return new Font("Malgun Gothic",style,(int)size); }
    static void quality(Graphics2D g) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON); }
    static Color shade(Color c,double n) { return new Color((int)Math.min(255,c.getRed()*n),(int)Math.min(255,c.getGreen()*n),(int)Math.min(255,c.getBlue()*n)); }
    static void center(Graphics2D g,String s,int x,int y) { g.drawString(s,x-g.getFontMetrics().stringWidth(s)/2,y); }
    static void icon(Graphics2D g,BlockCatalog.Block b,int x,int y,int size) {
        if (sokoban.ui.EditorScene.paintIcon(g,b.id(),x,y,size)) return;
        int m=Math.max(3,size/7),s=size-2*m; x+=m; y+=m;
        g.setStroke(new BasicStroke(Math.max(1.5f,size/22f))); Color c=b.color();
        switch(b.icon()) {
            case "cube" -> {
                g.setColor(shade(c,.65)); g.fillRoundRect(x,y+3,s,s-3,5,5);
                g.setColor(c); g.fillRoundRect(x,y,s,s*2/3,5,5);
                g.setColor(shade(c,1.2)); g.drawLine(x+3,y+3,x+s-3,y+3);
                g.setColor(shade(c,.8)); g.drawLine(x+s/2,y+s*2/3,x+s/2,y+s-2);
                if(b.id().equals("box")) { g.drawLine(x+4,y+5,x+s-4,y+s*2/3-3); g.drawLine(x+s-4,y+5,x+4,y+s*2/3-3); }
            }
            case "player" -> { g.setColor(c); g.fillOval(x+s/3,y,s/3,s/3); g.fillRoundRect(x+s/5,y+s/3+2,s*3/5,s*2/3-2,8,8); g.setColor(shade(c,.65)); g.fillRect(x+s/2,y+s/2,2,s/3); }
            case "hole" -> { g.setColor(shade(c,.6)); g.fillOval(x,y+s/6,s,s*2/3); g.setColor(BG); g.fillOval(x+4,y+s/6+4,s-8,Math.max(3,s*2/3-8)); g.setColor(c); g.drawArc(x,y+s/6,s,s*2/3,10,160); }
            case "ice" -> { g.setColor(shade(c,.5)); g.fillRoundRect(x,y,s,s,5,5); g.setColor(c); g.drawLine(x+s/2,y+3,x+s/2,y+s-3); g.drawLine(x+3,y+s/2,x+s-3,y+s/2); g.drawLine(x+6,y+6,x+s-6,y+s-6); g.drawLine(x+s-6,y+6,x+6,y+s-6); }
            case "ring" -> { g.setColor(shade(c,.25)); g.fillOval(x,y,s,s); g.setColor(c); g.drawOval(x+2,y+2,s-4,s-4); if(b.glyph().isEmpty()) g.fillOval(x+s/2-3,y+s/2-3,6,6); }
            default -> { g.setColor(shade(c,.25)); g.fillRoundRect(x,y,s,s,7,7); g.setColor(c); g.drawRoundRect(x,y,s,s,7,7); }
        }
        if(!b.glyph().isEmpty()) { g.setColor(c); g.setFont(font(Font.BOLD,size*.36f)); center(g,b.glyph(),x+s/2,y+s/2+(int)(size*.12)); }
    }
    static Polygon polygon(double... xy) { Polygon p=new Polygon(); for(int i=0;i<xy.length;i+=2)p.addPoint((int)xy[i],(int)xy[i+1]); return p; }
}
