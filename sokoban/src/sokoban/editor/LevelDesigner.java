package sokoban.editor;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import static sokoban.editor.EditorArt.*;

/** Swing level editor embedded in App, with an optional standalone entry point. */
public final class LevelDesigner {
    private JFrame frame;
    private Runnable returnToGame;
    private java.util.function.Consumer<sokoban.core.Level> playtest;
    public void setPlaytest(java.util.function.Consumer<sokoban.core.Level> action) { playtest=action; }
    private final Path catalogPath;
    private BlockCatalog catalog;
    private EditorDocument doc=EditorDocument.demo(),saved;
    private final Deque<EditorDocument> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private Path projectFile;
    private int floor,brush,page,selectedX=-1,selectedY=-1;
    private boolean ghost=true,onlyFloor;
    private final JLabel status=new JLabel(" "),boardTitle=new JLabel(),counter=new JLabel(),brushTitle=new JLabel();
    private final JPanel floorsPanel=new JPanel(),inspector=new JPanel();
    private final Board board=new Board();
    private final Preview preview=new Preview();
    private final Hotbar hotbar=new Hotbar();
    private final JLabel pageLabel=new JLabel();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(()-> {
            try { installTheme(); new LevelDesigner(Path.of("config/editor-blocks.properties")).show(); }
            catch(Exception e) { JOptionPane.showMessageDialog(null,e.getMessage(),"레벨 디자이너",JOptionPane.ERROR_MESSAGE); }
        });
    }
    public LevelDesigner(Path catalogPath) throws Exception { this.catalogPath=catalogPath; catalog=new BlockCatalog(catalogPath); installTheme(); }
    private static void installTheme() {
        for(Object key:Collections.list(UIManager.getDefaults().keys())) if(UIManager.get(key) instanceof Font) UIManager.put(key,font(Font.PLAIN,13));
        UIManager.put("Panel.background",PANEL); UIManager.put("Label.foreground",TEXT);
        UIManager.put("OptionPane.background",PANEL); UIManager.put("OptionPane.messageForeground",TEXT);
        UIManager.put("TextField.background",BG); UIManager.put("TextField.foreground",TEXT); UIManager.put("TextField.caretForeground",TEXT);
        UIManager.put("TextArea.background",BG); UIManager.put("TextArea.foreground",TEXT);
        UIManager.put("List.background",BG); UIManager.put("List.foreground",TEXT);
        UIManager.put("CheckBox.background",PANEL); UIManager.put("CheckBox.foreground",TEXT);
    }
    private void show() {
        frame=new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){ public void windowClosing(WindowEvent e){ if(discardOkay())frame.dispose(); }});
        mount();
    }
    /** Embed into the existing game window, restoring its canvas and close behavior on return. */
    public void openIn(JFrame host,Runnable onReturn) {
        frame=host;
        Container previous=host.getContentPane(); Dimension size=host.getSize(),minimum=host.getMinimumSize();
        String title=host.getTitle(); int closeOperation=host.getDefaultCloseOperation();
        WindowAdapter guard=new WindowAdapter(){public void windowClosing(WindowEvent e){if(discardOkay())System.exit(0);}};
        returnToGame=()->{if(!discardOkay())return;host.removeWindowListener(guard);host.setContentPane(previous);host.setMinimumSize(minimum);host.setSize(size);host.setTitle(title);host.setDefaultCloseOperation(closeOperation);host.revalidate();host.repaint();onReturn.run();};
        host.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);host.addWindowListener(guard);mount();
    }
    private void mount() {
        JPanel root=new JPanel(new BorderLayout(12,12)); root.setBackground(BG); root.setBorder(new EmptyBorder(16,18,12,18));
        root.add(header(),BorderLayout.NORTH); root.add(workspace(),BorderLayout.CENTER); root.add(bottom(),BorderLayout.SOUTH);
        frame.setContentPane(root); shortcuts(root); refresh();
        frame.setSize(1440,930); frame.setMinimumSize(new Dimension(1120,760)); frame.setLocationRelativeTo(null); frame.setVisible(true);
    }
    private JPanel header() {
        JPanel p=new JPanel(new BorderLayout()); p.setOpaque(false);
        JPanel titles=new JPanel(new GridLayout(2,1,0,3)); titles.setOpaque(false);
        JLabel title=new JLabel("레벨 디자이너"); title.setFont(font(Font.BOLD,22)); titles.add(title);
        JLabel sub=new JLabel("층을 고르고, 블록을 놓고, 공간을 설계하세요."); sub.setForeground(DIM); titles.add(sub); p.add(titles,BorderLayout.WEST);
        JPanel buttons=row(); if(returnToGame!=null)buttons.add(button("게임으로",returnToGame));buttons.add(button("새 맵",()->newMap())); buttons.add(button("열기",()->open()));
        buttons.add(button("저장",()->save(false))); buttons.add(button("다른 이름",()->save(true)));
        if(playtest!=null)buttons.add(button("플레이 테스트",()->testLevel()));
        JButton export=button("게임 TXT 내보내기",()->export()); export.setBackground(new Color(0x514873)); buttons.add(export);
        p.add(buttons,BorderLayout.EAST); return p;
    }
    private JPanel workspace() {
        JPanel area=new JPanel(new BorderLayout(12,0)); area.setOpaque(false);
        JPanel left=card(new BorderLayout(0,12)); left.setPreferredSize(new Dimension(174,400));
        JPanel top=new JPanel(new GridLayout(0,1,0,9)); top.setOpaque(false); top.add(label("층 목록",DIM,11)); top.add(label("편집할 층",TEXT,18));
        JPanel floorTools=row(); floorTools.add(button("+ 층",()->addFloor())); floorTools.add(button("− 층",()->removeFloor())); top.add(floorTools);
        left.add(top,BorderLayout.NORTH); floorsPanel.setLayout(new BoxLayout(floorsPanel,BoxLayout.Y_AXIS)); floorsPanel.setOpaque(false);
        JScrollPane layerScroll=scroll(floorsPanel); left.add(layerScroll,BorderLayout.CENTER);
        JPanel leftBottom=new JPanel(new GridLayout(0,1,0,7)); leftBottom.setOpaque(false);
        leftBottom.add(label("PgUp / PgDn  층 전환",DIM,11)); leftBottom.add(button("맵 설정",()->settings())); left.add(leftBottom,BorderLayout.SOUTH);
        area.add(left,BorderLayout.WEST);
        JPanel middle=card(new BorderLayout(0,8)); JPanel tool=row(); boardTitle.setFont(font(Font.BOLD,17)); tool.add(boardTitle);
        JCheckBox ghostBox=new JCheckBox("다른 층 함께 보기",true); ghostBox.setOpaque(false);ghostBox.setForeground(TEXT); ghostBox.addActionListener(e->{ghost=ghostBox.isSelected();board.repaint();});tool.add(ghostBox);
        tool.add(button("좌 회전",()->board.rotate(1)));tool.add(button("우 회전",()->board.rotate(-1)));
        tool.add(button("되돌리기",()->undo())); tool.add(button("다시 하기",()->redo())); middle.add(tool,BorderLayout.NORTH); middle.add(board,BorderLayout.CENTER);
        JPanel guide=row(); guide.add(label("좌클릭·드래그 배치   /   우클릭 지우기   /   휠클릭 블록 복사   /   Shift+클릭 속성 선택",DIM,11));middle.add(guide,BorderLayout.SOUTH); area.add(middle,BorderLayout.CENTER);
        JPanel right=new JPanel(new BorderLayout(0,12));right.setOpaque(false);right.setPreferredSize(new Dimension(294,450));
        JPanel overview=card(new BorderLayout(0,5));JPanel overTitle=row();overTitle.add(label("게임 화면 미리보기",DIM,12));overview.add(overTitle,BorderLayout.NORTH);
        preview.setPreferredSize(new Dimension(250,225));overview.add(preview,BorderLayout.CENTER);
        JCheckBox isolation=new JCheckBox("선택 층만 보기");isolation.setOpaque(false);isolation.addActionListener(e->{onlyFloor=isolation.isSelected();preview.repaint();});overview.add(isolation,BorderLayout.SOUTH);right.add(overview,BorderLayout.NORTH);
        inspector.setLayout(new BoxLayout(inspector,BoxLayout.Y_AXIS));inspector.setBackground(PANEL);JPanel details=card(new BorderLayout());details.add(scroll(inspector));right.add(details,BorderLayout.CENTER);area.add(right,BorderLayout.EAST);return area;
    }
    private JPanel bottom() {
        JPanel p=new JPanel(new BorderLayout(0,7));p.setOpaque(false); JPanel tray=card(new BorderLayout(10,0));
        JPanel caption=new JPanel(new GridLayout(0,1,0,4));caption.setOpaque(false);caption.setPreferredSize(new Dimension(140,70));
        caption.add(label("블록 목록",DIM,10));brushTitle.setFont(font(Font.BOLD,16));caption.add(brushTitle);caption.add(label("1–9 선택  ·  0 지우개",DIM,11));tray.add(caption,BorderLayout.WEST);
        hotbar.setPreferredSize(new Dimension(800,86));tray.add(hotbar,BorderLayout.CENTER);
        JPanel controls=new JPanel(new GridLayout(0,1,0,4));controls.setOpaque(false);controls.add(button("전체 블록  E",()->library()));
        JPanel paging=row();paging.add(button("‹",()->page(-1)));paging.add(pageLabel);paging.add(button("›",()->page(1)));controls.add(paging);tray.add(controls,BorderLayout.EAST);p.add(tray,BorderLayout.CENTER);
        JPanel foot=new JPanel(new BorderLayout());foot.setOpaque(false);status.setForeground(DIM);status.setFont(font(Font.PLAIN,11));counter.setForeground(DIM);counter.setFont(font(Font.PLAIN,11));foot.add(status,BorderLayout.CENTER);foot.add(counter,BorderLayout.EAST);p.add(foot,BorderLayout.SOUTH);return p;
    }
    private static JPanel row(){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,7,0));p.setOpaque(false);return p;}
    private static JPanel card(LayoutManager layout){JPanel p=new JPanel(layout);p.setBackground(PANEL);p.setBorder(new CompoundBorder(new LineBorder(LINE,1,true),new EmptyBorder(12,12,12,12)));return p;}
    private static JLabel label(String text,Color c,int size){JLabel l=new JLabel(text);l.setForeground(c);l.setFont(font(Font.PLAIN,size));return l;}
    private static JButton button(String text,Runnable action){JButton b=new JButton(text);b.setFocusPainted(false);b.setBackground(new Color(0x253148));b.setForeground(TEXT);b.setBorder(new CompoundBorder(new LineBorder(LINE),new EmptyBorder(7,10,7,10)));b.addActionListener(e->action.run());return b;}
    private static JScrollPane scroll(Component c){JScrollPane s=new JScrollPane(c);s.setBorder(null);s.getViewport().setBackground(PANEL);s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);s.getVerticalScrollBar().setUnitIncrement(20);return s;}
    private BlockCatalog.Block brushBlock(){return catalog.all().get(brush);}
    private void select(int index){if(index<0||index>=catalog.all().size())return;brush=index;page=index/9;hotbar.repaint();board.repaint();brushTitle.setText(brushBlock().name());pageLabel.setText((page+1)+" / "+((catalog.all().size()+8)/9));showInspector();}
    private void page(int delta){int pages=(catalog.all().size()+8)/9;page=Math.floorMod(page+delta,pages);hotbar.repaint();pageLabel.setText((page+1)+" / "+pages);}
    private void chooseFloor(int z){floor=Math.max(0,Math.min(doc.floors-1,z));selectedX=selectedY=-1;refresh();}
    private void refresh(){
        floor=Math.max(0,Math.min(floor,doc.floors-1));frame.setTitle((doc.same(saved)?"":"● ")+doc.name+" — 소코반 레벨 디자이너");
        boardTitle.setForeground(TEXT);brushTitle.setForeground(TEXT);boardTitle.setText((floor+1)+"층 · 3D 편집");counter.setText(doc.width+" × "+doc.depth+" × "+doc.floors+"층");
        floorsPanel.removeAll();for(int z=doc.floors-1;z>=0;z--){final int layer=z;JButton b=new JButton((z+1)+"층     "+doc.count(z)+"개");b.setFont(font(Font.BOLD,16));b.setForeground(z==floor?TEXT:DIM);b.setBackground(z==floor?new Color(0x494266):BG);b.setFocusPainted(false);b.setBorder(new CompoundBorder(new LineBorder(z==floor?ACCENT:LINE),new EmptyBorder(19,12,19,12)));b.setMaximumSize(new Dimension(180,62));b.addActionListener(e->chooseFloor(layer));floorsPanel.add(b);floorsPanel.add(Box.createVerticalStrut(9));}
        floorsPanel.revalidate();floorsPanel.repaint();board.repaint();preview.repaint();select(brush);
    }
    private void change(Runnable action){EditorDocument before=doc.copy();action.run();remember(before);refresh();}
    private void remember(EditorDocument before){if(!doc.same(before)){undo.push(before);while(undo.size()>100)undo.removeLast();redo.clear();}}
    private void undo(){if(undo.isEmpty())return;redo.push(doc.copy());doc=undo.pop();refresh();status.setText("되돌렸습니다.");}
    private void redo(){if(redo.isEmpty())return;undo.push(doc.copy());doc=redo.pop();refresh();status.setText("다시 적용했습니다.");}
    private void addFloor(){if(doc.floors==16){status.setText("최대 16층까지 만들 수 있습니다.");return;}change(()->{doc=doc.resized(doc.width,doc.depth,doc.floors+1);floor=doc.floors-1;});}
    private void removeFloor(){if(doc.floors==1)return;if(JOptionPane.showConfirmDialog(frame,"맨 위 "+doc.floors+"층을 삭제할까요? 되돌리기로 복원할 수 있습니다.","층 삭제",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;change(()->doc=doc.resized(doc.width,doc.depth,doc.floors-1));}
    private boolean discardOkay(){if(doc.same(saved))return true;int result=JOptionPane.showConfirmDialog(frame,"변경한 프로젝트를 저장할까요?","저장하지 않은 변경",JOptionPane.YES_NO_CANCEL_OPTION);return result==JOptionPane.NO_OPTION||(result==JOptionPane.YES_OPTION&&save(false));}
    private void newMap(){if(!discardOkay())return;var next=dimensionsDialog(null);if(next==null)return;doc=next;projectFile=null;saved=null;undo.clear();redo.clear();floor=0;selectedX=selectedY=-1;refresh();}
    private void settings(){var next=dimensionsDialog(doc);if(next==null)return;change(()->doc=next);}
    private EditorDocument dimensionsDialog(EditorDocument original){
        JTextField id=new JTextField(original==null?"custom01":original.id),name=new JTextField(original==null?"새 레벨":original.name);
        JSpinner w=new JSpinner(new SpinnerNumberModel(original==null?10:original.width,1,32,1)),d=new JSpinner(new SpinnerNumberModel(original==null?8:original.depth,1,32,1)),h=new JSpinner(new SpinnerNumberModel(original==null?3:original.floors,1,16,1));
        JCheckBox pull=new JCheckBox("당기기 허용 메타데이터",original!=null&&original.pull);
        JPanel panel=new JPanel(new GridLayout(0,2,12,12));panel.add(new JLabel("레벨 ID"));panel.add(id);panel.add(new JLabel("이름"));panel.add(name);panel.add(new JLabel("가로 (X)"));panel.add(w);panel.add(new JLabel("세로 (Y)"));panel.add(d);panel.add(new JLabel("층 수 (Z)"));panel.add(h);panel.add(pull);panel.add(new JLabel("게임 동작 구현과 별도"));
        if(JOptionPane.showConfirmDialog(frame,panel,"맵 설정",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;
        if(id.getText().isBlank()||name.getText().isBlank()){error(new Exception("ID와 이름을 입력하세요."));return null;}
        int width=(int)w.getValue(),depth=(int)d.getValue(),height=(int)h.getValue();
        if(original!=null&&(width<original.width||depth<original.depth||height<original.floors)&&JOptionPane.showConfirmDialog(frame,"축소한 범위 밖의 블록은 삭제됩니다. 계속할까요?","크기 축소",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;
        EditorDocument next=original==null?new EditorDocument(width,depth,height,id.getText().trim(),name.getText().trim()):original.resized(width,depth,height);
        next.id=id.getText().trim();next.name=name.getText().trim();next.pull=pull.isSelected();return next;
    }
    private void open(){
        if(!discardOkay())return;JFileChooser chooser=new JFileChooser(Path.of("levels").toFile());chooser.setFileFilter(new FileNameExtensionFilter("프로젝트 / 게임 레벨 (*.slevel, *.txt)","slevel","txt"));
        if(chooser.showOpenDialog(frame)!=JFileChooser.APPROVE_OPTION)return;
        try{Path file=chooser.getSelectedFile().toPath();boolean nativeFile=file.toString().toLowerCase(Locale.ROOT).endsWith(".slevel");EditorDocument next=nativeFile?EditorIO.load(file):EditorIO.importGame(file);
            doc=next;projectFile=nativeFile?file:null;saved=nativeFile?doc.copy():null;undo.clear();redo.clear();floor=0;selectedX=selectedY=-1;refresh();status.setText("열었습니다: "+file.getFileName());
        }catch(Exception ex){error(ex);}
    }
    private Path savePath(String extension,String description,String filename){JFileChooser chooser=new JFileChooser(Path.of("levels").toFile());chooser.setFileFilter(new FileNameExtensionFilter(description,extension));chooser.setSelectedFile(new java.io.File(filename));if(chooser.showSaveDialog(frame)!=JFileChooser.APPROVE_OPTION)return null;Path file=chooser.getSelectedFile().toPath();if(!file.toString().toLowerCase(Locale.ROOT).endsWith("."+extension))file=file.resolveSibling(file.getFileName()+"."+extension);if(Files.exists(file)&&JOptionPane.showConfirmDialog(frame,"기존 파일을 덮어쓸까요?\n"+file.getFileName(),"덮어쓰기",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;return file;}
    private boolean save(boolean saveAs){try{Path file=saveAs||projectFile==null?savePath("slevel","편집 프로젝트 (*.slevel)",doc.id+".slevel"):projectFile;if(file==null)return false;EditorIO.save(file,doc);projectFile=file;saved=doc.copy();refresh();status.setText("프로젝트 저장 완료 · "+file.toAbsolutePath());return true;}catch(Exception ex){error(ex);return false;}}
    private void export(){try{String text=EditorIO.gameText(doc,catalog);Path file=savePath("txt","게임 레벨 (*.txt)",doc.id+".txt");if(file==null)return;EditorIO.writeAtomic(file,text);status.setText("게임 TXT 저장 완료 · 형식 검사 통과 / 최적해·풀이 가능 여부는 미검증");}catch(Exception ex){error(ex);}}
    private void testLevel(){try{String text=EditorIO.gameText(doc,catalog);playtest.accept(sokoban.level.LevelParser.parse("편집 맵",text.lines().toList()));}catch(Exception ex){error(ex);}}
    private void error(Exception ex){JOptionPane.showMessageDialog(frame,ex.getMessage(),"확인 필요",JOptionPane.WARNING_MESSAGE);}
    private void showInspector(){
        inspector.removeAll();addDetail(label("선택한 블록",DIM,10));addDetail(label(brushBlock().name(),TEXT,20));
        JTextArea description=new JTextArea(brushBlock().description());description.setLineWrap(true);description.setWrapStyleWord(true);description.setEditable(false);description.setFocusable(false);description.setBackground(PANEL);description.setForeground(DIM);description.setBorder(new EmptyBorder(4,0,10,0));description.setMaximumSize(new Dimension(260,90));addDetail(description);
        addDetail(label(brushBlock().exportable()?"게임 TXT 형식 지원":"설계용 · 프로젝트 저장 지원",brushBlock().exportable()?CYAN:ACCENT,11));
        if(selectedX>=0&&selectedY>=0&&selectedX<doc.width&&selectedY<doc.depth){
            var cell=doc.cell(selectedX,selectedY,floor);addDetail(Box.createVerticalStrut(18));addDetail(label("선택한 칸  "+(selectedX+1)+", "+(selectedY+1)+"  /  "+(floor+1)+"층",DIM,11));
            addDetail(label("지형 · "+catalog.display(cell.terrain()).name(),TEXT,13));
            if(!cell.entity().isEmpty())addDetail(label("개체 · "+catalog.display(cell.entity()).name(),TEXT,13));
            Map<String,JTextField> terrain=propertyFields(cell.terrain(),cell.terrainProps()),entity=propertyFields(cell.entity(),cell.entityProps());
            if(!terrain.isEmpty()||!entity.isEmpty())addDetail(button("속성 적용",()->change(()->{var current=doc.cell(selectedX,selectedY,floor);Map<String,String> tp=new LinkedHashMap<>(),ep=new LinkedHashMap<>();terrain.forEach((k,v)->tp.put(k,v.getText()));entity.forEach((k,v)->ep.put(k,v.getText()));doc.set(selectedX,selectedY,floor,new EditorDocument.Cell(current.terrain(),current.entity(),tp,ep));})));
            if(!cell.entity().isEmpty())addDetail(button("이 칸의 개체만 지우기",()->change(()->{var c=doc.cell(selectedX,selectedY,floor);doc.set(selectedX,selectedY,floor,new EditorDocument.Cell(c.terrain(),"",c.terrainProps(),Map.of()));})));
        }else{addDetail(Box.createVerticalStrut(15));addDetail(label("Shift+클릭으로 배치된 블록 선택",DIM,11));}
        inspector.revalidate();inspector.repaint();
    }
    private Map<String,JTextField> propertyFields(String type,Map<String,String> stored){Map<String,JTextField> fields=new LinkedHashMap<>();if(type.isEmpty())return fields;Map<String,String> values=new LinkedHashMap<>(catalog.display(type).defaults());values.putAll(stored);for(var e:values.entrySet()){addDetail(label(e.getKey(),DIM,11));JTextField input=new JTextField(e.getValue());input.setMaximumSize(new Dimension(250,29));fields.put(e.getKey(),input);addDetail(input);}return fields;}
    private void addDetail(Component c){if(c instanceof JComponent j)j.setAlignmentX(Component.LEFT_ALIGNMENT);inspector.add(c);inspector.add(Box.createVerticalStrut(6));}
    private void library(){
        JDialog dialog=new JDialog(frame,"블록 라이브러리",true);JPanel panel=card(new BorderLayout(8,12));JTextField search=new JTextField();search.setToolTipText("블록 이름 또는 분류 검색");JPanel top=new JPanel(new BorderLayout(8,0));top.add(label("블록 검색",DIM,12),BorderLayout.WEST);top.add(search);panel.add(top,BorderLayout.NORTH);
        DefaultListModel<BlockCatalog.Block> model=new DefaultListModel<>();catalog.all().forEach(model::addElement);JList<BlockCatalog.Block> list=new JList<>(model);list.setFixedCellHeight(60);
        list.setCellRenderer((l,b,i,selected,focus)->{JLabel label=new JLabel("   "+b.name()+"   /   "+b.category()+(b.exportable()?"":"   · 설계용"));label.setOpaque(true);label.setBackground(selected?new Color(0x494266):BG);label.setForeground(TEXT);label.setIcon(new Icon(){public int getIconWidth(){return 52;}public int getIconHeight(){return 52;}public void paintIcon(Component c,Graphics g,int x,int y){Graphics2D g2=(Graphics2D)g.create();quality(g2);EditorArt.icon(g2,b,x,y,48);g2.dispose();}});return label;});
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}public void changedUpdate(DocumentEvent e){filter();}private void filter(){model.clear();String q=search.getText().trim().toLowerCase(Locale.ROOT);catalog.all().stream().filter(b->(b.name()+b.id()+b.category()).toLowerCase(Locale.ROOT).contains(q)).forEach(model::addElement);}});
        Runnable choose=()->{var b=list.getSelectedValue();if(b!=null){select(catalog.all().indexOf(b));dialog.dispose();board.requestFocusInWindow();}};
        list.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)choose.run();}});list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"),"select");list.getActionMap().put("select",new AbstractAction(){public void actionPerformed(ActionEvent e){choose.run();}});panel.add(scroll(list));
        JPanel actions=row();actions.add(button("블록 선택",choose));actions.add(button("정의 다시 불러오기",()->{try{String id=brushBlock().id();BlockCatalog next=new BlockCatalog(catalogPath);catalog=next;brush=Math.max(0,next.all().indexOf(next.get(id)));page=brush/9;refresh();dialog.dispose();status.setText("블록 정의를 다시 불러왔습니다.");}catch(Exception ex){error(ex);}}));panel.add(actions,BorderLayout.SOUTH);dialog.add(panel);dialog.setSize(540,610);dialog.setLocationRelativeTo(frame);dialog.setVisible(true);
    }
    private void shortcuts(JPanel root){
        for(int i=1;i<=9;i++){final int slot=i-1;bind(root,"pressed "+i,()->select(page*9+slot),true);bind(root,"pressed NUMPAD"+i,()->select(page*9+slot),true);}
        bind(root,"pressed 0",()->select(catalog.all().indexOf(catalog.get("empty"))),true);bind(root,"pressed E",()->library(),true);
        bind(root,"pressed PAGE_UP",()->chooseFloor(floor+1),true);bind(root,"pressed PAGE_DOWN",()->chooseFloor(floor-1),true);
        bind(root,"pressed LEFT",()->board.rotate(1),true);bind(root,"pressed RIGHT",()->board.rotate(-1),true);
        bind(root,"ctrl Z",()->undo(),true);bind(root,"ctrl Y",()->redo(),true);bind(root,"ctrl S",()->save(false),false);bind(root,"ctrl O",()->open(),false);
        bind(root,"pressed OPEN_BRACKET",()->page(-1),true);bind(root,"pressed CLOSE_BRACKET",()->page(1),true);
    }
    private void bind(JPanel root,String key,Runnable action,boolean skipText){root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key),key);root.getActionMap().put(key,new AbstractAction(){public void actionPerformed(ActionEvent e){if(skipText&&KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextComponent)return;action.run();}});}

    private final class Board extends JPanel {
        int hx=-1,hy=-1;boolean painting,erasing;EditorDocument beforeStroke;
        Board(){setBackground(BG);setFocusable(true);MouseAdapter mouse=new MouseAdapter(){
            public void mouseMoved(MouseEvent e){locate(e);repaint();}
            public void mouseExited(MouseEvent e){hx=hy=-1;repaint();}
            public void mousePressed(MouseEvent e){requestFocusInWindow();locate(e);if(hx<0)return;selectedX=hx;selectedY=hy;
                if(e.isShiftDown()){showInspector();repaint();return;}
                if(SwingUtilities.isMiddleMouseButton(e)){var c=doc.cell(hx,hy,floor);var b=catalog.get(c.entity().isEmpty()?c.terrain():c.entity());if(b!=null)select(catalog.all().indexOf(b));return;}
                if(!SwingUtilities.isLeftMouseButton(e)&&!SwingUtilities.isRightMouseButton(e))return;
                beforeStroke=doc.copy();painting=true;erasing=SwingUtilities.isRightMouseButton(e);paintCell();}
            public void mouseDragged(MouseEvent e){locate(e);if(painting)paintCell();repaint();}
            public void mouseReleased(MouseEvent e){if(painting){painting=false;remember(beforeStroke);refresh();}}
        };addMouseListener(mouse);addMouseMotionListener(mouse);}
        final sokoban.ui.EditorScene scene=new sokoban.ui.EditorScene();
        Rectangle area(){return new Rectangle(30,20,Math.max(1,getWidth()-60),Math.max(1,getHeight()-65));}
        void rotate(int step){scene.rotate(step);preview.scene.rotate(step);hx=hy=-1;repaint();preview.repaint();}
        void locate(MouseEvent e){scene.configure(doc,area(),floor,!ghost);Point p=scene.pick(doc,floor,e.getX(),e.getY());hx=p==null?-1:p.x;hy=p==null?-1:p.y;}
        void paintCell(){if(hx<0)return;selectedX=hx;selectedY=hy;var b=erasing?catalog.get("empty"):brushBlock();var old=doc.cell(hx,hy,floor);if(b.entity()&&!catalog.display(old.terrain()).supportsEntity()){status.setText("이 지형에는 개체를 겹칠 수 없습니다. 빈 칸 또는 목표를 선택하세요.");return;}doc.place(hx,hy,floor,b,catalog);status.setText((floor+1)+"층  X "+(hx+1)+" · Y "+(hy+1)+"  /  "+b.name());repaint();preview.repaint();}
        protected void paintComponent(Graphics original){super.paintComponent(original);Graphics2D g=(Graphics2D)original.create();quality(g);
            scene.paint(g,doc,catalog,area(),floor,!ghost);
            if(selectedX>=0&&selectedY>=0&&selectedX<doc.width&&selectedY<doc.depth){var p=scene.project(selectedX,selectedY,floor);g.setColor(ACCENT);g.setStroke(new BasicStroke(1.7f));g.draw(p.edges());}
            if(hx>=0&&hy>=0){var p=scene.project(hx,hy,floor);g.setColor(new Color(99,217,208,45));g.fill(p.shape());g.setColor(CYAN);g.setStroke(new BasicStroke(2));g.draw(p.edges());
                g.setFont(font(Font.BOLD,12));center(g,(floor+1)+"층 · "+(hx+1)+", "+(hy+1),(int)p.center().getX(),(int)p.shape().getBounds2D().getMinY()-8);}
            g.setColor(DIM);g.setFont(font(Font.PLAIN,12));center(g,"선택한 "+(floor+1)+"층에만 배치됩니다  ·  좌우 방향키: 45° 회전",getWidth()/2,getHeight()-14);g.dispose();
        }
    }
    private final class Hotbar extends JPanel {
        Hotbar(){setOpaque(false);addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){int slot=e.getX()*9/Math.max(1,getWidth());select(page*9+slot);board.requestFocusInWindow();}});}
        protected void paintComponent(Graphics original){Graphics2D g=(Graphics2D)original.create();quality(g);int w=getWidth()/9;for(int i=0;i<9;i++){int index=page*9+i,x=i*w+3;boolean chosen=index==brush;g.setColor(chosen?new Color(0x393452):BG);g.fillRoundRect(x,2,w-6,getHeight()-4,8,8);g.setColor(chosen?ACCENT:LINE);g.setStroke(new BasicStroke(chosen?2:1));g.drawRoundRect(x,2,w-6,getHeight()-4,8,8);g.setFont(font(Font.BOLD,10));g.setColor(chosen?ACCENT:DIM);g.drawString(""+(i+1),x+7,16);if(index<catalog.all().size()){var b=catalog.all().get(index);int s=Math.min(44,w-10);icon(g,b,x+(w-6-s)/2,12,s);g.setFont(font(Font.PLAIN,11));g.setColor(TEXT);String name=b.name();while(g.getFontMetrics().stringWidth(name)>w-12&&name.length()>1)name=name.substring(0,name.length()-1);center(g,name,x+(w-6)/2,getHeight()-12);if(!b.exportable()){g.setColor(ACCENT);g.fillOval(x+w-18,9,5,5);}}}g.dispose();}
    }
    private final class Preview extends JPanel {
        final sokoban.ui.EditorScene scene=new sokoban.ui.EditorScene();
        Preview(){setOpaque(false);}
        protected void paintComponent(Graphics original){super.paintComponent(original);Graphics2D g=(Graphics2D)original.create();quality(g);
            scene.paint(g,doc,catalog,new Rectangle(8,8,Math.max(1,getWidth()-16),Math.max(1,getHeight()-30)),floor,onlyFloor);
            g.setColor(DIM);g.setFont(font(Font.PLAIN,10));center(g,"기존 게임의 렌더러 · 좌표 · 블록 모양 사용",getWidth()/2,getHeight()-3);g.dispose();
        }
    }
}
