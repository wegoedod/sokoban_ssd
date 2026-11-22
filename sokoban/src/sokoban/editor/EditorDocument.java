package sokoban.editor;

import java.util.*;

/** Immutable cells retain independent terrain/entity properties, including unknown future fields. */
public final class EditorDocument {
    public record Cell(String terrain, String entity, Map<String,String> terrainProps, Map<String,String> entityProps) {
        public Cell { terrainProps = Map.copyOf(terrainProps); entityProps = Map.copyOf(entityProps); }
        public static Cell empty() { return new Cell("empty", "", Map.of(), Map.of()); }
        public boolean occupied() { return !terrain.equals("empty") || !entity.isEmpty(); }
    }
    public final int width, depth, floors;
    public String id, name;
    public boolean pull;
    public Double star3, star2;
    private final Cell[] cells;
    public EditorDocument(int width, int depth, int floors, String id, String name) {
        if (width < 1 || width > 32 || depth < 1 || depth > 32 || floors < 1 || floors > 16)
            throw new IllegalArgumentException("크기: 1~32칸, 층: 1~16층");
        this.width=width; this.depth=depth; this.floors=floors; this.id=id; this.name=name;
        cells = new Cell[width*depth*floors]; Arrays.fill(cells, Cell.empty());
    }
    public int index(int x,int y,int z) { return (z*depth+y)*width+x; }
    public Cell cell(int x,int y,int z) { return cells[index(x,y,z)]; }
    public void set(int x,int y,int z,Cell cell) { cells[index(x,y,z)] = cell; }
    public int count(int z) { int n=0; for(int y=0;y<depth;y++) for(int x=0;x<width;x++) if(cell(x,y,z).occupied()) n++; return n; }
    public void place(int x,int y,int z,BlockCatalog.Block block,BlockCatalog catalog) {
        Cell old = cell(x,y,z);
        if (block.id().equals("empty")) { set(x,y,z,Cell.empty()); return; }
        if (block.entity()) {
            if (!catalog.display(old.terrain()).supportsEntity()) return;
            if (old.entity().equals(block.id())) return;
            if (block.unique()) for(int i=0;i<cells.length;i++) {
                Cell c=cells[i]; if(c.entity().equals(block.id())) cells[i]=new Cell(c.terrain(),"",c.terrainProps(),Map.of());
            }
            set(x,y,z,new Cell(old.terrain(),block.id(),old.terrainProps(),block.defaults()));
        } else {
            if(old.terrain().equals(block.id())) return;
            set(x,y,z,new Cell(block.id(),block.supportsEntity()?old.entity():"",block.defaults(),
                    block.supportsEntity()?old.entityProps():Map.of()));
        }
    }
    public EditorDocument copy() {
        EditorDocument d=new EditorDocument(width,depth,floors,id,name); d.pull=pull;
        d.star3=star3; d.star2=star2;
        System.arraycopy(cells,0,d.cells,0,cells.length); return d;
    }
    public EditorDocument resized(int w,int d,int h) {
        EditorDocument next=new EditorDocument(w,d,h,id,name); next.pull=pull;
        next.star3=star3; next.star2=star2;
        for(int z=0;z<Math.min(h,floors);z++) for(int y=0;y<Math.min(d,depth);y++) for(int x=0;x<Math.min(w,width);x++)
            next.set(x,y,z,cell(x,y,z));
        return next;
    }
    public boolean same(EditorDocument d) {
        return d!=null && width==d.width && depth==d.depth && floors==d.floors && pull==d.pull
                && id.equals(d.id) && name.equals(d.name) && Objects.equals(star3,d.star3)
                && Objects.equals(star2,d.star2) && Arrays.equals(cells,d.cells);
    }
    public static EditorDocument demo() {
        EditorDocument d=new EditorDocument(10,8,3,"custom01","새 레벨");
        for(int x=1;x<9;x++) { d.set(x,1,0,new Cell("wall","",Map.of(),Map.of())); d.set(x,6,0,new Cell("wall","",Map.of(),Map.of())); }
        for(int y=2;y<6;y++) { d.set(1,y,0,new Cell("wall","",Map.of(),Map.of())); d.set(8,y,0,new Cell("wall","",Map.of(),Map.of())); }
        d.set(3,4,0,new Cell("empty","player",Map.of(),Map.of()));
        d.set(5,4,0,new Cell("empty","box",Map.of(),Map.of()));
        d.set(6,4,0,new Cell("goal","",Map.of(),Map.of()));
        for(int x=3;x<7;x++) d.set(x,2,1,new Cell("wall","",Map.of(),Map.of()));
        d.set(4,3,1,new Cell("ice","",Map.of(),Map.of()));
        d.set(4,4,1,new Cell("ice","",Map.of(),Map.of()));
        d.set(6,3,2,new Cell("wall","",Map.of(),Map.of()));
        return d;
    }
}
