package sokoban.ui;

import sokoban.core.*;
import sokoban.editor.*;
import sokoban.level.LevelParser;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Exercises real renderer, projected mouse targets, and editor adapter, including multi-box phases. */
public final class MagnetRenderTest {
    private static Level read(String name) throws Exception {
        return LevelParser.parse(name,Files.readAllLines(Path.of("test", "fixtures",name+".txt")));
    }
    public static void verify() throws Exception {
        Level lv=read("magnet-vertical-demo");StepResult step=Engine.step(lv,lv.initial,Direction.RIGHT);
        if(step==null||step.phases().isEmpty())throw new AssertionError("missing motion phases");
        BlockReason held = Engine.whyBlocked(lv, step.state(), Direction.RIGHT);
        if (held == null || held.kind() != BlockReason.Kind.MAGNET_HELD
                || !BlockText.text(lv, step.state(), held).equals("철 상자 고정됨"))
            throw new AssertionError("fixed iron box must explain why it cannot be pushed");
        if (Engine.whyBlocked(lv, step.state().withMagnet(false), Direction.RIGHT) != null)
            throw new AssertionError("power OFF must release the iron box");
        if (!java.util.Arrays.equals(frame(lv, lv.initial, 0), frame(lv, lv.initial, 550)))
            throw new AssertionError("power OFF must have no animated magnetic effect");
        if (java.util.Arrays.equals(frame(lv, step.state(), 0), frame(lv, step.state(), 550)))
            throw new AssertionError("power ON must visibly animate");
        IsoRenderer renderer=new IsoRenderer();Camera camera=new Camera();camera.snapToDefaultView();
        Rectangle area=new Rectangle(0,0,720,520);
        MouseMoves lockedMouse = new MouseMoves();
        lockedMouse.layout(lv, step.state(), renderer, camera, area);
        var boxCenter = renderer.projectCell(3, 2, 1, false).center();
        var lockedTarget = lockedMouse.pick((int)Math.round(boxCenter.getX()), (int)Math.round(boxCenter.getY()));
        if (lockedTarget == null || lockedTarget.available()
                || !lockedMouse.description(lockedTarget, lv).contains("철 상자 고정됨"))
            throw new AssertionError("mouse target must show the same fixed-box explanation");
        BufferedImage image=new BufferedImage(720,520,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        for(int angle=0;angle<8;angle++) {
            camera.yaw=camera.targetYaw=angle*45;
            renderer.tickEffects(211);
            for(double t:new double[]{0,.25,.5,.75,1})renderer.render(g,lv,step.state(),new IsoRenderer.Anim(step,t),area,camera,false);
            var editor=new EditorScene();editor.paint(g,EditorIO.importGame(Path.of("test", "fixtures","magnet-vertical-demo.txt")),
                    new BlockCatalog(Path.of("config/editor-blocks.properties")),area,1,false);
        }
        // A manually pushed iron box is then pulled to a different final cell. The preview must use that final cell.
        Level manual=LevelParser.parse("manual",List.of("id: manual","magnet.0,2,0: +X,7","layer 0","-----@--","-----I--","M------."));
        GameState state=manual.initial.withMagnet(true);
        StepResult pushed=Engine.step(manual,state,Direction.DOWN);
        int expected=manual.idx(1,2,0);
        if(pushed==null||pushed.blockTo()!=expected||!pushed.state().isIron(expected))throw new AssertionError("preview must include automatic movement");
        camera.snapToTopView();MouseMoves mouse=new MouseMoves();mouse.layout(manual,state,renderer,camera,area);
        var point=renderer.projectCell(5,1,0,true).center();var target=mouse.pick((int)point.getX(),(int)point.getY());
        if(target==null||target.preview().blockTo()!=expected)throw new AssertionError("hover destination differs from actual move");
        g.dispose();
    }

    private static int[] frame(Level level, GameState state, long millis) {
        BufferedImage image = new BufferedImage(720, 520, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        Camera camera = new Camera(); camera.snapToDefaultView();
        IsoRenderer renderer = new IsoRenderer(); renderer.tickEffects(millis);
        renderer.render(g, level, state, null, new Rectangle(0, 0, 720, 520), camera, false);
        g.dispose();
        return image.getRGB(0, 0, 720, 520, null, 0, 720);
    }
    /** Optional local visual inspection artifact, kept outside the repository. */
    public static void main(String[] args) throws Exception {
        verify();
        if(args.length==0)return;
        Level lv=read("magnet-vertical-demo");StepResult step=Engine.step(lv,lv.initial,Direction.RIGHT);
        BufferedImage image=new BufferedImage(1440,620,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        g.setColor(Theme.BG_BOTTOM);g.fillRect(0,0,1440,620);
        Camera camera=new Camera();camera.snapToDefaultView();IsoRenderer renderer=new IsoRenderer();
        for(int i=0;i<2;i++) {
            Graphics2D panel=(Graphics2D)g.create(i*720,0,720,620);
            panel.setColor(Theme.TEXT);panel.setFont(Theme.font(Font.BOLD,22));Gfx.centered(panel,i==0?"자기장 OFF · 3층 철 상자":"자기장 ON · 2층에 부착",360,36);
            renderer.render(panel,lv,i==0?lv.initial:step.state(),null,new Rectangle(20,65,680,530),camera,false);panel.dispose();
        }
        g.dispose();ImageIO.write(image,"png",Path.of(args[0]).toFile());
    }
}
