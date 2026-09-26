package com.ubor.core;

import java.awt.image.BufferedImage;

/** SI units throughout. Timestamps must use the receiver's monotonic clock. */
public final class Types {
    private Types() {}
    public enum Mode { DISARMED, TELEOP, AUTO, COLLECT, FAULT }
    public enum Label {
        NONE, PAPER, PLASTIC_BOTTLE, METAL_CAN, HAZARD, UNKNOWN;
        public boolean collectible() { return this==PAPER || this==PLASTIC_BOTTLE || this==METAL_CAN; }
    }
    public record Command(long sequence, long receivedNanos, boolean deadman, boolean arm,
                          boolean auto, boolean collect, boolean reset, boolean stop,
                          double linear, double angular, Label label) {
        public Command {
            if (sequence<0 || label==null || !Double.isFinite(linear) || !Double.isFinite(angular)
                    || Math.abs(linear)>1 || Math.abs(angular)>4) throw new IllegalArgumentException("Invalid command");
        }
        public static Command idle(long now) { return new Command(0,now,false,false,false,false,false,false,0,0,Label.UNKNOWN); }
    }
    public record Sensors(long timeNanos, double batteryV, double frontRangeM,
                          boolean emergency, boolean bumper, boolean cliff,
                          boolean coverOpen, boolean hopperFull, boolean hardwarePermit,
                          boolean driverFault, boolean intakeBeam, boolean dropBeam,
                          double leftA, double rightA, double brushA, double beltA,
                          long leftCounts, long rightCounts) {
        public boolean numbersValid() {
            return Double.isFinite(batteryV) && batteryV>=0 && Double.isFinite(frontRangeM) && frontRangeM>=0
                && validCurrent(leftA) && validCurrent(rightA) && validCurrent(brushA) && validCurrent(beltA);
        }
        private static boolean validCurrent(double a) { return Double.isFinite(a) && a>=0 && a<30; }
    }
    public record Vision(long timeNanos, boolean healthy, Label label, double confidence,
                         double rangeM, double bearingRad, double proposedLinear, double proposedAngular) {
        public Vision {
            if (label==null || !Double.isFinite(confidence) || confidence<0 || confidence>1
                    || !Double.isFinite(rangeM) || !Double.isFinite(bearingRad)
                    || !Double.isFinite(proposedLinear) || !Double.isFinite(proposedAngular))
                throw new IllegalArgumentException("Non-finite or invalid vision output");
        }
        public static Vision unavailable(long now) { return new Vision(now,false,Label.UNKNOWN,0,99,0,0,0); }
    }
    public record Frame(long timeNanos, BufferedImage image) {
        public Frame { if(image==null) throw new IllegalArgumentException("Null camera frame"); }
    }
    public record Decision(Mode mode, boolean enabled, double linear, double angular,
                           double brush, double belt, String reason) {
        public static Decision stopped(Mode m,String reason) { return new Decision(m,false,0,0,0,0,reason); }
    }
    public record Actuation(boolean enabled,double left,double right,double brush,double belt) {
        public Actuation {
            for(double x:new double[]{left,right,brush,belt}) if(!Double.isFinite(x)||Math.abs(x)>1.000001)
                throw new IllegalArgumentException("Actuator duty out of range");
        }
        public static Actuation off() { return new Actuation(false,0,0,0,0); }
    }
    public interface Hardware extends AutoCloseable {
        Sensors read(long now) throws Exception;
        void apply(Actuation command, long now) throws Exception;
        @Override void close() throws Exception;
    }
    public interface Perception extends AutoCloseable {
        Frame capture() throws Exception;
        Vision infer(Frame frame) throws Exception;
        @Override default void close() throws Exception {}
    }
    public static double clamp(double x,double low,double high) { return Math.max(low,Math.min(high,x)); }
    public static boolean fresh(long now,long then,long maxAgeNanos) { long age=now-then; return age>=0 && age<=maxAgeNanos; }
}
