package com.ubor.wpilib;

import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Physical assumptions, not measured manufacturer specifications. SI except motor free RPM. */
public record WpiParameters(double massKg,double yawInertia,double wheelRadius,double trackWidth,
        double driveReduction,double driveStallTorque,double driveStallAmps,double driveFreeAmps,double driveFreeRpm,
        double brushReduction,double brushInertia,double brushStallTorque,double brushStallAmps,double brushFreeRpm,
        double beltReduction,double beltInertia,double beltStallTorque,double beltStallAmps,double beltFreeRpm,
        double rollerRadius,double motorNominalVolts,double batteryOpenVolts,double batteryResistance,
        double logicCurrent,double coastDragFraction) {
    public WpiParameters {
        for(double value:new double[]{massKg,yawInertia,wheelRadius,trackWidth,driveReduction,driveStallTorque,
            driveStallAmps,driveFreeRpm,brushReduction,brushInertia,brushStallTorque,brushStallAmps,brushFreeRpm,
            beltReduction,beltInertia,beltStallTorque,beltStallAmps,beltFreeRpm,rollerRadius,motorNominalVolts,batteryOpenVolts})
            if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException("Positive finite physical parameter required");
        for(double value:new double[]{driveFreeAmps,batteryResistance,logicCurrent,coastDragFraction})
            if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Non-negative finite parameter required");
        if(driveFreeAmps>=driveStallAmps||coastDragFraction>1||brushStallAmps<=.1||beltStallAmps<=.1)
            throw new IllegalArgumentException("Invalid motor or coast parameter");
        // The R01 speed controller has these geometry constants. Do not silently mismatch encoder scale.
        if(Math.abs(wheelRadius-.1)>1e-9||Math.abs(trackWidth-.5)>1e-9)
            throw new IllegalArgumentException("Update DriveControl geometry together with wheel/track geometry");
    }
    public static WpiParameters defaults() {
        return new WpiParameters(30,2.4,.1,.5,112.5,.09,5,.18,4500,
            25,.0035,.035,3,4500,38.8,.006,.035,3,4000,.025,24,25.6,.09,1,.03);
    }
    public static WpiParameters load(Path path)throws IOException {
        if(path==null)return defaults();
        Properties p=new Properties();try(Reader r=Files.newBufferedReader(path)){p.load(r);}
        Set<String> known=Set.of("mass.kg","yaw.inertia.kgm2","wheel.radius.m","track.width.m","drive.reduction",
            "drive.stall.torque.nm","drive.stall.current.a","drive.free.current.a","drive.free.rpm",
            "brush.reduction","brush.inertia.kgm2","brush.stall.torque.nm","brush.stall.current.a","brush.free.rpm",
            "belt.reduction","belt.inertia.kgm2","belt.stall.torque.nm","belt.stall.current.a","belt.free.rpm",
            "roller.radius.m","motor.nominal.v","battery.open.v","battery.resistance.ohm","logic.current.a","coast.drag.fraction");
        for(String key:p.stringPropertyNames())if(!known.contains(key))throw new IllegalArgumentException("Unknown property: "+key);
        WpiParameters d=defaults();
        return new WpiParameters(n(p,"mass.kg",d.massKg),n(p,"yaw.inertia.kgm2",d.yawInertia),
            n(p,"wheel.radius.m",d.wheelRadius),n(p,"track.width.m",d.trackWidth),
            n(p,"drive.reduction",d.driveReduction),n(p,"drive.stall.torque.nm",d.driveStallTorque),
            n(p,"drive.stall.current.a",d.driveStallAmps),n(p,"drive.free.current.a",d.driveFreeAmps),n(p,"drive.free.rpm",d.driveFreeRpm),
            n(p,"brush.reduction",d.brushReduction),n(p,"brush.inertia.kgm2",d.brushInertia),
            n(p,"brush.stall.torque.nm",d.brushStallTorque),n(p,"brush.stall.current.a",d.brushStallAmps),n(p,"brush.free.rpm",d.brushFreeRpm),
            n(p,"belt.reduction",d.beltReduction),n(p,"belt.inertia.kgm2",d.beltInertia),
            n(p,"belt.stall.torque.nm",d.beltStallTorque),n(p,"belt.stall.current.a",d.beltStallAmps),n(p,"belt.free.rpm",d.beltFreeRpm),
            n(p,"roller.radius.m",d.rollerRadius),n(p,"motor.nominal.v",d.motorNominalVolts),
            n(p,"battery.open.v",d.batteryOpenVolts),n(p,"battery.resistance.ohm",d.batteryResistance),
            n(p,"logic.current.a",d.logicCurrent),n(p,"coast.drag.fraction",d.coastDragFraction));
    }
    private static double n(Properties p,String key,double fallback){return Double.parseDouble(p.getProperty(key,Double.toString(fallback)));}
}
