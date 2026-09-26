package com.ubor.app;

import com.ubor.core.*;
import static com.ubor.core.Types.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/** No testing framework download required. Every assertion is reported individually. */
public final class SelfTest {
    private static final List<String> passed=new ArrayList<>();
    private static void check(String name,boolean success){if(!success)throw new AssertionError(name);passed.add(name);System.out.println("PASS "+name);}
    private static void throwsError(String name,Throwing action){try{action.run();}catch(Exception ex){check(name,true);return;}throw new AssertionError(name);}
    @FunctionalInterface interface Throwing{void run()throws Exception;}
    private static long t=1_000_000_000L;
    private static Sensors sensors(long n){return new Sensors(n,25.6,2,false,false,false,false,false,true,false,false,false,0,0,0,0,0,0);}
    private static Command cmd(long n){return new Command(1,n,true,true,false,false,false,false,.3,0,Label.PAPER);}
    private static Vision vis(long n){return new Vision(n,true,Label.PAPER,.99,1,0,.2,0);}
    private static Sensors fault(long n,String type){return new Sensors(n,type.equals("voltage")?22:25.6,type.equals("obstacle")?.2:2,
            type.equals("emergency"),type.equals("bumper"),type.equals("cliff"),type.equals("cover"),type.equals("full"),!type.equals("permit"),type.equals("driver"),false,false,
            type.equals("overload")?3:0,0,0,0,0,0);}
    public static void run(Path out)throws Exception {
        passed.clear();Files.createDirectories(out);
        check("fresh/current",fresh(t,t,200));check("fresh/future-rejected",!fresh(t,t+1,200));check("fresh/expired",!fresh(t,t-201,200));
        throwsError("command/NaN",()->new Command(0,t,true,true,false,false,false,false,Double.NaN,0,Label.NONE));
        throwsError("actuation/bounds",()->new Actuation(true,2,0,0,0));
        SafetyController safety=new SafetyController();check("manual/valid",safety.step(t,cmd(t),sensors(t),vis(t)).enabled());
        check("lease/expired",!new SafetyController().step(t,cmd(t-300_000_000L),sensors(t),vis(t)).enabled());
        check("deadman/release",!new SafetyController().step(t,Command.idle(t),sensors(t),vis(t)).enabled());
        for(String f:List.of("emergency","bumper","cliff","cover","full","permit","driver","voltage","obstacle"))check("interlock/"+f,!new SafetyController().step(t,cmd(t),fault(t,f),vis(t)).enabled());
        check("sensor/stale",!new SafetyController().step(t,cmd(t),sensors(t-200_000_000L),vis(t)).enabled());
        SafetyController latch=new SafetyController();latch.step(t,cmd(t),fault(t,"emergency"),vis(t));
        check("fault/latches",latch.step(t+20_000_000L,cmd(t+20_000_000L),sensors(t+20_000_000L),vis(t+20_000_000L)).mode()==Mode.FAULT);
        Command reset=new Command(2,t+40_000_000L,false,false,false,false,true,false,0,0,Label.NONE);
        check("fault/explicit-reset",latch.step(t+40_000_000L,reset,sensors(t+40_000_000L),vis(t+40_000_000L)).mode()==Mode.DISARMED);
        check("deadline/trips",safety.step(t+200_000_000L,cmd(t+200_000_000L),sensors(t+200_000_000L),vis(t+200_000_000L)).reason().equals("CONTROL_DEADLINE"));
        SafetyController overload=new SafetyController();Decision d=null;
        for(int i=0;i<17;i++){long n=t+i*20_000_000L;d=overload.step(n,cmd(n),fault(n,"overload"),vis(n));}
        check("current/stall-latches",d.mode()==Mode.FAULT&&d.reason().equals("MOTOR_STALL"));
        Command auto=new Command(0,t,true,true,true,false,false,false,0,0,Label.UNKNOWN);
        check("vision/missing-stops",!new SafetyController().step(t,auto,sensors(t),Vision.unavailable(t)).enabled());
        check("vision/old-stops",!new SafetyController().step(t,auto,sensors(t),vis(t-300_000_000L)).enabled());
        check("vision/hazard-stops",!new SafetyController().step(t,auto,sensors(t),new Vision(t,true,Label.HAZARD,.9,.5,0,.2,0)).enabled());
        check("vision/low-confidence",!new SafetyController().step(t,auto,sensors(t),new Vision(t,true,Label.PAPER,.8,.5,0,.2,0)).enabled());
        Command pickup=new Command(0,t,true,true,false,true,false,false,.3,0,Label.PAPER);
        check("pickup/manual-speed-clamp",new SafetyController().step(t,pickup,sensors(t),vis(t)).linear()==.08);
        Command unsafe=new Command(0,t,true,true,false,true,false,false,.3,0,Label.UNKNOWN);
        check("pickup/unknown-not-collected",new SafetyController().step(t,unsafe,sensors(t),vis(t)).brush()==0);
        SafetyController collect=new SafetyController();Vision close=new Vision(t,true,Label.PAPER,.99,.2,0,.2,0);
        check("pickup/auto-start",collect.step(t,auto,sensors(t),close).mode()==Mode.COLLECT);
        long n=t+20_000_000L;Command auto2=new Command(1,n,true,true,true,false,false,false,0,0,Label.UNKNOWN);
        Sensors intake=new Sensors(n,25.6,2,false,false,false,false,false,true,false,true,false,0,0,0,0,0,0);
        d=collect.step(n,auto2,intake,vis(n));check("pickup/stop-chassis-after-intake",d.linear()==0&&d.brush()==0&&d.belt()>0);
        n+=20_000_000L;Sensors drop=new Sensors(n,25.6,2,false,false,false,false,false,true,false,false,true,0,0,0,0,0,0);
        d=collect.step(n,new Command(2,n,true,true,true,false,false,false,0,0,Label.NONE),drop,vis(n));check("pickup/drop-confirmed",d.reason().equals("PICKUP_CONFIRMED"));
        byte[] key=new byte[32],nonce=ControlProtocol.challenge();Arrays.fill(key,(byte)42);
        byte[] packet=ControlProtocol.encode(cmd(t),nonce,key);Command decoded=ControlProtocol.decode(packet,nonce,key,-1,t);
        check("protocol/round-trip",decoded.linear()==.3&&decoded.arm());
        throwsError("protocol/replay-rejected",()->ControlProtocol.decode(packet,nonce,key,1,t));
        byte[] bad=packet.clone();bad[15]^=1;throwsError("protocol/tampering-rejected",()->ControlProtocol.decode(bad,nonce,key,-1,t));
        throwsError("protocol/other-session",()->ControlProtocol.decode(packet,ControlProtocol.challenge(),key,-1,t));
        throwsError("protocol/short-packet",()->ControlProtocol.decode(new byte[3],nonce,key,-1,t));
        List<byte[]> spi=new ArrayList<>();RegisterDevices.Mcp3008 adc=new RegisterDevices.Mcp3008(tx->{spi.add(tx);return new byte[]{0,2,0};});
        check("SPI/ADC-decode",adc.read(3)==512);check("SPI/ADC-command",Arrays.equals(spi.get(0),new byte[]{1,(byte)0xB0,0}));
        throwsError("SPI/ADC-invalid-channel",()->adc.read(9));
        List<byte[]> i2c=new ArrayList<>();RegisterDevices.Pca9685 pwm=new RegisterDevices.Pca9685(tx->i2c.add(tx));pwm.duty(2,.5);
        check("PWM/channel-register",(i2c.get(0)[0]&255)==14);check("PWM/half-duty",(i2c.get(0)[4]&255)==8);
        pwm.duty(0,0);check("PWM/full-off-bit",i2c.get(1)[4]==16);
        RegisterDevices.Mcp23s17 io=new RegisterDevices.Mcp23s17(tx->{spi.add(tx);return tx;});io.init();
        check("GPIO-expander/init-direction",Arrays.equals(spi.get(spi.size()-1),new byte[]{0x40,0,(byte)0xF0}));
        i2c.clear();new RegisterDevices.FourMotors(io,pwm).set(new Actuation(true,.3,-.4,.5,.6));
        check("PWM/no-ALL-register-write-after-channel-duty",i2c.get(i2c.size()-1).length==5);
        final int[] k={0};RegisterDevices.Ls7366r counter=new RegisterDevices.Ls7366r(tx->{int raw=k[0]++==0?Integer.MAX_VALUE-1:Integer.MIN_VALUE+2;return ByteBuffer.allocate(5).put((byte)0).putInt(raw).array();});
        counter.count();check("encoder/signed-wrap",counter.count()==4);
        double[] wheels=DriveControl.wheelTargets(.2,.4);check("kinematics/differential",Math.abs(wheels[0]-.1)<1e-9&&Math.abs(wheels[1]-.3)<1e-9);
        Actuation off=new DriveControl().update(Decision.stopped(Mode.DISARMED,"TEST"),sensors(t));check("drive/immediate-disable",!off.enabled()&&off.left()==0);
        Simulator sim=new Simulator();sim.advance(t);sim.apply(new Actuation(true,.5,.5,0,0),t);sim.advance(t+250_000_000L);
        check("sim/watchdog-drops-permit",!sim.read(t+250_000_000L).hardwarePermit());sim.close();
        Demo.Result result=Demo.run(out.resolve("integration"),90);check("integration/oracle-collects-three",result.collected()==3);
        check("integration/hazard-left-behind",result.remaining()==1);
        StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite name=\"UBOR.offline\" tests=\""+passed.size()+"\" failures=\"0\">\n");
        for(String name:passed)xml.append("  <testcase classname=\"com.ubor.app.SelfTest\" name=\"").append(name).append("\"/>\n");xml.append("</testsuite>\n");Files.writeString(out.resolve("junit.xml"),xml);
        Files.writeString(out.resolve("summary.txt"),"PASS "+passed.size()+" assertions\nJava "+System.getProperty("java.version")+"\nOnly dependency-free core/app; no Pi4J, DL4J, ARM, GPIO or camera execution.\n"+String.join("\n",passed)+"\n");
        System.out.println("PASS "+passed.size()+" assertions");
    }
}
