package com.ubor.app;

import com.ubor.core.*;
import static com.ubor.core.Types.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Single operator lease. A connection without valid packets times out instead of occupying control indefinitely. */
public final class ControlServer implements AutoCloseable {
    private final RobotService robot;private final byte[] key;private final ServerSocket server;
    private volatile boolean running=true;private volatile Socket active;
    public ControlServer(RobotService robot,byte[] key,InetAddress bind,int port) throws IOException {
        this.robot=robot;this.key=key.clone();server=new ServerSocket(port,1,bind);
    }
    public int port() { return server.getLocalPort(); }
    public void start() { Thread thread=new Thread(this::run,"operator-TCP");thread.setDaemon(true);thread.start(); }
    private void run() {
        while(running) {
            try(Socket socket=server.accept()) {
                active=socket;socket.setTcpNoDelay(true);socket.setSoTimeout(180);
                InputStream in=socket.getInputStream();DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                byte[] nonce=ControlProtocol.challenge();out.write(nonce);out.flush();long lastSequence=-1;
                while(running) {
                    byte[] packet=ControlProtocol.readExactly(in,ControlProtocol.PACKET_SIZE);
                    Command c=ControlProtocol.decode(packet,nonce,key,lastSequence,System.nanoTime());lastSequence=c.sequence();robot.accept(c);
                    byte[] status=robot.telemetry().getBytes(StandardCharsets.UTF_8);out.writeInt(status.length);out.write(status);
                    // Send a bounded preview at 5 Hz; no camera read happens on this thread.
                    byte[] jpeg=new byte[0];Frame f=robot.frame();
                    if(c.sequence()%4==0 && f!=null) { ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(f.image(),"JPEG",bytes);jpeg=bytes.toByteArray();if(jpeg.length>500_000) jpeg=new byte[0]; }
                    out.writeInt(jpeg.length);out.write(jpeg);out.flush();
                }
            } catch(Exception ex) { if(running) System.err.println("Control session ended: "+ex.getClass().getSimpleName()); }
            finally { active=null;robot.disconnect(); }
        }
    }
    @Override public void close() throws IOException { running=false;if(active!=null) active.close();server.close();robot.disconnect(); }
}
