package com.ubor.core;

import static com.ubor.core.Types.*;

/** Deterministic supervisor; independent hardware still has to remove motor power on a JVM stall. */
public final class SafetyController {
    public static final long LEASE_NS=200_000_000L, SENSOR_NS=150_000_000L, VISION_NS=250_000_000L;
    private Mode mode=Mode.DISARMED;
    private String fault="";
    private long lastTick=Long.MIN_VALUE, overloadSince=Long.MIN_VALUE, collectionSince;
    private boolean sawIntake;
    public synchronized Decision step(long now, Command c, Sensors s, Vision v) {
        String trip="";
        if(lastTick!=Long.MIN_VALUE && (now<=lastTick || now-lastTick>150_000_000L)) trip="CONTROL_DEADLINE";
        lastTick=now;
        if(!s.numbersValid() || !fresh(now,s.timeNanos(),SENSOR_NS)) trip="SENSOR_INVALID_OR_STALE";
        else if(s.emergency()) trip="EMERGENCY_STOP";
        else if(s.bumper()) trip="BUMPER";
        else if(s.cliff()) trip="CLIFF";
        else if(s.coverOpen()) trip="GUARD_OPEN";
        else if(s.driverFault()) trip="DRIVER_FAULT";
        else if(s.batteryV()<22.4) trip="LOW_BATTERY";
        if(c.stop()) trip="REMOTE_STOP";
        boolean overload=s.leftA()>2.5 || s.rightA()>2.5 || s.brushA()>1.8 || s.beltA()>1.8;
        if(overload) {
            if(overloadSince==Long.MIN_VALUE) overloadSince=now;
            if(now-overloadSince>=300_000_000L) trip="MOTOR_STALL";
        } else overloadSince=Long.MIN_VALUE;
        if(!trip.isEmpty()) { fault=trip;mode=Mode.FAULT; }
        if(mode==Mode.FAULT) {
            // A stale or replayed packet cannot reset a latch.
            if(trip.isEmpty() && fresh(now,c.receivedNanos(),LEASE_NS) && c.reset() && !c.arm() && !c.deadman()) {
                mode=Mode.DISARMED;fault="";
            }
            return Decision.stopped(mode,fault.isEmpty()?"RESET":fault);
        }
        if(!fresh(now,c.receivedNanos(),LEASE_NS) || !c.deadman() || !c.arm()) {
            mode=Mode.DISARMED;return Decision.stopped(mode,"DEADMAN_OR_LINK");
        }
        if(!s.hardwarePermit()) { mode=Mode.DISARMED;return Decision.stopped(mode,"PHYSICAL_ARM_REQUIRED"); }
        if(s.hopperFull()) { mode=Mode.DISARMED;return Decision.stopped(mode,"HOPPER_FULL"); }
        if(s.frontRangeM()<.35) { mode=Mode.DISARMED;return Decision.stopped(mode,"OBSTACLE_STOP"); }
        if(!c.auto()) {
            mode=Mode.TELEOP;
            boolean collecting=c.collect() && c.label().collectible();
            double linear=clamp(c.linear(),-.05,collecting?.08:.30);
            if(collecting) linear=Math.max(0,linear);
            return new Decision(mode,true,linear,clamp(c.angular(),-1,1),collecting?.60:0,collecting?.45:0,
                    collecting?"OPERATOR_VERIFIED_PICKUP":"MANUAL");
        }
        if(!v.healthy() || !fresh(now,v.timeNanos(),VISION_NS)) {
            mode=Mode.AUTO;return Decision.stopped(mode,"VISION_UNAVAILABLE_OR_STALE");
        }
        if(v.label()==Label.HAZARD && v.confidence()>=.60 && !(mode==Mode.COLLECT&&(sawIntake||s.intakeBeam()))) {
            mode=Mode.AUTO;return Decision.stopped(mode,"HAZARD_VISION_STOP");
        }
        if(mode==Mode.COLLECT) {
            sawIntake |= s.intakeBeam();
            if(sawIntake && s.dropBeam()) {
                mode=Mode.AUTO;return Decision.stopped(mode,"PICKUP_CONFIRMED");
            }
            if(now-collectionSince>10_000_000_000L) {
                fault="PICKUP_TIMEOUT";mode=Mode.FAULT;return Decision.stopped(mode,fault);
            }
            return new Decision(mode,true,sawIntake?0:.08,0,sawIntake?0:.60,.45,"PICKUP_ACTIVE");
        }
        mode=Mode.AUTO;
        boolean target=v.label().collectible() && v.confidence()>=.90;
        if(target && v.rangeM()>=0 && v.rangeM()<=.38 && Math.abs(v.bearingRad())<=.15) {
            mode=Mode.COLLECT;collectionSince=now;sawIntake=false;
            return new Decision(mode,true,.08,0,.60,.45,"PICKUP_START");
        }
        // No exploration / learned roaming when there is no admitted target in the supervised R01 experiment.
        if(!target) return Decision.stopped(mode,"NO_ADMITTED_TARGET");
        return new Decision(mode,true,clamp(v.proposedLinear(),0,.20),clamp(v.proposedAngular(),-1,1),0,0,"MODEL_PROPOSAL");
    }
}
