package com.ubor.ml;
import com.ubor.core.*;
import static com.ubor.core.Types.*;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.security.MessageDigest;

/** Small baseline: multiscale sliding-window classifier + separate imitation-driving CNN.
 * Not YOLO, not instance segmentation, not SLAM. Window-bottom ground projection has coarse accuracy.
 */
public final class UvcDl4jPerception implements Perception {
    private final UvcCameraPerception camera;
    private final MultiLayerNetwork classifier,policy;
    private final GroundProjector ground;
    private final double cameraToIntake;
    public UvcDl4jPerception(Path config,Path models)throws Exception {
        Properties p=new Properties();try(InputStream in=Files.newInputStream(config)){p.load(in);}
        if(!Boolean.parseBoolean(p.getProperty("camera.calibration.complete","false")))throw new IllegalStateException("Camera geometry uncalibrated");
        classifier=load(models.resolve("classifier.zip"),"classifier");policy=load(models.resolve("policy.zip"),"policy");
        ground=new GroundProjector(num(p,"camera.fx"),num(p,"camera.fy"),num(p,"camera.cx"),num(p,"camera.cy"),num(p,"camera.height.m"),Math.toRadians(num(p,"camera.pitch.deg")));
        cameraToIntake=num(p,"camera.to.intake.m");camera=new UvcCameraPerception(config);
    }
    private static double num(Properties p,String k){double x=Double.parseDouble(p.getProperty(k));if(!Double.isFinite(x))throw new IllegalArgumentException(k);return x;}
    private static MultiLayerNetwork load(Path model,String kind)throws Exception {
        Properties meta=new Properties();try(InputStream in=Files.newInputStream(Path.of(model+".properties"))){meta.load(in);}
        if(!"true".equals(meta.getProperty("approved.for.hardware"))||!kind.equals(meta.getProperty("kind"))||!"UBOR-RGB64-NCHW-01".equals(meta.getProperty("format")))throw new IllegalStateException("Unapproved/incompatible model "+model);
        if("classifier".equals(kind)&&!"NONE,PAPER,PLASTIC_BOTTLE,METAL_CAN,HAZARD,UNKNOWN".equals(meta.getProperty("labels")))throw new IllegalStateException("Class order mismatch");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(model)));if(!hash.equals(meta.getProperty("sha256")))throw new SecurityException("Model checksum mismatch");
        return ModelSerializer.restoreMultiLayerNetwork(model.toFile());
    }
    @Override public Frame capture()throws Exception{return camera.capture();}
    @Override public Vision infer(Frame f)throws Exception {
        var image=f.image();INDArray drive=policy.output(Images.tensor(image),false);
        Label best=Label.NONE;double conf=0,distance=99,bearing=0;boolean hazard=false;
        // Two object scales, lower image region. Masks must exclude visible chassis during calibration.
        for(double scale:new double[]{.20,.35}) {
            int size=(int)Math.round(image.getWidth()*scale),step=Math.max(1,size/2);
            for(int y=image.getHeight()/3;y+size<=image.getHeight();y+=step)for(int x=0;x+size<=image.getWidth();x+=step) {
                INDArray scores=classifier.output(Images.tensor(image.getSubimage(x,y,size,size)),false);int index=scores.argMax(1).getInt(0);double probability=scores.getDouble(0,index);
                if(index<0||index>=Label.values().length||!Double.isFinite(probability))throw new IOException("Invalid classifier output");Label label=Label.values()[index];
                if(label==Label.HAZARD&&probability>=.60)hazard=true;
                if(label.collectible()&&probability>conf) {
                    try{double[] pos=ground.project(x+size/2.0,y+size);double gap=pos[0]-cameraToIntake;if(gap<0||gap>3)continue;best=label;conf=probability;distance=gap;bearing=pos[3];}catch(IllegalArgumentException ignored){}
                }
            }
        }
        if(hazard){best=Label.HAZARD;conf=.99;distance=99;bearing=0;}
        return new Vision(f.timeNanos(),true,best,conf,distance,bearing,drive.getDouble(0,0)*.3,drive.getDouble(0,1));
    }
    @Override public void close()throws Exception{camera.close();}
}
