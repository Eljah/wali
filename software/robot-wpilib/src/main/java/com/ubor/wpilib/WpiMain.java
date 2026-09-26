package com.ubor.wpilib;

import com.ubor.app.*;
import com.ubor.core.*;
import static com.ubor.core.Types.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/** WPILib is used by the same Simulator, RobotService, safety loop and controller, not a separate toy. */
public final class WpiMain {
    public static void main(String[] arguments)throws Exception{
        List<String> args=new ArrayList<>(Arrays.asList(arguments));
        boolean nt=args.remove("--nt");Path config=null,record=null;
        int index=args.indexOf("--params");
        if(index>=0){if(index+1==args.size())throw new IllegalArgumentException("--params requires a file");config=Path.of(args.remove(index+1));args.remove(index);}
        index=args.indexOf("--record");
        if(index>=0){if(index+1==args.size())throw new IllegalArgumentException("--record requires a directory");record=Path.of(args.remove(index+1));args.remove(index);}
        if(args.isEmpty()){help();return;}
        String mode=args.get(0);
        if(!mode.equals("--desktop"))System.setProperty("java.awt.headless","true");
        if(mode.equals("--verify")){WpiVerification.run(Path.of(args.size()>1?args.get(1):"wpilib-report"));return;}
        if(!Set.of("--desktop","--server","--demo","--about").contains(mode)){help();throw new IllegalArgumentException("Unknown WPILib mode: "+mode);}
        WpilibDynamics physics=new WpilibDynamics(WpiParameters.load(config));
        Simulator world=new Simulator(physics);
        // Initialize matrix/JNI hot paths before starting the 50 Hz deadline worker.
        for(int i=0;i<20;i++)physics.step(.005,Actuation.off(),false,false);
        System.out.println(physics.name()+" / SIM_ORACLE_NOT_ML / no physical hardware");
        System.out.println("WPILib origin: "+edu.wpi.first.wpilibj.simulation.DifferentialDrivetrainSim.class.getProtectionDomain().getCodeSource().getLocation());
        if(mode.equals("--about")){try(world){System.out.println("HAL initialized; JNI path="+System.getProperty("java.library.path"));}return;}
        if(mode.equals("--demo")){
            try(world){Path out=Path.of(args.size()>1?args.get(1):"wpilib-demo");
                System.out.println(Demo.run(out,args.size()>2?Double.parseDouble(args.get(2)):90,world));}
            return;
        }
        // Desktop operator must explicitly press Physical ARM; deterministic demo fixture is pre-armed.
        world.permit=false;
        boolean desktop=mode.equals("--desktop");
        int port=desktop?0:Integer.parseInt(args.size()>1?args.get(1):"5808");
        byte[] key=desktop?ControlProtocol.challenge():ControlProtocol.keyFromEnvironment();
        RobotService robot=new RobotService(world,world,record);
        ControlServer server=new ControlServer(robot,key,InetAddress.getByName("127.0.0.1"),port);
        WpiDashboard dashboard=nt?new WpiDashboard():null;
        ScheduledExecutorService plant=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"wpilib-physics-200Hz"));
        AtomicBoolean closing=new AtomicBoolean();
        Runnable shutdown=()->{
            if(!closing.compareAndSet(false,true))return;
            plant.shutdownNow();
            try{plant.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            try{server.close();}catch(Exception ignored){}
            robot.close();if(dashboard!=null)dashboard.close();world.close();
        };
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown,"wpilib-shutdown"));
        plant.scheduleAtFixedRate(()->{
            try{world.advance(System.nanoTime());}
            catch(Exception|LinkageError error){synchronized(world){world.emergency=true;world.permit=false;}
                robot.disconnect();System.err.println("PHYSICS FAULT: "+error);}
        },0,5,TimeUnit.MILLISECONDS);
        if(dashboard!=null)plant.scheduleAtFixedRate(()->dashboard.update(world,physics),0,100,TimeUnit.MILLISECONDS);
        robot.start();server.start();
        System.out.println("TCP controller 127.0.0.1:"+server.port()+"; Physical ARM required");
        if(nt)System.out.println("Glass/NT4: 127.0.0.1:5810, SmartDashboard/UBOR/Field");
        if(desktop)SwingUtilities.invokeLater(()->{
            DesktopController ui=new DesktopController("127.0.0.1",server.port(),key,world);ui.setVisible(true);
            ui.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent e){shutdown.run();System.exit(0);}});
        });else {
            System.out.println("Console commands: arm, estop, restore, jam. They only affect this simulated world.");
            Thread console=new Thread(()->{
                try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(System.in))){
                    String line;while((line=reader.readLine())!=null){synchronized(world){switch(line.trim()){
                        case "arm" -> System.out.println("Physical ARM="+world.physicalArm());
                        case "estop" -> {world.emergency=true;world.permit=false;}
                        case "restore" -> {world.emergency=false;world.permit=false;}
                        case "jam" -> world.jam=!world.jam;
                        default -> System.out.println("Use arm / estop / restore / jam");
                    }}}
                }catch(Exception e){System.err.println("Console closed: "+e);}
            },"simulation-physical-controls");console.setDaemon(true);console.start();
            new CountDownLatch(1).await();
        }
    }
    private static void help(){System.out.println("""
UBOR R02 WPILib desktop simulation / Java 21
  --desktop [--nt] [--params config/wpilib-sim.properties] [--record dir]
  --demo [output-dir] [seconds] [--params path]
  --verify [output-dir]       REAL WPILib + native-HAL integration suite
  --about                    initialize HAL and print binary origin
  --server [PORT]             loopback only; UBOR_KEY required
Physical ARM (SIM), then Arm / Disarm, then hold SPACE. AUTO uses ORACLE, NOT DL4J.
The legacy dist/ubor-sim.jar does NOT contain this backend. No silent fallback exists.
""");}
}
