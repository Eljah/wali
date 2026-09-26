package com.ubor.core;

/** Pinhole / flat-ground geometry. This does not infer depth above uneven ground. */
public record GroundProjector(double fx,double fy,double cx,double cy,double heightM,double downPitchRad) {
    public GroundProjector {
        if(fx<=0||fy<=0||heightM<=0||!Double.isFinite(downPitchRad)) throw new IllegalArgumentException("Camera calibration");
    }
    /** Pixel coordinates must be the object's ground contact, not bounding-box centre. */
    public double[] project(double pixelX,double pixelY) {
        double right=(pixelX-cx)/fx,down=(pixelY-cy)/fy;
        double forward=Math.cos(downPitchRad)-down*Math.sin(downPitchRad);
        double verticalDown=Math.sin(downPitchRad)+down*Math.cos(downPitchRad);
        if(verticalDown<=.04 || forward<=0) throw new IllegalArgumentException("Ray misses accepted ground region");
        double t=heightM/verticalDown;
        double x=t*forward,y=-t*right;
        return new double[]{x,y,Math.hypot(x,y),Math.atan2(y,x)};
    }
}
