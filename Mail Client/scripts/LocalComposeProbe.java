import com.mailapp.client.model.PublicConfigDto;
import com.mailapp.client.net.ApiClient;
import com.mailapp.client.ui.dialogs.ComposeDialog;
import com.mailapp.client.ui.theme.Theme;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone local component observation, deliberately no mailbox, login, session or send. */
class LocalComposeProbe {
    private static Path root, evidence;
    private static ComposeDialog dialog;
    private static Robot robot;
    private static List<Component> controls = List.of();
    private static final AtomicReference<Component> observedPress = new AtomicReference<>();
    private static final PublicConfigDto ON = new PublicConfigDto(false,false,false,false,true,1,1,20);
    private static final PublicConfigDto OFF = new PublicConfigDto(false,false,false,false,false,1,1,20);

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Need a desktop and new evidence directory");
        evidence = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectory(evidence);
        root = Files.createTempDirectory("mailapp-compose-local-").toRealPath();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { deleteFixture(); } catch (Exception ignored) { }
        }));
        boolean incomplete = false;
        try (ServerSocket reserved = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            // No HTTP listener and no authenticated account. Isolate all local files/preferences.
            System.setProperty("user.home", root.toString());
            System.setProperty("mailclient.config.dir", root.resolve("config").toString());
            Files.createDirectory(root.resolve("config"));
            Path files = Files.createDirectory(root.resolve("files"));
            Files.writeString(files.resolve("a.txt"), "hello");
            Files.createFile(files.resolve("b.txt"));
            Files.write(files.resolve("over.bin"), new byte[1024*1024+1]);
            Properties p = new Properties();
            p.setProperty("download.dir", files.toString());
            try (var out = Files.newOutputStream(root.resolve("config/config.properties"))) { p.store(out, "Standalone fixture"); }
            edt(() -> {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                Theme.installUiDefaults();
                dialog = new ComposeDialog(null,new ApiClient("http://127.0.0.1:"+reserved.getLocalPort()),List.of(),null,ON,null);
                dialog.setLocationRelativeTo(null);
                SwingUtilities.invokeLater(() -> dialog.setVisible(true));
                return null;
            });
            // Use the dialog's own display device; a default-device Robot can
            // disagree with Swing user-space coordinates on mixed-DPI desktops.
            GraphicsDevice device = edt(() -> dialog.getGraphicsConfiguration().getDevice());
            robot = new Robot(device);
            robot.setAutoDelay(45);
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                if (event instanceof MouseEvent mouse && mouse.getID() == MouseEvent.MOUSE_PRESSED
                        && mouse.getSource() instanceof Component component
                        && owned(SwingUtilities.getWindowAncestor(component))) {
                    observedPress.set(component);
                }
            }, AWTEvent.MOUSE_EVENT_MASK);
            Thread.ofVirtual().start(() -> {
                try { Thread.sleep(300_000); } catch (InterruptedException ignored) {}
                System.exit(3);
            });
            report("READY standalone ComposeDialog pid=" + ProcessHandle.current().pid()
                    + "; no mailbox/session; commands inspect, aim N, click N, address, address2, invalid, files, oversized, subject, enter, clear, comma, semicolon, flag-off, flag-on, flag-unknown, quit");
            try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
                while(scanner.hasNextLine()) {
                    String cmd = scanner.nextLine().trim();
                    if(cmd.equals("quit")) break;
                    try {
                        switch(cmd) {
                            case "inspect" -> report(edt(LocalComposeProbe::inspect));
                            case "address" -> type("alpha@example.test");
                            case "address2" -> type("beta@example.test");
                            case "invalid" -> type("invalid");
                            case "files" -> type("\"a.txt\" \"b.txt\"");
                            case "oversized" -> type("over.bin");
                            case "subject" -> type("Local draft observation");
                            case "enter" -> { requireTextFocus(); press(KeyEvent.VK_ENTER, false); }
                            case "comma" -> { requireTextFocus(); press(KeyEvent.VK_COMMA, false); }
                            case "semicolon" -> { requireTextFocus(); press(KeyEvent.VK_SEMICOLON, false); }
                            case "clear" -> { requireTextFocus(); robot.keyPress(KeyEvent.VK_CONTROL); robot.keyPress(KeyEvent.VK_A); robot.keyRelease(KeyEvent.VK_A); robot.keyRelease(KeyEvent.VK_CONTROL); press(KeyEvent.VK_BACK_SPACE,false); }
                            case "flag-off", "flag-on", "flag-unknown" -> edt(() -> { dialog.applyPublicConfig(cmd.equals("flag-off")?OFF:cmd.equals("flag-on")?ON:null); return null; });
                            default -> {
                                if(cmd.matches("aim [0-9]+")) aim(Integer.parseInt(cmd.substring(4)));
                                else if(cmd.matches("click [0-9]+")) click(Integer.parseInt(cmd.substring(6)));
                                else throw new IllegalArgumentException("Unknown command");
                            }
                        }
                        report("ACTION " + cmd + " completed");
                    } catch (Exception ex) { incomplete = true; report("STOP " + ex.getClass().getSimpleName()+": "+ex.getMessage()); break; }
                }
            }
        } finally {
            edt(() -> { if(dialog!=null) dialog.dispose(); return null; });
            deleteFixture();
            report("CLEANUP root removed="+!Files.exists(root));
        }
        System.exit(incomplete ? 2 : 0);
    }

    private static void deleteFixture() throws Exception {
        if (root != null && Files.exists(root)) {
            try(var walk=Files.walk(root)) { for(Path path:walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }

    private static <T> T edt(Callable<T> c) throws Exception {
        FutureTask<T> task = new FutureTask<>(c); SwingUtilities.invokeLater(task); return task.get();
    }
    private static Window active() {
        Window w=KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (owned(w) && w.isShowing() && w.isActive()) return w;
        throw new IllegalStateException("Owned probe window is not active; no native input sent");
    }
    private static boolean owned(Window w) {
        for (Window owner=w;owner!=null;owner=owner.getOwner()) if(owner==dialog) return true;
        return false;
    }
    private static JFileChooser chooser(Component c) {
        if (c instanceof JFileChooser picker) return picker;
        if (c instanceof Container cont) for (Component child : cont.getComponents()) {
            JFileChooser picker = chooser(child);
            if (picker != null) return picker;
        }
        return null;
    }
    private static void requireOwnedChooser(Window w) {
        JFileChooser picker = chooser(w);
        if (picker != null && (picker.getCurrentDirectory() == null
                || !picker.getCurrentDirectory().toPath().toAbsolutePath().normalize().startsWith(root.resolve("files"))))
            throw new IllegalStateException("File chooser is outside the isolated fixture; no inspection or input");
    }
    private static void collect(Component c,List<Component> result) {
        if(!c.isShowing()) return;
        if(c instanceof JButton || c instanceof JToggleButton || c instanceof JTextComponent || c instanceof JLabel) result.add(c);
        if(c instanceof Container cont) for(Component child:cont.getComponents()) collect(child,result);
    }
    private static String inspect() {
        if(!dialog.isDisplayable()) return "ComposeDialog closed";
        Window w=active();
        requireOwnedChooser(w);
        List<Component> all=new ArrayList<>();collect(w,all);controls=List.copyOf(all);
        var gc=w.getGraphicsConfiguration();
        StringBuilder b=new StringBuilder("Window="+(w instanceof JDialog d?d.getTitle():w.getClass().getSimpleName())
                +" bounds="+w.getBounds()+" scale="+gc.getDefaultTransform().getScaleX()+"x"+gc.getDefaultTransform().getScaleY()
                +" focus="+focusDescription());
        JFileChooser picker=chooser(w);
        if(picker!=null) b.append(" chooserOwned=true multiSelect=").append(picker.isMultiSelectionEnabled())
                .append(" selectedCount=").append(picker.getSelectedFiles().length);
        for(int i=0;i<all.size();i++) {
            Component c=all.get(i); String text=c instanceof AbstractButton a?a.getText():c instanceof JTextComponent t?t.getText():c instanceof JLabel l?l.getText():"";
            if(c instanceof JTextComponent || c instanceof AbstractButton || (text!=null&&!text.isBlank())) b.append("\n").append(i).append(" ").append(c.getClass().getSimpleName()).append(" enabled=").append(c.isEnabled()).append(" focus=").append(c.isFocusOwner()).append(" size=").append(c.getWidth()).append("x").append(c.getHeight()).append(" text=").append(text);
        }
        return b.toString();
    }
    private static Component aim(int i) throws Exception {
        Component target = controls.get(i);
        Point pt=edt(() -> {
            Window w=active(); requireOwnedChooser(w); Component c=target;
            if(SwingUtilities.getWindowAncestor(c)!=w || !c.isShowing() || !c.isEnabled() || !(c instanceof AbstractButton || c instanceof JTextComponent)) throw new IllegalStateException("Inactive or non-interactive control");
            if(c instanceof AbstractButton a && "Gửi thư".equals(a.getText())) throw new IllegalStateException("Send is excluded from this local probe");
            Rectangle r=c instanceof JComponent j?j.getVisibleRect():new Rectangle(0,0,c.getWidth(),c.getHeight());
            if(r.width<6 || r.height<6) throw new IllegalStateException("Control is clipped");
            Point q=c.getLocationOnScreen();q.translate(r.x+r.width/2,r.y+r.height/2);
            if(!w.getBounds().contains(q)) throw new IllegalStateException("Click outside owned window");
            Point local=new Point(q); SwingUtilities.convertPointFromScreen(local,w);
            Component hit=SwingUtilities.getDeepestComponentAt(w,local.x,local.y);
            if(hit!=c && (hit==null || !SwingUtilities.isDescendingFrom(hit,c)))
                throw new IllegalStateException("Calculated hit is not the intended component");
            return q;
        });
        observedPress.set(null);
        robot.mouseMove(pt.x,pt.y);
        robot.waitForIdle();
        edt(() -> {
            Window w=active(); requireOwnedChooser(w);
            var pointer=MouseInfo.getPointerInfo();
            if(pointer==null || pointer.getLocation().distance(pt)>2)
                throw new IllegalStateException("Native pointer did not reach the intended Swing point; no mouse press sent");
            if(SwingUtilities.getWindowAncestor(target)!=w || !target.isShowing())
                throw new IllegalStateException("Target changed before mouse press");
            return null;
        });
        report("AIM verified Swing hit and pointer; no press sent; pid=" + ProcessHandle.current().pid());
        return target;
    }
    private static void click(int i) throws Exception {
        Component target = aim(i);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        // Give the operating system a bounded delivery interval; no second click.
        for (int n=0; n<20 && observedPress.get()==null; n++) Thread.sleep(50);
        Component actual=observedPress.get();
        if(actual==null || (actual!=target && !SwingUtilities.isDescendingFrom(actual,target)))
            throw new IllegalStateException("Native press was not received by the intended component; actual="+
                    (actual==null?"none":actual.getClass().getSimpleName())+"; "+edt(LocalComposeProbe::focusDescription));
        if(target instanceof JTextComponent) {
            for(int n=0;n<20&&!edt(target::isFocusOwner);n++) Thread.sleep(50);
            if(!edt(target::isFocusOwner))
                throw new IllegalStateException("Native press reached target but focus was not assigned; "+edt(LocalComposeProbe::focusDescription));
        }
        report("NATIVE press received by target; "+edt(LocalComposeProbe::focusDescription));
    }
    private static String focusDescription() {
        Component c=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return c!=null && owned(SwingUtilities.getWindowAncestor(c))
                ? c.getClass().getSimpleName()+" editable="+(c instanceof JTextComponent t && t.isEditable())
                : "none-in-owned-window";
    }
    private static void requireTextFocus() throws Exception {
        edt(() -> { Window w=active(); requireOwnedChooser(w); Component c=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();if(!(c instanceof JTextComponent t)||!t.isEditable()||!t.isEnabled()||SwingUtilities.getWindowAncestor(c)!=w)throw new IllegalStateException("Owned editable field is not focused: "+focusDescription());return null; });
    }
    private static void press(int code,boolean shift) throws Exception {
        edt(LocalComposeProbe::active); if(shift)robot.keyPress(KeyEvent.VK_SHIFT);robot.keyPress(code);robot.keyRelease(code);if(shift)robot.keyRelease(KeyEvent.VK_SHIFT);robot.waitForIdle();
    }
    private static void type(String s) throws Exception {
        requireTextFocus();for(char c:s.toCharArray()) { requireTextFocus(); if(c=='@')press(KeyEvent.VK_2,true);else if(c=='"')press(KeyEvent.VK_QUOTE,true);else press(KeyEvent.getExtendedKeyCodeForChar(c),Character.isUpperCase(c)); }
    }
    private static void report(String s) throws Exception {
        System.out.println(s);System.out.flush();Files.writeString(evidence.resolve("observation.txt"),s+System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
}
