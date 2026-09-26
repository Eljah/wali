package com.ubor.pi;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Measured monotonic sensor voltage->distance lookup. No fabricated calibration curve. */
public final class RangeCalibration {
    private final double[][] points;
    public RangeCalibration(Path csv)throws IOException {
        List<double[]> rows=new ArrayList<>();
        for(String raw:Files.readAllLines(csv)) {
            String s=raw.trim();if(s.isEmpty()||s.startsWith("#")||s.startsWith("voltage"))continue;
            String[] p=s.split(",");if(p.length!=2)throw new IOException("Expected voltage,distance_m");
            double v=Double.parseDouble(p[0]),d=Double.parseDouble(p[1]);
            if(!Double.isFinite(v)||!Double.isFinite(d)||v<=0||v>=3.3||d<0)throw new IOException("Invalid calibration point");
            rows.add(new double[]{v,d});
        }
        if(rows.size()<4)throw new IOException("At least four measured calibration points required");
        rows.sort(Comparator.comparingDouble(a->a[0]));points=rows.toArray(double[][]::new);
        double sign=Math.signum(points[1][1]-points[0][1]);
        for(int i=1;i<points.length;i++)if(points[i][0]<=points[i-1][0]||Math.signum(points[i][1]-points[i-1][1])!=sign||sign==0)throw new IOException("Calibration must be strictly monotonic");
    }
    public double meters(double v)throws IOException {
        if(v<points[0][0]||v>points[points.length-1][0])throw new IOException("Range voltage outside validated sensor range: "+v);
        for(int i=1;i<points.length;i++)if(v<=points[i][0]) {
            double[] a=points[i-1],b=points[i];return a[1]+(v-a[0])/(b[0]-a[0])*(b[1]-a[1]);
        }
        throw new IOException("Range interpolation failed");
    }
}
