package com.ubor.core;

import static com.ubor.core.Types.*;

/** Wheel speed control with feed-forward, anti-windup and duty slew limiting. */
public final class DriveControl {
    public static final double TRACK_M=.500, WHEEL_R_M=.100, COUNTS_PER_TURN=2048, MAX_WHEEL_MPS=.40;
    private long prevTime=Long.MIN_VALUE, prevLeft,prevRight;
    private final Loop left=new Loop(),right=new Loop();
    public static double[] wheelTargets(double v,double w) {
        double l=v-w*TRACK_M/2,r=v+w*TRACK_M/2;
        double scale=Math.max(1,Math.max(Math.abs(l),Math.abs(r))/MAX_WHEEL_MPS);
        return new double[]{l/scale,r/scale};
    }
    public Actuation update(Decision d,Sensors s) {
        double dt=prevTime==Long.MIN_VALUE?.02:(s.timeNanos()-prevTime)*1e-9;
        double lm=(s.leftCounts()-prevLeft)*2*Math.PI*WHEEL_R_M/COUNTS_PER_TURN;
        double rm=(s.rightCounts()-prevRight)*2*Math.PI*WHEEL_R_M/COUNTS_PER_TURN;
        boolean initialized=prevTime!=Long.MIN_VALUE;
        prevTime=s.timeNanos();prevLeft=s.leftCounts();prevRight=s.rightCounts();
        if(!d.enabled() || dt<=0 || dt>.15) { left.reset();right.reset();return Actuation.off(); }
        double[] t=wheelTargets(d.linear(),d.angular());
        return new Actuation(true,left.update(t[0],initialized?lm/dt:0,dt),
                right.update(t[1],initialized?rm/dt:0,dt),d.brush(),d.belt());
    }
    private static final class Loop {
        double integral,last;
        double update(double target,double measured,double dt) {
            double error=target-measured;
            double candidate=clamp(integral+error*dt,-.20,.20);
            double raw=target/MAX_WHEEL_MPS+1.2*error+1.0*candidate;
            if(Math.abs(raw)<1 || Math.signum(error)!=Math.signum(raw)) integral=candidate;
            double desired=clamp(raw,-1,1);
            last=clamp(desired,last-2*dt,last+2*dt);
            return last;
        }
        void reset() { integral=0;last=0; }
    }
}
