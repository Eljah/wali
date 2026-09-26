package com.ubor.wpilib;

import com.ubor.app.*;
import com.ubor.core.*;
import static com.ubor.core.Types.*;
import java.nio.file.*;
import java.util.*;

/** Executed only with the REAL WPILib and HAL native libraries. Missing JNI is a failure, not a skip. */
public final class WpiVerification {
    private static final List<String> passed=new ArrayList<>();
    private static void check(String name,boolean ok){if(!ok)throw new AssertionError(name);passed.add(name);System.out.println("PASS "+name);}
    private static void step(WpilibDynamics p,Actuation a,boolean power,boolean jam,int n){for(int i=0;i<n;i++)p.step(.005,a,power,jam);}
    public static void run(Path out)throws Exception{
        Files.createDirectories(out);passed.clear();
        Files.deleteIfExists(out.resolve("passed.txt"));Files.deleteIfExists(out.resolve("failure.txt"));
        Files.writeString(out.resolve("result.json"),"{\"status\":\"RUNNING_NOT_YET_PASSED\"}\n");
        try {
            try(WpilibDynamics p=new WpilibDynamics()){
                String origin=edu.wpi.first.wpilibj.simulation.DifferentialDrivetrainSim.class.getProtectionDomain().getCodeSource().getLocation().toString();
                Files.writeString(out.resolve("wpilib-origin.txt"),origin+"\njava.library.path="+System.getProperty("java.library.path")+"\n");
                check("real-library-origin",origin.contains("wpilibj"));
                step(p,Actuation.off(),false,false,100);
                check("zero-input-stays-at-origin",Math.abs(p.state().x())<1e-8&&Math.abs(p.state().y())<1e-8);
                step(p,new Actuation(true,.4,.4,0,0),true,false,200);
                check("straight-forward",p.state().x()>.05);
                check("straight-symmetric",Math.abs(p.state().y())<1e-6&&Math.abs(p.state().heading())<1e-6);
                check("nonzero-wheel-positions",p.state().leftPosition()>.05&&p.state().rightPosition()>.05);
                check("HAL-encoder-distance",Math.abs(p.halLeftDistance()-p.state().leftPosition())<.002);
                check("battery-uses-25V-not-12V",p.state().batteryV()>23&&p.state().batteryV()<25.6);
                double x=p.state().x();
                step(p,Actuation.off(),false,false,10);
                check("power-cut-coasts-not-teleport-stop",p.state().x()>x&&p.state().leftVelocity()>0);
                check("power-cut-motor-outputs-zero",Arrays.stream(p.appliedVoltages()).allMatch(v->v==0));
                check("power-cut-no-winding-current",p.state().leftCurrent()==0&&p.state().rightCurrent()==0);
            }
            try(WpilibDynamics p=new WpilibDynamics()){
                step(p,new Actuation(true,.2,.6,0,0),true,false,300);
                check("differential-turn-CCW",p.state().heading()>.1);
                double delta=Math.atan2(Math.sin(p.gyroRadians()-p.state().heading()),Math.cos(p.gyroRadians()-p.state().heading()));
                check("HAL-gyro-sign-convention",Math.abs(delta)<1e-7);
            }
            try(WpilibDynamics p=new WpilibDynamics()){
                step(p,new Actuation(true,-.3,-.3,0,0),true,false,200);
                check("reverse-sign",p.state().x()<-.05&&p.state().leftPosition()<0);
            }
            try(WpilibDynamics p=new WpilibDynamics()){
                step(p,new Actuation(true,0,0,.6,.45),true,false,400);
                check("brush-has-angular-dynamics",p.state().brushOmega()>3.77);
                check("belt-uses-roller-angular-dynamics",p.state().beltSpeed()>.054);
                step(p,new Actuation(true,0,0,.8,.8),true,true,100);
                check("jam-locks-mechanisms",p.state().brushOmega()==0&&p.state().beltSpeed()==0);
                check("jam-raises-winding-currents",p.state().brushCurrent()>1.8&&p.state().beltCurrent()>1.8);
            }
            try(WpilibDynamics p=new WpilibDynamics();Simulator world=new Simulator(p)){
                long t=1_000_000_000L;world.read(t);world.apply(new Actuation(true,.3,.3,0,0),t);
                world.advance(t+250_000_000L);
                check("watchdog-latches-permit",!world.permit);
                check("watchdog-cuts-voltage",Arrays.stream(p.appliedVoltages()).allMatch(v->v==0));
                check("explicit-physical-arm",world.physicalArm());
                world.emergency=true;Sensors s=world.read(t+270_000_000L);
                check("HAL-estop-round-trip",s.emergency()&&!s.hardwarePermit());
                world.emergency=false;world.read(t+290_000_000L);
                check("estop-clear-does-not-rearm",!world.permit);
            }
            try(Simulator world=new Simulator(new WpilibDynamics())){
                Demo.Result result=Demo.run(out.resolve("oracle-demo"),90,world);
                check("same-controller-collects-three",result.collected()==3);
                check("hazard-remains-on-floor",world.objects().stream().anyMatch(o->o.label==Label.HAZARD&&o.stage==0));
            }
            try(Simulator world=new Simulator(new WpilibDynamics())){
                NetworkSmoke.run(out.resolve("network-smoke.txt"),world);
                check("real-TCP-HMAC-WPILib-plant-disconnect",true);
            }
            Files.writeString(out.resolve("result.json"),"{\"status\":\"PASS\",\"backend\":\""+WpilibDynamics.BACKEND+"\",\"assertions\":"+passed.size()+",\"vision\":\"ORACLE_NOT_ML\",\"physical_hardware\":false}\n");
        } catch(Exception|Error e){
            Files.writeString(out.resolve("result.json"),"{\"status\":\"FAIL\",\"passed_before_failure\":"+passed.size()+",\"physical_hardware\":false}\n");
            Files.writeString(out.resolve("failure.txt"),e.toString()+"\n");throw e;
        } finally {Files.write(out.resolve("passed.txt"),passed);}
    }
}
