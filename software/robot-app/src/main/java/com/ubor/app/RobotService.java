package com.ubor.app;

import com.ubor.core.*;
import static com.ubor.core.Types.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.*;
import java.nio.file.Path;

/** The camera may stall; it never runs on the 50 Hz motor/safety worker. */
public final class RobotService implements AutoCloseable {
    private final Hardware hardware;private final Perception perception;
    private final SafetyController safety=new SafetyController();private final DriveControl drive=new DriveControl();
    private final ScheduledExecutorService control=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"control-50Hz"));
    private final ExecutorService vision=Executors.newSingleThreadExecutor(r->new Thread(r,"vision-recording"));
    private final AtomicReference<Command> command=new AtomicReference<>(Command.idle(System.nanoTime()));
    private volatile Vision observation=Vision.unavailable(System.nanoTime());
    private volatile Decision decision=Decision.stopped(Mode.DISARMED,"BOOT");
    private volatile Frame frame;private volatile Sensors sensors;
    private volatile boolean running;
    private volatile String failure="";
    private final DatasetRecorder recorder;
    public RobotService(Hardware h,Perception p,Path recordPath) throws Exception {
        hardware=h;perception=p;recorder=recordPath==null?null:new DatasetRecorder(recordPath,"run_"+System.currentTimeMillis());
    }
    public void accept(Command c) { command.set(c); }
    public void disconnect() { command.set(Command.idle(System.nanoTime())); }
    public Decision decision() { return decision; }
    public Frame frame() { return frame; }
    public Sensors sensors() { return sensors; }
    public void start() {
        running=true;
        control.scheduleAtFixedRate(this::tick,0,20,TimeUnit.MILLISECONDS);
        vision.submit(()->{
            while(running) {
                try {
                    Frame captured=perception.capture();frame=captured;
                    observation=perception.infer(captured);
                    if(recorder!=null) recorder.record(captured,command.get(),decision);
                    Thread.sleep(100);
                } catch(InterruptedException ex) { Thread.currentThread().interrupt();break; }
                catch(Exception|LinkageError ex) {
                    observation=Vision.unavailable(System.nanoTime());failure="VISION:"+ex.getClass().getSimpleName();
                    try { Thread.sleep(250); } catch(InterruptedException interrupted) { break; }
                }
            }
        });
    }
    private void tick() {
        long now=System.nanoTime();
        try {
            Sensors read=hardware.read(now);sensors=read;
            decision=safety.step(System.nanoTime(),command.get(),read,observation);
            hardware.apply(drive.update(decision,read),System.nanoTime());
        } catch(Exception|LinkageError ex) {
            failure="HARDWARE:"+ex.getClass().getSimpleName();decision=Decision.stopped(Mode.FAULT,failure);
            try { hardware.apply(Actuation.off(),now); } catch(Exception ignored) {}
            // Never keep petting a watchdog after an I/O exception. Stop the deadline worker entirely.
            running=false;control.shutdown();
        }
    }
    public String telemetry() {
        Decision d=decision;Sensors s=sensors;
        String basic=String.format(Locale.ROOT,"{\"mode\":\"%s\",\"reason\":\"%s\",\"enabled\":%s,\"v\":%.4f,\"w\":%.4f,\"battery\":%.3f,\"failure\":\"%s\"}",
                d.mode(),d.reason(),d.enabled(),d.linear(),d.angular(),s==null?0:s.batteryV(),failure);
        if(hardware instanceof Simulator world && world.physicsState()!=null){
            SimDynamics.State p=world.physicsState();
            return basic.substring(0,basic.length()-1)+String.format(Locale.ROOT,
                ",\"physics\":\"%s\",\"x_m\":%.4f,\"y_m\":%.4f,\"left_mps\":%.4f,\"right_mps\":%.4f,\"brush_rad_s\":%.4f,\"belt_mps\":%.4f}",
                world.dynamicsName(),p.x(),p.y(),p.leftVelocity(),p.rightVelocity(),p.brushOmega(),p.beltSpeed());
        }
        return basic;
    }
    @Override public void close() {
        running=false;command.set(Command.idle(System.nanoTime()));control.shutdownNow();vision.shutdownNow();
        try { control.awaitTermination(2,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        try { hardware.apply(Actuation.off(),System.nanoTime()); } catch(Exception ignored) {}
        try { hardware.close(); } catch(Exception ignored) {}
        try { perception.close(); } catch(Exception ignored) {}
        if(recorder!=null) recorder.close();
    }
}
