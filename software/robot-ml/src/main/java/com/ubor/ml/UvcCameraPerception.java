package com.ubor.ml;
import com.ubor.core.Types.*;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/** Camera-only commissioning path: manual driving and data recording BEFORE any models exist. */
public class UvcCameraPerception implements Perception {
    private final OpenCVFrameGrabber grabber;
    private final Java2DFrameConverter converter=new Java2DFrameConverter();
    public UvcCameraPerception(Path config)throws Exception {
        Properties p=new Properties();try(InputStream in=Files.newInputStream(config)){p.load(in);}
        grabber=new OpenCVFrameGrabber(Integer.parseInt(p.getProperty("camera.index","0")));grabber.setImageWidth(640);grabber.setImageHeight(360);grabber.setFrameRate(15);grabber.start();
    }
    @Override public Frame capture()throws Exception {
        long began=System.nanoTime();var raw=grabber.grab();BufferedImage source=converter.convert(raw);if(source==null)throw new IOException("No UVC image");
        BufferedImage copy=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=copy.createGraphics();g.drawImage(source,0,0,null);g.dispose();
        return new Frame(began,copy); // Conservative timestamp; actual exposure/driver queue latency still needs measuring.
    }
    @Override public Vision infer(Frame frame){return Vision.unavailable(frame.timeNanos());}
    @Override public void close()throws Exception {try{grabber.stop();grabber.release();}finally{converter.close();}}
}
