package com.ubor.app;

import com.ubor.core.*;
import static com.ubor.core.Types.*;
import javax.swing.*;
import java.awt.*;
import com.ubor.core.Types.Label;
import com.ubor.core.Types.Frame;
import java.awt.event.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Java Swing remote. SPACE is a hold-to-run deadman, never a toggle. */
public final class DesktopController extends JFrame {
    private final Set<Integer> keys=ConcurrentHashMap.newKeySet();
    private volatile boolean arm,auto,stop,reset;
    private volatile Label label=Label.PAPER;
    private final ControlClient client;
    private final Simulator simulator;
    public DesktopController(String host,int port,byte[] secret,Simulator world) {
        super("UBOR-JAVA R02 / "+(world==null?"hardware remote":world.dynamicsName()));simulator=world;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);setSize(1200,820);setLocationByPlatform(true);
        client=new ControlClient(host,port,secret,this::command);
        JPanel toolbar=new JPanel();
        JButton armButton=new JButton("Arm / Disarm");armButton.addActionListener(e->arm=!arm);toolbar.add(armButton);
        JCheckBox autoBox=new JCheckBox("AUTO (model / SIM oracle)");autoBox.addActionListener(e->auto=autoBox.isSelected());toolbar.add(autoBox);
        JButton stopButton=new JButton("STOP");stopButton.addActionListener(e->{stop=true;arm=false;keys.clear();});toolbar.add(stopButton);
        JButton resetButton=new JButton("Reset latch (release SPACE)");resetButton.addActionListener(e->{arm=false;stop=false;reset=true;new javax.swing.Timer(200,ev->{reset=false;((javax.swing.Timer)ev.getSource()).stop();}).start();});toolbar.add(resetButton);
        JComboBox<Label> labels=new JComboBox<>(Label.values());labels.setSelectedItem(label);labels.addActionListener(e->label=(Label)labels.getSelectedItem());toolbar.add(labels);
        if(world!=null) {
            JButton jam=new JButton("Toggle jam");jam.addActionListener(e->{synchronized(world){world.jam=!world.jam;}});toolbar.add(jam);
            JButton estop=new JButton("Physical stop / restore");estop.addActionListener(e->{synchronized(world){world.emergency=!world.emergency;world.permit=false;}});toolbar.add(estop);
            JButton physicalArm=new JButton("Physical ARM (SIM)");physicalArm.addActionListener(e->world.physicalArm());toolbar.add(physicalArm);
        }
        add(toolbar,BorderLayout.NORTH);View view=new View();add(view,BorderLayout.CENTER);
        JLabel help=new JLabel(" Hold SPACE; arrows/WASD drive; hold C = pickup with selected human label. Release SPACE or lose focus = stop.");add(help,BorderLayout.SOUTH);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e->{
            if(!isFocused()) return false;
            if(e.getID()==KeyEvent.KEY_PRESSED) keys.add(e.getKeyCode());
            if(e.getID()==KeyEvent.KEY_RELEASED) keys.remove(e.getKeyCode());
            return Set.of(KeyEvent.VK_SPACE,KeyEvent.VK_W,KeyEvent.VK_A,KeyEvent.VK_S,KeyEvent.VK_D,KeyEvent.VK_UP,KeyEvent.VK_DOWN,KeyEvent.VK_LEFT,KeyEvent.VK_RIGHT,KeyEvent.VK_C).contains(e.getKeyCode());
        });
        addWindowFocusListener(new WindowAdapter(){@Override public void windowLostFocus(WindowEvent e){keys.clear();arm=false;}});
        addWindowListener(new WindowAdapter(){@Override public void windowClosed(WindowEvent e){try{client.close();}catch(Exception ignored){}}});
        javax.swing.Timer timer=new javax.swing.Timer(100,e->view.repaint());timer.start();client.start();
    }
    private Command command() {
        double v=(pressed(KeyEvent.VK_UP,KeyEvent.VK_W)?.25:0)-(pressed(KeyEvent.VK_DOWN,KeyEvent.VK_S)?.05:0);
        double w=(pressed(KeyEvent.VK_LEFT,KeyEvent.VK_A)?.8:0)-(pressed(KeyEvent.VK_RIGHT,KeyEvent.VK_D)?.8:0);
        return new Command(0,System.nanoTime(),keys.contains(KeyEvent.VK_SPACE),arm,auto,keys.contains(KeyEvent.VK_C),reset,stop,v,w,label);
    }
    private boolean pressed(int a,int b) { return keys.contains(a)||keys.contains(b); }
    private final class View extends JPanel {
        @Override protected void paintComponent(Graphics gg) {
            super.paintComponent(gg);Graphics2D g=(Graphics2D)gg;int w=getWidth(),h=getHeight();
            if(simulator!=null) g.drawImage(simulator.mapImage(w,h/2),0,0,null);
            else {g.setColor(Color.DARK_GRAY);g.drawString("Remote hardware: stay within direct operator observation",25,35);}
            if(client.preview!=null) g.drawImage(client.preview,10,h/2+10,w/2-20,h/2-60,null);
            g.setColor(Color.BLACK);g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
            String[] status=client.status.replace(",",",\n").split("\n");for(int i=0;i<status.length;i++)g.drawString(status[i],w/2+5,h/2+25+i*19);
        }
    }
}
