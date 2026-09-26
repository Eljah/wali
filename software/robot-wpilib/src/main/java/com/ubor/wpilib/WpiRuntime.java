package com.ubor.wpilib;

import edu.wpi.first.hal.HAL;
import edu.wpi.first.wpilibj.RobotBase;

/** Desktop HAL only. This must never replace Pi4J on a real robot. */
public final class WpiRuntime {
    private static boolean initialized;
    private static boolean inUse;
    private WpiRuntime() {}
    public static synchronized void acquire() {
        String arch=System.getProperty("os.arch");
        if(!arch.equals("amd64")&&!arch.equals("x86_64")&&!arch.equals("aarch64"))
            throw new IllegalStateException("No packaged native profile for architecture "+arch);
        if(!initialized){
            if(!HAL.initialize(500,0))throw new IllegalStateException("WPILib HAL.initialize failed");
            if(RobotBase.isReal())throw new IllegalStateException("WPILib backend is DESKTOP SIMULATION ONLY");
            initialized=true;
        }
        // One world per JVM, because RIO voltage and HAL channel numbers are global resources.
        if(inUse)throw new IllegalStateException("Only one WPILib world can own HAL channels at a time");
        inUse=true;
    }
    public static synchronized void release(){inUse=false;}
}
