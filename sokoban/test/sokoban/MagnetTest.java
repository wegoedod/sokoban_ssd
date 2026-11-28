package sokoban;

import sokoban.config.GameConfig;
import sokoban.core.*;
import sokoban.editor.*;
import sokoban.level.LevelParser;
import sokoban.solver.Solver;
import java.nio.file.*;
import java.util.*;
import static sokoban.Check.*;

/** 실제 턴·되돌리기·솔버·내보내기가 같은 자기장 규칙을 따르는지 검증한다. */
final class MagnetTest {
    public static void main(String[] args) { register(); finish(); }
    static void register() {
        test("자기장: 6축 흡인·거리·철 상자 부착", MagnetTest::sixAxes);
        test("자기장: 스위치 재진입 OFF·부착 해제·한 턴 되돌리기", MagnetTest::switchAndUndo);
        test("자기장: 벽·일반 상자·플레이어 차폐와 범위", MagnetTest::occlusion);
        test("자기장: 동률 장치·여러 철 상자·결정성", MagnetTest::multiple);
        test("자기장: 포탈 이동·출구 충돌·기존 구멍 소모", MagnetTest::portalAndHole);
        test("자기장: 포탈 순환 시 입력 전체 취소", MagnetTest::cycle);
        test("자기장: 상태 동등성·일반 밀기·메타데이터 검증", MagnetTest::stateAndParser);
        test("자기장: 에디터 TXT·프로젝트 왕복", MagnetTest::editorRoundTrip);
        test("자기장: 체험 맵 최소해 재생", MagnetTest::demoSolutions);
        test("자기장: 다층 렌더·모션·프리뷰 도착점", sokoban.ui.MagnetRenderTest::verify);
    }

    private static Level map(String... rows) throws Exception {
        List<String> lines=new ArrayList<>(List.of("id: magnet-test", "magnet.0,1,0: +X,7", "layer 0"));
        lines.addAll(List.of(rows)); return LevelParser.parse("magnet-test",lines);
    }
    private static Level demo(String name) throws Exception {
        Path path=Path.of("test", "fixtures",name+".txt"); return LevelParser.parse(name,Files.readAllLines(path));
    }
    private static StepResult move(Level level,GameState state,Direction dir) {
        StepResult r=Engine.step(level,state,dir);notNull(r,"expected move "+dir);return r;
    }
    static void sixAxes() {
        for(Direction face:Direction.values()) {
            int size=7,center=(3*size+3)*size+3,box=center+face.dx*3+face.dy*size*3+face.dz*size*size*3;
            TileType[] tiles=new TileType[size*size*size];Arrays.fill(tiles,TileType.EMPTY);
            tiles[center]=TileType.MAGNET;tiles[1]=TileType.MAGNET_SWITCH;tiles[tiles.length-1]=TileType.GOAL;
            Level lv=new Level("axis","axis",size,size,size,tiles,GameState.of(0,new int[]{box},new int[0],new int[]{box},false),
                    -1,"",null,null,Map.of(),"test",false,null,null,null,List.of(new Magnet(center,face,3)));
            StepResult r=move(lv,lv.initial,Direction.RIGHT);
            int expected=lv.neighbor(center,face);
            check(r.state().isIron(expected));eq(1,r.state().blockCount());check(Engine.isMagnetHeld(lv,r.state(),expected));
            eq(3,r.phases().size());check(!lv.initial.magnetOn);check(lv.initial.hasBlock(box));
            eq(r,Engine.step(lv,lv.initial,Direction.RIGHT));
        }
    }
    static void switchAndUndo() throws Exception {
        Level lv=demo("magnet-release-demo");Session session=new Session(lv,GameConfig.defaults());
        StepResult on=session.move(Direction.RIGHT);notNull(on);eq(1,session.moves());check(session.state().magnetOn);
        int attached=lv.idx(2,2,0);check(session.state().isIron(attached));
        isNull(session.move(Direction.DOWN));eq(BlockReason.Kind.MAGNET_HELD,Engine.whyBlocked(lv,session.state(),Direction.DOWN).kind());
        eq(1,session.moves());check(session.undo());eq(lv.initial,session.state());check(session.redo());eq(on.state(),session.state());
        notNull(session.move(Direction.RIGHT));check(session.state().magnetOn);
        notNull(session.move(Direction.LEFT));check(!session.state().magnetOn);
        check(session.undo());check(session.state().magnetOn);check(session.redo());check(!session.state().magnetOn);
        check(session.move(Direction.DOWN).cleared());session.reset();eq(lv.initial,session.state());eq(0,session.moves());
        Level switchBox=LevelParser.parse("box-switch",List.of("id: box-switch","layer 0","@$S-.")).withSolution(-1,"");
        check(!move(switchBox,switchBox.initial,Direction.RIGHT).state().magnetOn,"box must not toggle a switch");
    }
    static void occlusion() throws Exception {
        for(String row:List.of("M-#--I-.","M-$--I-.")) {
            Level lv=map("@S------",row);GameState s=move(lv,lv.initial,Direction.RIGHT).state();
            check(s.isIron(lv.idx(5,1,0)));if(row.contains("$"))check(s.hasBlock(lv.idx(2,1,0)));
        }
        Level range=LevelParser.parse("range",List.of("id: range","magnet.0,1,0: +X,3","layer 0","@S------","M----I-."));
        check(move(range,range.initial,Direction.RIGHT).state().isIron(range.idx(5,1,0)));
        Level player=map("--@-----","M-S--I-.");GameState on=move(player,player.initial,Direction.DOWN).state();
        check(on.isIron(player.idx(5,1,0)));GameState away=move(player,on,Direction.UP).state();
        check(away.isIron(player.idx(1,1,0)));check(away.magnetOn);
    }
    static void multiple() throws Exception {
        Level lv=map("@S------","M---II-.");GameState state=move(lv,lv.initial,Direction.RIGHT).state();
        check(state.isIron(lv.idx(1,1,0)));check(state.isIron(lv.idx(5,1,0)));eq(2,state.blockCount());
        Level tie=LevelParser.parse("tie",List.of("id: tie","magnet.0,1,0: +X,5","magnet.6,1,0: -X,5","layer 0","@S----.","M--I--M"));
        check(move(tie,tie.initial,Direction.RIGHT).state().isIron(tie.idx(1,1,0)));
    }
    static void portalAndHole() throws Exception {
        Level portal=map("@S-------","M-A-I---.","---------","-----A---");
        GameState arrival=move(portal,portal.initial,Direction.RIGHT).state();check(arrival.isIron(portal.idx(5,3,0)));
        Level blocked=map("@S-------","M-A-I---.","---------","-----A$--");
        GameState prepared=blocked.initial.withPush(blocked.initial.player,blocked.idx(6,3,0),blocked.idx(5,3,0));
        GameState stopped=move(blocked,prepared,Direction.RIGHT).state();check(stopped.isIron(blocked.idx(3,1,0)));
        Level player=map("--@------","M-A-I---.","---------","-----A---");
        GameState exit=move(player,player.initial.withMagnet(true),Direction.DOWN).state();
        eq(player.idx(5,3,0),exit.player);check(exit.isIron(player.idx(3,1,0)));
        Level hole=map("@S-------","M-O-I---.","-------$-");
        StepResult filled=move(hole,hole.initial,Direction.RIGHT);
        check(!filled.state().isHole(hole.idx(2,1,0)));eq(1,filled.state().blockCount());
        check(!filled.state().isIron(hole.idx(4,1,0)));check(filled.phases().stream().anyMatch(StepResult::blockVanished));
    }
    static void cycle() throws Exception {
        Level loop=map("@S-------","MAIA----.");
        isNull(Engine.step(loop,loop.initial,Direction.RIGHT));
        eq(BlockReason.Kind.FORCE_LOOP,Engine.whyBlocked(loop,loop.initial,Direction.RIGHT).kind());
        check(!loop.initial.magnetOn);check(loop.initial.isIron(loop.idx(2,1,0)));
    }
    static void stateAndParser() throws Exception {
        GameState iron=GameState.of(0,new int[]{1},new int[0],new int[]{1},false);
        check(!iron.equals(GameState.of(0,new int[]{1},new int[0])));
        check(!iron.equals(iron.withMagnet(true)));check(iron.withPush(1,1,2).isIron(2));
        Level lv=demo("magnet-vertical-demo");eq(lv.magnets,lv.withSolution(3,"RRR").magnets);
        for(String metadata:List.of("magnet.0,1,0: +Q,3","magnet.0,1,0: +X,0","magnet.0,1,0: +X,33","magnet.9,1,0: +X,3","magnet.1,0,0: +X,3")) {
            boolean rejected=false;try{LevelParser.parse("bad",List.of("id: bad",metadata,"layer 0","@S------","M----I-."));}catch(sokoban.level.LevelFormatException expected){rejected=true;}check(rejected,metadata);
        }
    }
    static void editorRoundTrip() throws Exception {
        BlockCatalog catalog=new BlockCatalog(Path.of("config/editor-blocks.properties"));
        for(String name:List.of("magnet-demo","magnet-release-demo","magnet-vertical-demo")) {
            Path file=Path.of("test", "fixtures",name+".txt");Level before=demo(name);EditorDocument document=EditorIO.importGame(file);
            Level after=LevelParser.parse("export",EditorIO.gameText(document,catalog).lines().toList());
            eq(before.initial,after.initial);eq(before.magnets,after.magnets);
            Path temp=Files.createTempFile("magnet-test-",".slevel");
            try{EditorIO.save(temp,document);check(document.same(EditorIO.load(temp)));}finally{Files.deleteIfExists(temp);}
        }
        EditorDocument fresh=EditorDocument.demo();fresh.place(2,2,1,catalog.get("magnet"),catalog);
        fresh.place(4,5,1,catalog.get("iron_box"),catalog);fresh.place(5,5,1,catalog.get("magnet_switch"),catalog);
        Level parsed=LevelParser.parse("fresh",EditorIO.gameText(fresh,catalog).lines().toList());
        eq(1,parsed.magnets.size());check(parsed.initial.isIron(parsed.idx(4,5,1)));
    }
    static void demoSolutions() throws Exception {
        String[] files={"magnet-demo","magnet-release-demo","magnet-vertical-demo"};String[] paths={"R","RRLD","RLRDRU"};
        for(int n=0;n<files.length;n++) {
            Level lv=demo(files[n]);GameState s=lv.initial;
            for(char c:paths[n].toCharArray())s=move(lv,s,Direction.fromSymbol(c)).state();
            check(Engine.isCleared(lv,s),files[n]);
            Solver.Result solution=Solver.solve(lv,200_000,5000);eq(Solver.Status.SOLVED,solution.status());
            s=lv.initial;for(char c:solution.path().toCharArray())s=move(lv,s,Direction.fromSymbol(c)).state();check(Engine.isCleared(lv,s));
            check(solution.moves()<=paths[n].length());
        }
    }
}
