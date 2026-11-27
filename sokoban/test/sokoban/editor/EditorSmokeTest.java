package sokoban.editor;

import java.nio.file.*;
import java.util.Map;
import sokoban.level.LevelParser;

/** Focused compatibility and document-loss checks; no solver run. */
public final class EditorSmokeTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        BlockCatalog catalog=new BlockCatalog(Path.of("config/editor-blocks.properties"));
        EditorDocument d=EditorDocument.demo();
        d.star3=1.3;d.star2=1.8;
        d.place(6,4,0,catalog.get("box"),catalog);
        check(d.cell(6,4,0).terrain().equals("goal")&&d.cell(6,4,0).entity().equals("box"),"box/goal composition");
        d.place(2,3,2,catalog.get("player"),catalog);
        check(d.cell(3,4,0).entity().isEmpty()&&d.cell(2,3,2).entity().equals("player"),"unique player across floors");
        EditorDocument before=d.copy();d.place(3,3,2,catalog.get("magnet"),catalog);
        check(!before.same(d),"snapshot isolation");
        d.set(3,3,2,new EditorDocument.Cell("magnet","",Map.of("group","문 & 상자","range","7","future-field","keep me"),Map.of()));
        Path temp=Files.createTempFile("sokoban-editor-test-",".slevel");
        try {EditorIO.save(temp,d);check(EditorIO.load(temp).same(d),"native round trip preserves custom properties");}
        finally{Files.deleteIfExists(temp);}
        boolean rejected=false;try{EditorIO.gameText(d,catalog);}catch(Exception expected){rejected=true;}
        check(rejected,"unsupported mechanics must not silently disappear on export");
        var scored=LevelParser.parse("scoring",EditorIO.gameText(before,catalog).lines().toList());
        check(scored.star3Ratio.equals(1.3)&&scored.star2Ratio.equals(1.8),"scoring metadata survives export");
        var scene=new sokoban.ui.EditorScene();
        var blank=new EditorDocument(5,5,3,"draft","플레이어 없는 초안");
        var image=new java.awt.image.BufferedImage(900,600,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();
        for(int angle=0;angle<8;angle++) {
            for(int z=0;z<3;z++) {
                scene.paint(graphics,blank,catalog,new java.awt.Rectangle(20,20,860,550),z,false);
                for(int y=0;y<5;y++)for(int x=0;x<5;x++) {
                    var center=scene.project(x,y,z).center();var hit=scene.pick(blank,z,(int)Math.round(center.getX()),(int)Math.round(center.getY()));
                    check(hit!=null&&hit.x==x&&hit.y==y,"3D selected-floor picking: angle="+angle+" z="+z);
                }
            }
            scene.rotate(1);
        }
        graphics.dispose();
        int count=0;
        try(var stream=Files.list(Path.of("levels"))){for(Path file:stream.filter(p->p.toString().endsWith(".txt")).toList()){
            EditorDocument imported=EditorIO.importGame(file);String exported=EditorIO.gameText(imported,catalog);
            var old=LevelParser.parse(file.toString(),Files.readAllLines(file));var next=LevelParser.parse("export",exported.lines().toList());
            check(old.w==next.w&&old.d==next.d&&old.h==next.h&&old.initial.equals(next.initial),"existing level state: "+file);
            for(int i=0;i<old.cellCount();i++)check(old.tile(i)==next.tile(i)&&old.portalPartner(i)==next.portalPartner(i),"existing terrain: "+file);
            count++;
        }}
        // 순서 열쇠 예제: 게임 TXT를 불러와 다시 내보내도 열쇠 3개와 순서가 그대로다
        EditorDocument demo=EditorIO.importGame(Path.of("examples","sequence-key-demo.txt"));
        var seq=LevelParser.parse("demo",EditorIO.gameText(demo,catalog).lines().toList());
        check(seq.keyCount==3,"sequence key example keeps 3 keys");
        check(seq.keyOrder(seq.idx(2,1,0))==1&&seq.keyOrder(seq.idx(4,1,0))==2&&seq.keyOrder(seq.idx(6,1,0))==3,"key order kept");
        check(seq.tile(seq.idx(2,1,0))==sokoban.core.TileType.EMPTY,"key sits on empty floor");
        System.out.println("Editor checks passed: composition, unique player, snapshots, XML properties, safe export, 600 projected cell picks, "+count+" existing levels, sequence key demo.");
    }
}
