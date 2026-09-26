package com.ubor.app;

import static com.ubor.core.Types.*;

/** Replaceable physics only. World contacts and perception do not live in this interface. */
public interface SimDynamics extends AutoCloseable {
    /** SI units. Positions are integrated by the selected physics backend, never by Simulator. */
    record State(double x, double y, double heading,
                 double leftVelocity, double rightVelocity, double leftPosition, double rightPosition,
                 double brushOmega, double beltSpeed, double batteryV,
                 double leftCurrent, double rightCurrent, double brushCurrent, double beltCurrent) {
        public State {
            for (double value : new double[]{x,y,heading,leftVelocity,rightVelocity,leftPosition,
                    rightPosition,brushOmega,beltSpeed,batteryV,leftCurrent,rightCurrent,brushCurrent,beltCurrent})
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite physics state");
            if (batteryV < 0 || leftCurrent < 0 || rightCurrent < 0 || brushCurrent < 0 || beltCurrent < 0)
                throw new IllegalArgumentException("Negative voltage or sensor current");
        }
    }
    String name();
    State step(double dtSeconds, Actuation request, boolean circuitClosed, boolean collectorJammed);
    State state();
    /** A HAL backend can round-trip the sensor values through its simulated devices. */
    default Sensors readSensors(long now, Sensors worldSensors) { return worldSensors; }
    @Override default void close() {}
}
