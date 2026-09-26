package com.ubor.pi;

import com.ubor.core.*;
import static com.ubor.core.Types.*;
import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.*;
import com.pi4j.io.spi.*;
import com.pi4j.io.i2c.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Pi4J 3.0.4 transport. Requires actual LinuxFS/GpioD providers and a commissioned board. */
public final class PiHardware implements Hardware {
    private final Context context;
    private final DigitalOutput arm,heartbeat;
    private final Map<Integer,DigitalInput> inputs=new HashMap<>();
    private final RegisterDevices.Mcp3008 adc;
    private final RegisterDevices.Ls7366r left,right;
    private final RegisterDevices.FourMotors motors;
    private final RangeCalibration range;
    private final int rightMotorSign,rightEncoderSign,leftMotorSign,leftEncoderSign;
    private boolean beat;
    public PiHardware(Path file)throws Exception {
        Properties p=new Properties();try(InputStream in=Files.newInputStream(file)){p.load(in);}
        if(!Boolean.parseBoolean(p.getProperty("calibration.complete","false")))throw new IllegalStateException("Hardware locked: complete commissioning, then approve calibration");
        Path base=file.toAbsolutePath().getParent();range=new RangeCalibration(base.resolve(p.getProperty("range.calibration","range-calibration.csv")));
        leftMotorSign=sign(p,"motor.left.sign");rightMotorSign=sign(p,"motor.right.sign");leftEncoderSign=sign(p,"encoder.left.sign");rightEncoderSign=sign(p,"encoder.right.sign");
        if(Integer.parseInt(p.getProperty("encoder.counts.per.wheel.rev","0"))!=2048)throw new IllegalStateException("Update DriveControl calibration or provide 2048 actual counts per wheel revolution");
        context=Pi4J.newAutoContext();
        DigitalOutput a=null;
        try {
            arm=output("software-arm",27);a=arm;arm.low();
            heartbeat=output("deadline-heartbeat",22);heartbeat.low();
            for(int pin:new int[]{23,24,25,5,6,12,13,16,26})inputs.put(pin,context.create(DigitalInput.newConfigBuilder(context).id("input-"+pin).address(pin).pull(PullResistance.PULL_UP).provider("gpiod-digital-input").build()));
            adc=new RegisterDevices.Mcp3008(bus("adc",0,0,500_000));
            RegisterDevices.Mcp23s17 directions=new RegisterDevices.Mcp23s17(bus("direction",0,1,500_000));
            left=new RegisterDevices.Ls7366r(bus("encoder-left",1,0,500_000));right=new RegisterDevices.Ls7366r(bus("encoder-right",1,1,500_000));
            I2C device=context.create(I2C.newConfigBuilder(context).id("motor-pwm").bus(1).device(0x40).provider("linuxfs-i2c").build());
            RegisterDevices.Pca9685 pwm=new RegisterDevices.Pca9685(tx->{try{if(device.write(tx)<0)throw new IOException("I2C write failed");}catch(RuntimeException e){throw new IOException(e);}});
            pwm.init();directions.init();left.init();right.init();motors=new RegisterDevices.FourMotors(directions,pwm);motors.set(Actuation.off());
        } catch(Exception|LinkageError e) {if(a!=null)a.low();context.shutdown();throw e;}
    }
    private static int sign(Properties p,String key) {int v=Integer.parseInt(p.getProperty(key,"0"));if(v!=1&&v!=-1)throw new IllegalArgumentException("Calibrate "+key+" to +1/-1");return v;}
    private DigitalOutput output(String id,int pin) {return context.create(DigitalOutput.newConfigBuilder(context).id(id).address(pin).initial(DigitalState.LOW).shutdown(DigitalState.LOW).provider("gpiod-digital-output").build());}
    private RegisterDevices.SpiBus bus(String id,int bus,int chipSelect,int baud) {
        Spi spi=context.create(Spi.newConfigBuilder(context).id(id).bus(bus).address(chipSelect).baud(baud).mode(SpiMode.MODE_0).provider("linuxfs-spi").build());
        return tx->{try{byte[] rx=new byte[tx.length];int status=spi.transfer(tx,rx);if(status<0)throw new IOException("SPI failure "+id);return rx;}catch(RuntimeException e){throw new IOException(e);}};
    }
    private boolean high(int pin){return inputs.get(pin).isHigh();}
    private double current(int channel)throws IOException {return adc.volts(channel)/.6825;}
    @Override public synchronized Sensors read(long now)throws Exception {
        double battery=adc.volts(0)*11;
        double l=current(1),r=current(2),brush=current(3),belt=current(4);
        double front=range.meters(adc.volts(5));
        // Timestamp is the START of the scan. The supervisor evaluates age AFTER read() completes.
        return new Sensors(now,battery,front,high(23),high(24),high(25),high(5),high(6),!high(26),!high(16),!high(12),!high(13),
                l,r,brush,belt,left.count()*leftEncoderSign,right.count()*rightEncoderSign);
    }
    @Override public synchronized void apply(Actuation command,long now)throws Exception {
        if(!command.enabled())arm.low();
        Actuation signed=new Actuation(command.enabled(),command.left()*leftMotorSign,command.right()*rightMotorSign,command.brush(),command.belt());
        motors.set(signed);
        if(command.enabled())arm.high();
        // The TPS3431 requires falling edges: toggling every completed 20 ms cycle gives one per 40 ms.
        // NEVER move this to a timer independent from successful I/O completion.
        beat=!beat;if(beat)heartbeat.high();else heartbeat.low();
    }
    @Override public synchronized void close()throws Exception {
        arm.low();try{motors.set(Actuation.off());}finally{heartbeat.low();context.shutdown();}
    }
}
