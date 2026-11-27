package sokoban.editor;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import sokoban.core.Level;
import sokoban.level.LevelParser;

public final class EditorIO {
    private EditorIO() { }
    private static DocumentBuilderFactory factory() throws Exception {
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setXIncludeAware(false); f.setExpandEntityReferences(false); return f;
    }
    public static void save(Path path,EditorDocument model) throws Exception {
        Document xml=factory().newDocumentBuilder().newDocument();
        Element root=xml.createElement("sokoban-editor"); xml.appendChild(root);
        root.setAttribute("version","1"); root.setAttribute("width",""+model.width);
        root.setAttribute("depth",""+model.depth); root.setAttribute("floors",""+model.floors);
        root.setAttribute("id",model.id); root.setAttribute("name",model.name); root.setAttribute("pull",""+model.pull);
        if(model.star3!=null)root.setAttribute("star3",model.star3.toString());
        if(model.star2!=null)root.setAttribute("star2",model.star2.toString());
        for(int z=0;z<model.floors;z++) for(int y=0;y<model.depth;y++) for(int x=0;x<model.width;x++) {
            var c=model.cell(x,y,z); if(!c.occupied() && c.terrainProps().isEmpty() && c.entityProps().isEmpty()) continue;
            Element e=xml.createElement("cell"); root.appendChild(e);
            e.setAttribute("x",""+x); e.setAttribute("y",""+y); e.setAttribute("z",""+z);
            e.setAttribute("terrain",c.terrain()); e.setAttribute("entity",c.entity());
            writeProps(xml,e,"terrain",c.terrainProps()); writeProps(xml,e,"entity",c.entityProps());
        }
        TransformerFactory tf=TransformerFactory.newInstance();
        tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");
        tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");
        Transformer t=tf.newTransformer(); t.setOutputProperty(OutputKeys.INDENT,"yes");
        t.setOutputProperty(OutputKeys.ENCODING,"UTF-8");
        StringWriter writer=new StringWriter(); t.transform(new DOMSource(xml),new StreamResult(writer));
        writeAtomic(path,writer.toString());
    }
    private static void writeProps(Document xml,Element parent,String scope,Map<String,String> props) {
        for(var p:new TreeMap<>(props).entrySet()) {
            Element e=xml.createElement("property"); e.setAttribute("scope",scope);
            e.setAttribute("name",p.getKey()); e.setAttribute("value",p.getValue()); parent.appendChild(e);
        }
    }
    public static EditorDocument load(Path file) throws Exception {
        if(Files.size(file)>8_000_000) throw new IOException("8MB 이하의 프로젝트만 열 수 있습니다.");
        Document xml=factory().newDocumentBuilder().parse(file.toFile()); Element root=xml.getDocumentElement();
        if(!root.getTagName().equals("sokoban-editor") || !root.getAttribute("version").equals("1"))
            throw new IOException("지원하지 않는 프로젝트 형식입니다.");
        EditorDocument model=new EditorDocument(Integer.parseInt(root.getAttribute("width")),Integer.parseInt(root.getAttribute("depth")),
                Integer.parseInt(root.getAttribute("floors")),root.getAttribute("id"),root.getAttribute("name"));
        model.pull=Boolean.parseBoolean(root.getAttribute("pull"));
        if(root.hasAttribute("star3"))model.star3=Double.valueOf(root.getAttribute("star3"));
        if(root.hasAttribute("star2"))model.star2=Double.valueOf(root.getAttribute("star2"));
        Set<Integer> seen=new HashSet<>(); NodeList cells=root.getElementsByTagName("cell");
        for(int i=0;i<cells.getLength();i++) {
            Element e=(Element)cells.item(i); int x=Integer.parseInt(e.getAttribute("x")),y=Integer.parseInt(e.getAttribute("y")),z=Integer.parseInt(e.getAttribute("z"));
            if(x<0||y<0||z<0||x>=model.width||y>=model.depth||z>=model.floors||!seen.add(model.index(x,y,z)))
                throw new IOException("잘못되거나 중복된 셀 좌표입니다.");
            Map<String,String> terrain=new LinkedHashMap<>(),entity=new LinkedHashMap<>();
            NodeList fields=e.getElementsByTagName("property");
            for(int j=0;j<fields.getLength();j++) { Element p=(Element)fields.item(j);
                (p.getAttribute("scope").equals("entity")?entity:terrain).put(p.getAttribute("name"),p.getAttribute("value")); }
            String type=e.getAttribute("terrain"); if(type.isBlank()) throw new IOException("셀의 terrain ID가 없습니다.");
            model.set(x,y,z,new EditorDocument.Cell(type,e.getAttribute("entity"),terrain,entity));
        }
        return model;
    }
    public static EditorDocument importGame(Path file) throws Exception {
        Level lv=LevelParser.parse(file.toString(),Files.readAllLines(file,StandardCharsets.UTF_8));
        EditorDocument d=new EditorDocument(lv.w,lv.d,lv.h,lv.id,lv.name); d.pull=lv.pullAllowed;
        d.star3=lv.star3Ratio;d.star2=lv.star2Ratio;
        for(int z=0;z<lv.h;z++) for(int y=0;y<lv.d;y++) for(int x=0;x<lv.w;x++) {
            int i=lv.idx(x,y,z);
            if(lv.keyOrder(i)>0) {
                d.set(x,y,z,new EditorDocument.Cell("sequence_key","",Map.of("order",""+lv.keyOrder(i)),Map.of()));
                continue;
            }
            String t=switch(lv.tile(i)) { case EMPTY->"empty"; case WALL->"wall"; case GOAL->"goal";
                case HOLE->lv.initial.isHole(i)?"hole":"filled_hole"; case ICE->"ice";
                case PORTAL->lv.portalId(i)=='B'?"portal_b":"portal_a"; };
            d.set(x,y,z,new EditorDocument.Cell(t,lv.initial.player==i?"player":lv.initial.hasBlock(i)?"box":"",Map.of(),Map.of()));
        }
        return d;
    }
    public static String gameText(EditorDocument d,BlockCatalog catalog) throws Exception {
        if(d.id.isBlank()||d.name.isBlank()||d.id.contains("\n")||d.name.contains("\n")) throw new IOException("레벨 ID와 이름을 확인하세요.");
        StringBuilder out=new StringBuilder("id: "+d.id+"\nname: "+d.name+"\npull: "+d.pull+"\n");
        // Editing invalidates a previously calculated solution, but preserves scoring settings.
        if(d.star3!=null)out.append("star3: ").append(d.star3).append('\n');
        if(d.star2!=null)out.append("star2: ").append(d.star2).append('\n');
        for(int z=0;z<d.floors;z++) {
            out.append("layer ").append(z).append('\n');
            for(int y=0;y<d.depth;y++) { for(int x=0;x<d.width;x++) {
                var cell=d.cell(x,y,z); var base=catalog.get(cell.terrain()); var ent=catalog.get(cell.entity());
                String where=" ("+(x+1)+", "+(y+1)+", "+(z+1)+"층)";
                if(base==null||!base.exportable()||(!cell.entity().isEmpty()&&(ent==null||!ent.exportable())))
                    throw new IOException("게임에서 지원하지 않는 설계용 블록"+where+" — 프로젝트(.slevel)로 저장하세요.");
                String symbol=base.symbol();
                if(base.id().equals("sequence_key")) {
                    // 순서 열쇠는 order 속성이 게임 규칙(몇 번째 열쇠인지)이므로 기호로 옮긴다. group은 기록용이라 버린다.
                    for(String key:cell.terrainProps().keySet())
                        if(!key.equals("order")) throw new IOException("순서 열쇠의 '"+key+"' 속성은 게임 TXT에 저장할 수 없습니다"+where+" — 프로젝트로 저장하세요.");
                    if(!cell.entityProps().isEmpty())
                        throw new IOException("순서 열쇠 칸의 개체 속성은 게임 TXT에 저장할 수 없습니다"+where+" — 프로젝트로 저장하세요.");
                    symbol=keySymbol(cell.terrainProps().getOrDefault("order","1"),where);
                } else if(!cell.terrainProps().isEmpty()||!cell.entityProps().isEmpty()) {
                    throw new IOException("추가 속성은 게임 TXT에 저장할 수 없습니다"+where+" — 프로젝트로 저장하세요.");
                }
                if(ent!=null) {
                    if(base.id().equals("empty")) symbol=ent.symbol();
                    else if(base.id().equals("goal") && ent.id().equals("box")) symbol="*";
                    else if(base.id().equals("goal") && ent.id().equals("player")) symbol="+";
                    else throw new IOException("게임 TXT로 표현할 수 없는 겹침"+where);
                }
                if(symbol.length()!=1) throw new IOException("잘못된 블록 기호"+where);
                out.append(symbol);
            } out.append('\n'); }
        }
        String text=out.toString(); LevelParser.parse("편집 중인 레벨",text.lines().toList()); return text;
    }
    /** 순서 열쇠의 order 속성(1~9)을 레벨 기호 하나로 옮긴다. */
    private static String keySymbol(String order,String where) throws IOException {
        try {
            int n=Integer.parseInt(order.trim());
            if(n>=1&&n<=9) return Integer.toString(n);
        } catch(RuntimeException ignored) {
        }
        throw new IOException("순서 열쇠 번호는 1~9 사이 정수여야 합니다"+where+" — 현재 '"+order+"'");
    }
    public static void writeAtomic(Path destination,String text) throws IOException {
        Path path=destination.toAbsolutePath(); Files.createDirectories(path.getParent());
        Path temp=Files.createTempFile(path.getParent(),".sokoban-", ".tmp");
        try {
            Files.writeString(temp,text,StandardCharsets.UTF_8);
            try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
