package com.ubor.app;

import static com.ubor.core.Types.*;
import java.nio.file.*;
import java.util.*;

/** Contract tests with an EXPLICIT test double. These are NOT WPILib physics tests. */
public final class SimBoundaryTest {
    private static final List<String> passed=new ArrayList<>();
    private static void check(String name,boolean ok){if(!ok)throw new AssertionError(name);passed.add(name);System.out.println("PASS "+name);}
    private static final class Probe implements SimDynamics {
        int steps,reads,closes;double totalDt;boolean lastPowered,lastJam;
        double px,py,heading,brush=10,belt=.2;long specialLeftCount=4567;
        State state=new State(0,0,0,0,0,0,0,0,0,25,0,0,0,0);
        public String name(){return "TEST_DOUBLE_NOT_WPILIB";}
        public State step(double dt,Actuation a,boolean power,boolean jam){
            steps++;totalDt+=dt;lastPowered=power;lastJam=jam;
            state=new State(px,py,heading,.17,.11,.8,.7,brush,belt,24.8,.6,.7,.8,.9);return state;
        }
        public State state(){return state;}
        public Sensors readSensors(long now,Sensors s){
            reads++;
            return new Sensors(now,s.batteryV(),s.frontRangeM(),s.emergency(),s.bumper(),s.cliff(),s.coverOpen(),
                s.hopperFull(),s.hardwarePermit(),s.driverFault(),s.intakeBeam(),s.dropBeam(),s.leftA(),s.rightA(),s.brushA(),s.beltA(),specialLeftCount,s.rightCounts());
        }
        public void close(){closes++;}
    }
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args.length==0?"boundary-report":args[0]);Files.createDirectories(out);passed.clear();
        Probe p=new Probe();p.px=.3;p.py=.2;p.heading=.1;
        Simulator world=new Simulator(p);long t=1_000_000_000L;
        world.read(t);world.apply(new Actuation(true,.3,.4,.6,.45),t);Sensors s=world.read(t+20_000_000L);
        check("backend-identity",world.dynamicsName().equals("TEST_DOUBLE_NOT_WPILIB"));
        check("physics-5ms-substeps",p.steps==4);
        check("full-dt-accounted",Math.abs(p.totalDt-.02)<1e-12);
        check("pose-not-reintegrated-by-world",world.x==.3&&world.y==.2&&world.heading==.1);
        check("wheel-speeds-from-backend",world.leftSpeed==.17&&world.rightSpeed==.11);
        check("battery-from-backend",s.batteryV()==24.8);
        check("currents-from-backend",s.leftA()==.6&&s.rightA()==.7&&s.brushA()==.8&&s.beltA()==.9);
        check("sensor-adapter-called",p.reads==2);
        check("encoder-count-through-adapter",s.leftCounts()==4567);
        check("actuator-power-propagated",p.lastPowered);
        world.jam=true;world.read(t+40_000_000L);check("jam-propagated",p.lastJam);
        world.emergency=true;world.read(t+60_000_000L);
        check("physical-stop-removes-power",!p.lastPowered&&!world.permit);
        world.emergency=false;world.read(t+80_000_000L);
        check("clear-stop-does-not-rearm",!world.permit);
        check("new-physical-arm-required",world.physicalArm());
        world.guardOpen=true;check("cannot-arm-open-guard",!world.physicalArm());
        world.close();world.close();check("backend-closed-once",p.closes==1);
        boolean rejected=false;try{new SimDynamics.State(Double.NaN,0,0,0,0,0,0,0,0,25,0,0,0,0);}catch(IllegalArgumentException e){rejected=true;}
        check("NaN-physics-rejected",rejected);
        Probe contact=new Probe();
        try(Simulator w=new Simulator(contact)){
            w.replaceLitter(List.of(new Simulator.Litter(.63,0,Label.HAZARD)));
            w.read(t);w.apply(new Actuation(true,0,0,.6,.45),t);w.read(t+20_000_000L);
            check("hazard-has-no-physical-immunity",w.objects().get(0).stage==1);
        }
        Probe stopped=new Probe();stopped.brush=0;stopped.belt=0;
        try(Simulator w=new Simulator(stopped)){
            w.replaceLitter(List.of(new Simulator.Litter(.63,0,Label.PAPER)));
            w.read(t);w.apply(new Actuation(true,0,0,.6,.45),t);w.read(t+20_000_000L);
            check("command-alone-cannot-pick-up",w.objects().get(0).stage==0);
        }
        Probe stalled=new Probe();
        try(Simulator w=new Simulator(stalled)){
            w.read(t);w.apply(new Actuation(true,.4,.4,0,0),t);w.read(t+250_000_000L);
            check("watchdog-reaches-physics-boundary",!w.permit&&!stalled.lastPowered);
        }
        Files.write(out.resolve("passed.txt"),passed);
        Files.writeString(out.resolve("result.json"),"{\"status\":\"PASS\",\"assertions\":"+passed.size()+",\"uses_wpilib\":false,\"backend\":\"TEST_DOUBLE_NOT_WPILIB\"}\n");
    }
}
