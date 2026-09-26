package com.ubor.app;

import com.ubor.core.*;
import static com.ubor.core.Types.*;
import java.nio.file.*;
import java.net.*;
import java.util.concurrent.*;
import javax.swing.*;

public final class Main {
    private static byte[] key(){return ControlProtocol.keyFromEnvironment();}
    private static void waitForever()throws InterruptedException {new CountDownLatch(1).await();}
    public static void main(String[] args)throws Exception {
        if(args.length==0){help();return;}
        if(java.util.Set.of("--self-test","--demo","--server-sim","--hardware","--hardware-teleop").contains(args[0])) System.setProperty("java.awt.headless","true");
        switch(args[0]) {
            case "--new-key" -> System.out.println(java.util.HexFormat.of().formatHex(ControlProtocol.challenge()));
            case "--self-test" -> SelfTest.run(Path.of(args.length>1?args[1]:"test-report"));
            case "--demo" -> System.out.println(Demo.run(Path.of(args.length>1?args[1]:"demo-report"),args.length>2?Double.parseDouble(args[2]):90));
            case "--desktop", "--server-sim" -> {
                Simulator sim=new Simulator();RobotService robot=new RobotService(sim,sim,null);
                byte[] k=args[0].equals("--desktop")?ControlProtocol.challenge():key();
                int port=args[0].equals("--desktop")?0:Integer.parseInt(args[1]);
                ControlServer server=new ControlServer(robot,k,InetAddress.getByName(args[0].equals("--desktop")?"127.0.0.1":"0.0.0.0"),port);
                ScheduledExecutorService plant=Executors.newSingleThreadScheduledExecutor();
                plant.scheduleAtFixedRate(()->sim.advance(System.nanoTime()),0,20,TimeUnit.MILLISECONDS);
                Runtime.getRuntime().addShutdownHook(new Thread(()->{try{server.close();}catch(Exception ignored){}robot.close();plant.shutdownNow();}));
                robot.start();server.start();System.out.println("SIM_ORACLE_NOT_ML port="+server.port());
                if(args[0].equals("--desktop"))SwingUtilities.invokeLater(()->{
                    DesktopController ui=new DesktopController("127.0.0.1",server.port(),k,sim);ui.setVisible(true);
                    ui.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent e){System.exit(0);}});
                });else waitForever();
            }
            case "--controller" -> SwingUtilities.invokeLater(()->new DesktopController(args[1],Integer.parseInt(args[2]),key(),null).setVisible(true));
            case "--gamepad" -> {
                try(LinuxJoystick joy=new LinuxJoystick(Path.of(args[3]));ControlClient client=new ControlClient(args[1],Integer.parseInt(args[2]),key(),joy::command)) {
                    client.start();while(joy.connected()){Thread.sleep(1000);System.out.println(client.status);}
                }
            }
            case "--hardware-teleop" -> {
                Hardware hw=(Hardware)Class.forName("com.ubor.pi.PiHardware").getConstructor(Path.class).newInstance(Path.of(args[1]));
                try {
                    Perception camera=(Perception)Class.forName("com.ubor.ml.UvcCameraPerception").getConstructor(Path.class).newInstance(Path.of(args[1]));
                    RobotService robot=new RobotService(hw,camera,Path.of(args[3]));
                    ControlServer server=new ControlServer(robot,key(),InetAddress.getByName("0.0.0.0"),Integer.parseInt(args[2]));
                    Runtime.getRuntime().addShutdownHook(new Thread(()->{try{server.close();}catch(Exception ignored){}robot.close();}));
                    robot.start();server.start();waitForever();
                } catch(Exception|LinkageError e){hw.close();throw e;}
            }
            case "--hardware" -> {
                // Reflection keeps the executable simulator dependency-free. Real adapters are in Maven's full profile.
                Hardware hw=(Hardware)Class.forName("com.ubor.pi.PiHardware").getConstructor(Path.class).newInstance(Path.of(args[1]));
                Perception camera=null;RobotService robot=null;ControlServer server=null;
                try {
                    camera=(Perception)Class.forName("com.ubor.ml.UvcDl4jPerception").getConstructor(Path.class,Path.class).newInstance(Path.of(args[1]),Path.of(args[2]));
                    robot=new RobotService(hw,camera,args.length>4?Path.of(args[4]):null);
                    server=new ControlServer(robot,key(),InetAddress.getByName("0.0.0.0"),Integer.parseInt(args[3]));
                    final RobotService r=robot;final ControlServer s=server;
                    Runtime.getRuntime().addShutdownHook(new Thread(()->{try{s.close();}catch(Exception ignored){}r.close();}));
                    robot.start();server.start();waitForever();
                } finally {if(server!=null)server.close();if(robot!=null)robot.close();else{hw.close();if(camera!=null)camera.close();}}
            }
            default -> help();
        }
    }
    private static void help(){System.out.println("""
UBOR-JAVA R02 LEGACY_FIRST_ORDER -- Java 21 (not WPILib)
  --new-key                              generate a random shared secret
  --desktop                              Swing controller + 2D SIM_ORACLE
  --demo [output-dir] [seconds]           deterministic integration, NOT ML
  --self-test [output-dir]                offline assertion suite
  --server-sim PORT                       network simulator, UBOR_KEY required
  --controller HOST PORT                 keyboard remote controller
  --gamepad HOST PORT /dev/input/js0       Linux USB gamepad, UBOR_KEY required
  --hardware-teleop robot.properties PORT record-dir
  --hardware robot.properties models PORT [record-dir]
Hardware requires the Maven full classpath, calibrated hardware and approved models.
Space=hold deadman; W/S/A/D=drive; use on-screen ARM. Releasing focus disarms.
""");}
}
