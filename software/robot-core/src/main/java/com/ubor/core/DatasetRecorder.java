package com.ubor.core;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.ubor.core.Types.*;

/** Call only from the camera/recording worker, never from the motor deadline thread. */
public final class DatasetRecorder implements AutoCloseable {
    private final Path root;private final String session;private final PrintWriter manifest;
    private int index;
    public DatasetRecorder(Path root,String session) throws IOException {
        if(!session.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Session name");
        this.root=root;this.session=session;Files.createDirectories(root.resolve("images"));
        this.manifest=new PrintWriter(Files.newBufferedWriter(root.resolve("manifest.csv"),java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE));
        manifest.println("image,split,label,v,w,session,frame_ns,command_ns,requested_v,requested_w,reason");
    }
    public synchronized boolean record(Frame f,Command c,Decision d) throws IOException {
        // Timestamp alignment is necessary, not sufficient: camera exposure latency must be measured on hardware.
        if(Math.abs(f.timeNanos()-c.receivedNanos())>100_000_000L || !c.deadman() || c.auto() || !d.enabled()) return false;
        String image=String.format(Locale.ROOT,"images/%s_%06d.png",session,index++);
        if(!ImageIO.write(f.image(),"PNG",root.resolve(image).toFile())) throw new IOException("PNG writer missing");
        manifest.printf(Locale.ROOT,"%s,unassigned,%s,%.6f,%.6f,%s,%d,%d,%.6f,%.6f,%s%n",image,c.label(),d.linear(),d.angular(),
                session,f.timeNanos(),c.receivedNanos(),c.linear(),c.angular(),d.reason());
        manifest.flush();if(manifest.checkError()) throw new IOException("Manifest write failed");return true;
    }
    @Override public synchronized void close() { manifest.close(); }
}
