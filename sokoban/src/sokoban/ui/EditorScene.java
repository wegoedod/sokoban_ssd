package sokoban.ui;

import sokoban.editor.BlockCatalog;
import sokoban.editor.EditorDocument;
import sokoban.core.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

/** Adapter for editor documents. Uses exactly the game's camera, projection and object renderer. */
public final class EditorScene {
    public record CellShape(Shape shape, Shape edges, Point2D center) { }
    private final IsoRenderer renderer=new IsoRenderer();
    private final Camera camera=new Camera();
    private Level level;
    public void rotate(int step) { camera.yaw=camera.targetYaw=Math.round(camera.yaw/45)*45+step*45; }
    public void configure(EditorDocument doc,Rectangle area,int layer,boolean isolate) {
        level=adapt(doc,isolate?layer:-1);renderer.configure(level,area,camera);
    }
    public CellShape project(int x,int y,int z) { var p=renderer.projectCell(x,y,z,false);return new CellShape(p.shape(),p.edges(),p.center()); }
    public Point pick(EditorDocument doc,int layer,int x,int y) {
        Point result=null;double distance=Double.POSITIVE_INFINITY;
        for(int cy=0;cy<doc.depth;cy++)for(int cx=0;cx<doc.width;cx++) {
            var p=project(cx,cy,layer);if(!p.shape().contains(x,y))continue;double next=p.center().distanceSq(x,y);
            if(next<distance){distance=next;result=new Point(cx,cy);}
        }
        return result;
    }
    public void paint(Graphics2D g,EditorDocument doc,BlockCatalog catalog,Rectangle area,int layer,boolean isolate) {
        configure(doc,area,layer,isolate);renderer.renderEditor(g,level,area,camera,layer);
        // Future, data-defined objects get a labeled projected volume without pretending to have game behavior.
        for(int z=0;z<doc.floors;z++)if(!isolate||z==layer)for(int y=0;y<doc.depth;y++)for(int x=0;x<doc.width;x++) {
            var c=doc.cell(x,y,z);for(String id:new String[]{c.terrain(),c.entity()}) {
                if(id.isEmpty()||known(id))continue;var b=catalog.display(id);var p=project(x,y,z);
                g.setColor(new Color(b.color().getRed(),b.color().getGreen(),b.color().getBlue(),45));g.fill(p.shape());
                g.setColor(b.color());g.setStroke(new BasicStroke(1.5f));g.draw(p.edges());
                g.setFont(Theme.font(Font.BOLD,12));Gfx.centered(g,b.glyph().isEmpty()?"?":b.glyph(),(int)p.center().getX(),(int)p.center().getY()+4);
            }
        }
    }
    private static boolean known(String id){return Set.of("empty","wall","goal","hole","filled_hole","ice","portal_a","portal_b","sequence_goal","box","player").contains(id);}
    private static Level adapt(EditorDocument d,int onlyLayer) {
        TileType[] tiles=new TileType[d.width*d.depth*d.floors];Arrays.fill(tiles,TileType.EMPTY);
        List<Integer> boxes=new ArrayList<>(),holes=new ArrayList<>();int player=-1;
        int[] partners=new int[tiles.length];Arrays.fill(partners,-1);char[] portals=new char[tiles.length];
        int[] orders=new int[tiles.length];
        for(int z=0;z<d.floors;z++)if(onlyLayer<0||z==onlyLayer)for(int y=0;y<d.depth;y++)for(int x=0;x<d.width;x++) {
            int i=d.index(x,y,z);var c=d.cell(x,y,z);tiles[i]=switch(c.terrain()){
                case "wall"->TileType.WALL;case "goal","sequence_goal"->TileType.GOAL;case "hole","filled_hole"->TileType.HOLE;
                case "ice"->TileType.ICE;case "portal_a","portal_b"->TileType.PORTAL;default->TileType.EMPTY;};
            if(c.terrain().equals("sequence_goal"))orders[i]=goalOrderNumber(c.terrainProps());
            if(c.terrain().equals("hole"))holes.add(i);if(tiles[i]==TileType.PORTAL)portals[i]=c.terrain().equals("portal_b")?'B':'A';
            if(c.entity().equals("box"))boxes.add(i);if(c.entity().equals("player"))player=i;
        }
        return new Level(d.id,d.name,d.width,d.depth,d.floors,tiles,
                GameState.of(player,boxes.stream().mapToInt(i->i).toArray(),holes.stream().mapToInt(i->i).toArray()),-1,"",null,null,Map.of(),"editor",d.pull,partners,portals,previewOrders(orders,tiles.length));
    }
    /**
     * 설계 중에는 순서 목표 번호가 아직 1..k로 완성되지 않았을 수 있다(예: 2번만 배치). 그런 번호는
     * 미리보기에서 번호 없는 목표로 그려 미리보기가 깨지지 않게 한다. 내보내기 검증은 EditorIO가 한다.
     */
    private static int[] previewOrders(int[] orders,int n) {
        int max=0;boolean[] seen=new boolean[10];
        for(int o:orders){ if(o==0) continue; if(o>9||seen[o]) return new int[n]; seen[o]=true; max=Math.max(max,o); }
        for(int o=1;o<=max;o++) if(!seen[o]) return new int[n];
        return orders;
    }
    /** 순서 목표 블록의 {@code order} 속성(1~9). 없거나 잘못되었으면 1번으로 본다. */
    private static int goalOrderNumber(java.util.Map<String,String> props) {
        try { int n=Integer.parseInt(props.getOrDefault("order","1").trim()); return n>=1&&n<=9?n:1; }
        catch(RuntimeException e) { return 1; }
    }
    private static final Level ICON_LEVEL=new Level("icon","icon",1,1,1,new TileType[]{TileType.EMPTY},GameState.of(-1,new int[0],new int[0]),-1,"",null,null,Map.of(),"editor");
    public static boolean paintIcon(Graphics2D g,String id,int x,int y,int size) {
        Color c;float opacity=1;boolean outline=false;
        switch(id) {
            case "wall"->{c=Theme.WALL;opacity=.55f;}case "box"->c=Theme.BLOCK;case "player"->c=Theme.PLAYER;
            case "goal"->{c=Theme.GOAL;opacity=.22f;outline=true;}case "hole"->{c=Theme.HOLE;opacity=.85f;}
            case "ice"->{c=Theme.ICE;opacity=.50f;}case "portal_a"->{c=Theme.PORTAL_A;opacity=.60f;}
            case "portal_b"->{c=Theme.PORTAL_B;opacity=.60f;}default->{return false;}
        }
        Graphics2D copy=(Graphics2D)g.create();new IsoRenderer().objectIcon(copy,ICON_LEVEL,new Rectangle(x,y,size,size),c,opacity,id.equals("player"),outline);copy.dispose();return true;
    }
}
