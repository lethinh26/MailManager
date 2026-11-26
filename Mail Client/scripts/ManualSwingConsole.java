import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.accessibility.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * Disposable-fixture, operator-driven Swing accessibility console.
 * Not a test suite: no scenario scripting, fake transport, business method
 * calls, assertions, global input or desktop capture. One command per request.
 * Runs the actual application entry in its own Java process.
 */
public class ManualSwingConsole {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final IdentityHashMap<Component,Integer> IDS = new IdentityHashMap<>();
    private static final Map<Integer,Component> COMPONENTS = new HashMap<>();
    private static final Map<String,String> SECRETS = new HashMap<>();
    private static final List<String> REDACTIONS = new ArrayList<>();
    private static int nextId = 1;
    private static Path root, channel, stopFile;
    private static final long LEASE_NANOS = TimeUnit.MINUTES.toNanos(45);

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected private manifest and channel name");
        Path manifest = Path.of(args[0]).toRealPath();
        JsonNode f = JSON.readTree(manifest.toFile());
        root = Path.of(f.path("root").asText()).toRealPath();
        Path temp = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        if (!root.startsWith(temp) || !root.getFileName().toString().startsWith("mailapp-")
                || !manifest.startsWith(root)) throw new IllegalArgumentException("Not a disposable fixture root");
        String url = f.path("baseUrl").asText();
        java.net.URI uri = java.net.URI.create(url);
        if (!"https".equals(uri.getScheme()) || !"localhost".equalsIgnoreCase(uri.getHost())
                || uri.getPort() <= 1024 || uri.getPort() == 8443 || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Not a disposable HTTPS endpoint");
        if (!args[1].matches("manual-ui-[a-zA-Z0-9_-]{1,48}")) throw new IllegalArgumentException("Invalid channel");
        Path cert = inside(f.path("cert").asText(), true);
        Path cfg = inside(f.path("clientConfigDir").asText(), false).resolve(args[1]);
        Files.createDirectories(cfg);
        cfg = cfg.toRealPath();
        if (!cfg.startsWith(root)) throw new IllegalArgumentException("Config outside fixture");
        Path download = inside(f.path("downloadDir").asText(), false);
        Files.createDirectories(download);
        download = download.toRealPath();
        if (!download.startsWith(root)) throw new IllegalArgumentException("Downloads outside fixture");
        stopFile = inside(f.path("stopFile").asText(), false);
        channel = root.resolve(args[1]);
        Files.createDirectory(channel);
        Properties props = new Properties();
        props.setProperty("server.url", url);
        props.setProperty("cert.path", cert.toString());
        props.setProperty("download.dir", download.toString());
        props.setProperty("autoclean.enabled","false");
        props.setProperty("autoclean.dryrun","true");
        props.setProperty("tls.insecure","false");
        try (OutputStream out = Files.newOutputStream(cfg.resolve("config.properties"), StandardOpenOption.CREATE_NEW)) {
            props.store(out, "Disposable manual fixture only");
        }
        System.setProperty("mailclient.config.dir",cfg.toString());
        System.setProperty("mailapp.server.url",url);
        System.setProperty("mailclient.insecure","false");
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Headful desktop unavailable");
        addSecret("adminInitialPassword", f.path("adminInitialPassword").asText());
        addSecret("adminPassword", f.path("adminPassword").asText());
        for (JsonNode user : f.path("users")) {
            String name = user.path("username").asText();
            addSecret(name+".password",user.path("password").asText());
            addSecret(name+".appPassword",user.path("appPassword").asText());
        }
        // All application initialization, real UI and actual transport remain unchanged.
        com.mailapp.client.Main.main(new String[0]);
        ObjectNode ready = JSON.createObjectNode();
        ready.put("pid", ProcessHandle.current().pid());
        ready.put("headful", true);
        atomicJson(channel.resolve("ready.json"), ready);
        long until = System.nanoTime() + LEASE_NANOS;
        Path inbox = channel.resolve("request.json");
        try {
            while (System.nanoTime() < until && !Files.exists(stopFile)) {
                if (!Files.isRegularFile(inbox)) { Thread.sleep(100); continue; }
                JsonNode request;
                try {
                    request = JSON.readTree(inbox.toFile());
                } catch (Exception ex) {
                    Files.deleteIfExists(inbox);
                    continue;
                }
                Files.delete(inbox);
                ObjectNode reply = JSON.createObjectNode();
                String requestId = request.path("id").asText();
                if (!requestId.matches("[a-f0-9]{32}")) continue;
                Path result = channel.resolve("response-" + requestId + ".json");
                reply.put("id", requestId);
                try {
                    String op = request.path("op").asText();
                    switch (op) {
                        case "inspect" -> reply.put("observation", edt(ManualSwingConsole::inspect));
                        case "snapshot" -> reply.put("observation", edt(() -> snapshot(request)));
                        case "close" -> { reply.put("observation","Fixture UI process closing"); atomicJson(result,reply); return; }
                        case "click", "value", "secret", "select", "enter", "pointer", "key", "focus", "captureSecret" -> {
                            int id = request.path("target").asInt(-1);
                            // Resolve and check on EDT; schedule actual action separately so
                            // modal dialogs can enter their own event loop without deadlock.
                            String target = edt(() -> describe(checked(id)));
                            EventQueue.invokeLater(() -> {
                                try { action(op,checked(id),request); }
                                catch (Throwable ex) {
                                    try {
                                        ObjectNode error = JSON.createObjectNode();
                                        error.put("id",request.path("id").asText());
                                        error.put("error",scrub(ex.getClass().getSimpleName()+": "+ex.getMessage()));
                                        atomicJson(channel.resolve("action-error.json"),error);
                                    } catch (Exception ignored) {}
                                }
                            });
                            reply.put("observation","Dispatched one "+op+" to "+target+"; inspect the resulting window separately.");
                        }
                        default -> throw new IllegalArgumentException("Unknown operation");
                    }
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause()!=null ? ex.getCause() : ex;
                    reply.put("error", scrub(cause.getClass().getSimpleName()+": "+cause.getMessage()));
                }
                atomicJson(result,reply);
            }
        } catch (Exception ex) {
            // Never swallow a transport failure in System.exit's finally path.
            System.err.println(scrub("Console stopped: "+ex.getClass().getSimpleName()+": "+ex.getMessage()));
        } finally {
            // Only this isolated JVM's own windows; no other processes/windows.
            EventQueue.invokeLater(() -> {
                Window[] windows=Window.getWindows();
                for (int i=windows.length-1;i>=0;i--) windows[i].dispose();
            });
            System.exit(0);
        }
    }

    private static Path inside(String s, boolean existing) throws IOException {
        if (s.isBlank()) throw new IllegalArgumentException("Missing fixture path");
        Path p = Path.of(s).toAbsolutePath().normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Path outside fixture");
        if (existing) p=p.toRealPath();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Resolved path outside fixture");
        return p;
    }
    private static void addSecret(String key,String value) {
        if (value==null || value.isBlank()) return;
        SECRETS.put(key,value);
        REDACTIONS.add(value);
    }
    private static String scrub(String s) {
        if (s==null) return "";
        for (String secret: REDACTIONS) s=s.replace(secret,"[fixture secret]");
        // Never return bearer tokens, authorization codes or raw secret-like values.
        s=s.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~-]+","Bearer [redacted]");
        s=s.replaceAll("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b","[JWT redacted]");
        return s.replace('\r',' ').replace('\n',' ').replace('\t',' ');
    }
    private static String trim(String s,int max) { s=scrub(s);return s.length()>max?s.substring(0,max)+"…":s; }
    private static <T> T edt(Callable<T> fn) throws Exception {
        FutureTask<T> t=new FutureTask<>(fn);
        EventQueue.invokeLater(t);
        return t.get(6,TimeUnit.SECONDS);
    }
    private static int id(Component c) {
        return IDS.computeIfAbsent(c,k->{int n=nextId++;COMPONENTS.put(n,k);return n;});
    }
    private static Component checked(int id) {
        Component c=COMPONENTS.get(id);
        if(c==null || !c.isShowing()) throw new IllegalArgumentException("Control not showing; inspect again");
        return c;
    }
    private static String title(Window w) {
        return w instanceof Frame f ? f.getTitle() : w instanceof Dialog d ? d.getTitle() : w.getClass().getSimpleName();
    }
    private static String describe(Component c) {
        String name="";
        if(c instanceof JPasswordField) name="[password input]";
        else if(c instanceof AbstractButton b) name=b.getText();
        else if(c instanceof JLabel l) name=l.getText();
        else if(c instanceof JTextComponent t) {
            Window w=SwingUtilities.getWindowAncestor(t);
            name=chooserAncestor(c)!=null?"[chooser value withheld]"
                    : w!=null && sensitive(w)?"[sensitive value withheld]":t.getText();
        }
        else if(c instanceof Window w) name=title(w);
        else if(c.getAccessibleContext()!=null) name=c.getAccessibleContext().getAccessibleName();
        String tip=c instanceof JComponent j?j.getToolTipText():null;
        return "#"+id(c)+" "+c.getClass().getSimpleName()+" "+trim(name,230)
                +(tip==null?"":" (tip: "+trim(tip,150)+")")+" enabled="+c.isEnabled()
                +" size="+c.getWidth()+"x"+c.getHeight();
    }
    private static boolean sensitive(Window w) {
        String title=title(w).toLowerCase(Locale.ROOT);
        if(title.contains("mật khẩu tạm") || title.contains("temporary password")) return true;
        // Temp-password dialogs sometimes use a generic title.
        return sensitiveContent(w);
    }
    private static JFileChooser chooserAncestor(Component c) {
        for(Component p=c;p!=null;p=p.getParent()) if(p instanceof JFileChooser f)return f;
        return null;
    }
    private static boolean fixturePath(File file) {
        if(file==null)return false;
        Path p=file.toPath().toAbsolutePath().normalize();
        if(!p.startsWith(root))return false;
        try {
            Path existing=p;
            while(existing!=null && !Files.exists(existing))existing=existing.getParent();
            return existing!=null && existing.toRealPath().startsWith(root);
        } catch(IOException ex){return false;}
    }
    private static boolean sensitiveContent(Component c) {
        if(c instanceof JPasswordField p && p.getEchoChar()==0 && p.getPassword().length>0) return true;
        // One-time password display is a noneditable text field in a modal
        // dialog. Avoid redacting the whole create-user form merely because
        // its explanatory label mentions a temporary password.
        if(c instanceof JTextField t && !t.isEditable() && !(t instanceof JPasswordField)
                && SwingUtilities.getWindowAncestor(t) instanceof JDialog d
                && (title(d).contains("Tạo tài khoản thành công") || title(d).contains("Đặt lại mật khẩu thành công"))) return true;
        if(c instanceof Container p) for(Component child:p.getComponents()) if(sensitiveContent(child)) return true;
        return false;
    }
    private static String inspect() {
        StringBuilder s=new StringBuilder();
        for(Window w:Window.getWindows()) if(w.isShowing()) {
            s.append("\nWINDOW ").append(describe(w)).append(" active=").append(w.isActive()).append("\n");
            if(sensitive(w)){
                s.append("[Sensitive window: text values withheld; permitted controls only.]\n");
                for(Component child:descendants(w)) if(child.isShowing()){
                    if(child instanceof JTextField t && !t.isEditable() && !(t instanceof JPasswordField))
                        s.append("#").append(id(child)).append(" noneditable sensitive field [redacted]\n");
                    else if(child instanceof JButton b && !Objects.toString(b.getText(),"").contains("Sao chép"))
                        s.append(describe(child)).append("\n");
                }
                continue;
            }
            walk(w,s,0);
        }
        return s.length()==0?"No showing fixture windows":s.toString();
    }
    private static void walk(Component c,StringBuilder s,int depth) {
        if(!c.isShowing() || depth>35 || s.length()>65000) return;
        if(c instanceof JFileChooser f) {
            s.append("  #").append(id(f)).append(" JFileChooser currentDir=")
                    .append(fixturePath(f.getCurrentDirectory())?f.getCurrentDirectory():"[outside fixture; directory listing withheld]").append("\n");
            // A Windows chooser exposes personal shell-folder shortcuts even
            // when its current folder is safe. Do not inspect those models.
            for(Component child:descendants(f)) if(child.isShowing()){
                if(child instanceof JTextField t && t.isEditable())
                    s.append(describe(child)).append("\n");
                else if(child instanceof JButton b && Set.of("Open","Save","Cancel","Mở","Lưu","Hủy").contains(Objects.toString(b.getText(),"")))
                    s.append(describe(child)).append("\n");
            }
            return;
        }
        boolean useful=c instanceof AbstractButton || c instanceof JLabel || c instanceof JTextComponent
                || c instanceof JComboBox<?> || c instanceof JList<?> || c instanceof JTable
                || c instanceof JTabbedPane || c instanceof JSpinner || c instanceof JProgressBar
                || c instanceof JSlider || c instanceof JFileChooser || c instanceof JPopupMenu
                || c instanceof JPanel && c.getMouseListeners().length>0;
        String pad=" ".repeat(Math.min(depth,16));
        if(useful){
            s.append(pad).append(describe(c));
            if(c instanceof AbstractButton b) s.append(" selected=").append(b.isSelected());
            if(c instanceof JTabbedPane t) {s.append(" tabs=");for(int i=0;i<t.getTabCount();i++)s.append(i).append(":").append(trim(t.getTitleAt(i),80)).append(" ");s.append("selected=").append(t.getSelectedIndex());}
            if(c instanceof JComboBox<?> b) {s.append(" selected=").append(b.getSelectedIndex()).append(" items=");for(int i=0;i<Math.min(b.getItemCount(),60);i++)s.append(i).append(":").append(trim(String.valueOf(b.getItemAt(i)),180)).append(" | ");}
            if(c instanceof JSpinner b) s.append(" value=").append(trim(String.valueOf(b.getValue()),80));
            if(c instanceof JProgressBar b) s.append(" indeterminate=").append(b.isIndeterminate());
            s.append("\n");
            if(c instanceof JList<?> l) {
                s.append(pad).append(" count=").append(l.getModel().getSize()).append(" selected=").append(Arrays.toString(l.getSelectedIndices())).append("\n");
                for(int i=0;i<Math.min(l.getModel().getSize(),65);i++){
                    Object value=l.getModel().getElementAt(i);
                    // Use the renderer's accessible text (not DTO object dump).
                    String rendered="";
                    try {
                        @SuppressWarnings("unchecked") JList<Object> cast=(JList<Object>)l;
                        Component r=cast.getCellRenderer().getListCellRendererComponent(cast,value,i,l.isSelectedIndex(i),false);
                        rendered=rendererText(r);
                    } catch(Exception ignored){}
                    s.append(pad).append(" row ").append(i).append(" ").append(trim(rendered,300)).append("\n");
                }
            }
            if(c instanceof JTable t) {
                s.append(pad).append(" rows=").append(t.getRowCount()).append(" selected=").append(Arrays.toString(t.getSelectedRows())).append("\n");
                for(int i=0;i<Math.min(t.getRowCount(),40);i++){
                    s.append(pad).append(" row ").append(i).append(" ");
                    for(int j=0;j<t.getColumnCount();j++) {
                        Component r=t.getCellRenderer(i,j).getTableCellRendererComponent(t,t.getValueAt(i,j),false,false,i,j);
                        s.append(trim(rendererText(r),160)).append(" | ");
                    }
                    s.append("\n");
                }
            }
        }
        if(c instanceof Container p && !(c instanceof JTable) && !(c instanceof JList<?>))
            for(Component child:p.getComponents()) walk(child,s,depth+1);
    }
    private static String rendererText(Component c) {
        if(c instanceof JLabel l) return l.getText();
        if(c instanceof JTextComponent t) return t.getText();
        StringBuilder out=new StringBuilder();
        if(c instanceof Container p) for(Component child:p.getComponents())out.append(rendererText(child)).append(" ");
        if(out.length()==0 && c.getAccessibleContext()!=null)out.append(Objects.toString(c.getAccessibleContext().getAccessibleName(),""));
        return out.toString();
    }
    private static List<Component> descendants(Component c) {
        List<Component> result=new ArrayList<>();
        result.add(c);
        if(c instanceof Container p) for(Component child:p.getComponents())result.addAll(descendants(child));
        return result;
    }
    private static String snapshot(JsonNode q) throws Exception {
        Component c=checked(q.path("target").asInt());
        Window w=c instanceof Window window?window:SwingUtilities.getWindowAncestor(c);
        if(w==null || sensitive(w) || descendants(w).stream().anyMatch(JFileChooser.class::isInstance))
            throw new IllegalArgumentException("Snapshot withheld for sensitive/native chooser dialog");
        String name=q.path("name").asText();
        if(!name.matches("[a-zA-Z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid snapshot name");
        if(w.getWidth()<=0 || w.getHeight()<=0 || w.getWidth()>3500 || w.getHeight()>2500)throw new IllegalArgumentException("Invalid window bounds");
        BufferedImage img=new BufferedImage(w.getWidth(),w.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();
        try {w.printAll(g);} finally{g.dispose();}
        Path target=channel.resolve(name+".png");
        if(Files.exists(target))throw new IllegalArgumentException("Snapshot already exists");
        ImageIO.write(img,"png",target.toFile());
        return "Window-only Swing rendering of "+describe(w)+" saved to "+target+" (not desktop capture)";
    }
    private static void action(String op,Component c,JsonNode q) {
        if(!c.isEnabled())throw new IllegalArgumentException("Disabled control");
        JFileChooser chooser=chooserAncestor(c);
        if(chooser!=null){
            if(op.equals("value")){
                String path=q.path("value").asText();
                File file=new File(path);
                if(!file.isAbsolute() || !fixturePath(file))
                    throw new IllegalArgumentException("Chooser input must be an absolute path inside fixture");
            } else if(op.equals("click") && c instanceof JButton button){
                if(!Set.of("Open","Save","Cancel","Mở","Lưu","Hủy").contains(Objects.toString(button.getText(),"")))
                    throw new IllegalArgumentException("Chooser navigation is not allowed");
                if(!Set.of("Cancel","Hủy").contains(button.getText())){
                    // FileChooser has not necessarily committed the editable
                    // filename yet; check the actual visible input field.
                    boolean safe=descendants(chooser).stream().filter(JTextField.class::isInstance)
                        .map(JTextField.class::cast).filter(t->t.isShowing() && t.isEditable())
                        .map(JTextField::getText).anyMatch(path->{
                            File file=new File(path);
                            if(!file.isAbsolute())file=new File(chooser.getCurrentDirectory(),path);
                            return fixturePath(file);
                        });
                    if(!safe)throw new IllegalArgumentException("Chooser selection not verified inside fixture");
                }
            } else throw new IllegalArgumentException("Only fixture path input and Open/Save/Cancel permitted in chooser");
        }
        switch(op) {
            case "click" -> {
                AccessibleContext ctx=c.getAccessibleContext();
                AccessibleAction a=ctx==null?null:ctx.getAccessibleAction();
                if(a==null || a.getAccessibleActionCount()==0 || !a.doAccessibleAction(0))
                    throw new IllegalArgumentException("No accessible invoke; inspect and use a targeted pointer instead");
            }
            case "value","secret" -> {
                if(op.equals("secret") && !(c instanceof JPasswordField))
                    throw new IllegalArgumentException("Fixture secrets may only be entered in password controls");
                String val=op.equals("secret")?SECRETS.get(q.path("name").asText()):q.path("value").asText();
                if(val==null || val.length()>20000)throw new IllegalArgumentException("Invalid value");
                if(c instanceof JPasswordField && op.equals("value")) {
                    // Operator-defined fake passwords must be redacted in all later observations.
                    addSecret("local-"+nextId,val);
                }
                if(c instanceof JTextComponent t && t.isEditable()) {
                    AccessibleContext ctx=t.getAccessibleContext();
                    AccessibleEditableText ed=ctx==null?null:ctx.getAccessibleEditableText();
                    if(ed!=null)ed.setTextContents(val); else t.setText(val);
                } else if(c instanceof JSpinner spinner) {
                    spinner.setValue(Integer.parseInt(val));
                } else throw new IllegalArgumentException("Not an editable control");
            }
            case "select" -> {
                int index=q.path("index").asInt(-1);
                if(c instanceof JTabbedPane t)t.setSelectedIndex(index);
                else if(c instanceof JComboBox<?> b)b.setSelectedIndex(index);
                else if(c instanceof JList<?> l){l.setSelectedIndex(index);l.ensureIndexIsVisible(index);}
                else if(c instanceof JTable t){t.setRowSelectionInterval(index,index);t.scrollRectToVisible(t.getCellRect(index,0,true));}
                else throw new IllegalArgumentException("Not a selectable control");
            }
            case "enter" -> {
                if(c instanceof JTextField t)t.postActionEvent();
                else throw new IllegalArgumentException("Not a text field");
            }
            case "focus" -> c.requestFocusInWindow();
            case "captureSecret" -> {
                Window w=SwingUtilities.getWindowAncestor(c);
                if (!(c instanceof JTextField t) || t.isEditable() || w==null || !sensitive(w)
                        || !q.path("name").asText().matches("fixture-temp-[a-zA-Z0-9_-]{1,40}"))
                    throw new IllegalArgumentException("Only a fixture one-time password field may be captured privately");
                addSecret(q.path("name").asText(),t.getText());
            }
            case "key" -> {
                int code=q.path("code").asInt(-1);
                if(!Set.of(KeyEvent.VK_ENTER,KeyEvent.VK_ESCAPE,KeyEvent.VK_TAB,KeyEvent.VK_DELETE,KeyEvent.VK_N,KeyEvent.VK_R).contains(code))
                    throw new IllegalArgumentException("Unsupported key");
                int mods=q.path("ctrl").asBoolean()?InputEvent.CTRL_DOWN_MASK:0;
                c.requestFocusInWindow();
                KeyboardFocusManager k=KeyboardFocusManager.getCurrentKeyboardFocusManager();
                k.redispatchEvent(c,new KeyEvent(c,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),mods,code,KeyEvent.CHAR_UNDEFINED));
                k.redispatchEvent(c,new KeyEvent(c,KeyEvent.KEY_RELEASED,System.currentTimeMillis(),mods,code,KeyEvent.CHAR_UNDEFINED));
            }
            case "pointer" -> {
                int x=q.path("x").asInt(-1),y=q.path("y").asInt(-1);
                if(c instanceof JList<?> l && q.has("row")){
                    Rectangle bounds=l.getCellBounds(q.path("row").asInt(),q.path("row").asInt());
                    if(bounds==null)throw new IllegalArgumentException("Invalid list row");
                    x=q.path("right").asBoolean()?bounds.x+bounds.width-12:bounds.x+bounds.width/2;
                    y=bounds.y+bounds.height/2;
                }
                if(x<0 || y<0 || x>=c.getWidth() || y>=c.getHeight())throw new IllegalArgumentException("Pointer outside target");
                int button=q.path("rightClick").asBoolean()?MouseEvent.BUTTON3:MouseEvent.BUTTON1;
                int clicks=q.path("double").asBoolean()?2:1;
                int mods=(q.path("ctrl").asBoolean()?InputEvent.CTRL_DOWN_MASK:0)|(q.path("shift").asBoolean()?InputEvent.SHIFT_DOWN_MASK:0);
                long now=System.currentTimeMillis();
                c.dispatchEvent(new MouseEvent(c,MouseEvent.MOUSE_PRESSED,now,mods,x,y,clicks,false,button));
                c.dispatchEvent(new MouseEvent(c,MouseEvent.MOUSE_RELEASED,now,mods,x,y,clicks,button==MouseEvent.BUTTON3,button));
                c.dispatchEvent(new MouseEvent(c,MouseEvent.MOUSE_CLICKED,now,mods,x,y,clicks,false,button));
            }
            default -> throw new IllegalArgumentException("Unsupported action");
        }
    }
    private static void atomicJson(Path dest,JsonNode data)throws IOException{
        Path tmp=dest.resolveSibling(dest.getFileName()+"-"+UUID.randomUUID()+".tmp");
        Files.writeString(tmp,JSON.writeValueAsString(data),StandardCharsets.UTF_8);
        try {
            for (int i=0; ; i++) {
                try {
                    Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                    break;
                } catch (FileSystemException ex) {
                    if(i==9)throw ex;
                    try{Thread.sleep(50);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException(interrupted);}
                }
            }
        } finally {Files.deleteIfExists(tmp);}
    }
}
