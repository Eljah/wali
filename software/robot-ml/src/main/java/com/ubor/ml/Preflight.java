package com.ubor.ml;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.api.ndarray.INDArray;
public final class Preflight {
    public static void main(String[] args) {
        System.out.println("Java="+System.getProperty("java.version")+" OS="+System.getProperty("os.name")+" ARCH="+System.getProperty("os.arch"));
        System.out.println("ND4J backend="+Nd4j.getBackend().getClass().getName());
        var net=Networks.create(false);INDArray input=Nd4j.zeros(1,3,64,64);
        for(int i=0;i<10;i++)net.output(input,false);
        long[] times=new long[100];for(int i=0;i<times.length;i++){long t=System.nanoTime();var result=net.output(input,false);if(result.length()!=6)throw new AssertionError();times[i]=System.nanoTime()-t;}
        java.util.Arrays.sort(times);System.out.println("single_crop_ms_p50="+times[49]/1e6+" p99="+times[98]/1e6);
        System.out.println("This is NOT the end-to-end camera/sliding-window latency benchmark. Multiply passes and measure the full pipeline separately.");
    }
}
