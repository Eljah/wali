package com.ubor.app;

import com.ubor.core.*;
import static com.ubor.core.Types.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public final class ControlClient implements AutoCloseable {
    private final String host;private final int port;private final byte[] key;private final Supplier<Command> command;
    private volatile boolean running=true;private volatile Socket socket;
    public volatile String status="DISCONNECTED";public volatile BufferedImage preview;
    public ControlClient(String host,int port,byte[] key,Supplier<Command> command) { this.host=host;this.port=port;this.key=key.clone();this.command=command; }
    public void start() { Thread t=new Thread(this::run,"controller-network");t.setDaemon(true);t.start(); }
    private void run() {
        // No automatic reconnect / rearm. A fresh connection is an explicit operator action.
        try(Socket s=new Socket()) {
            socket=s;s.connect(new InetSocketAddress(host,port),1500);s.setTcpNoDelay(true);s.setSoTimeout(400);
            InputStream raw=s.getInputStream();DataInputStream in=new DataInputStream(raw);OutputStream out=s.getOutputStream();
            byte[] nonce=ControlProtocol.readExactly(raw,32);long seq=0;
            while(running) {
                Command c=command.get();
                c=new Command(seq++,System.nanoTime(),c.deadman(),c.arm(),c.auto(),c.collect(),c.reset(),c.stop(),c.linear(),c.angular(),c.label());
                out.write(ControlProtocol.encode(c,nonce,key));out.flush();
                int n=in.readInt();if(n<0||n>4096) throw new IOException("Telemetry bound");
                status=new String(ControlProtocol.readExactly(raw,n),StandardCharsets.UTF_8);
                int length=in.readInt();if(length<0||length>500_000) throw new IOException("Preview bound");
                if(length>0) preview=ImageIO.read(new ByteArrayInputStream(ControlProtocol.readExactly(raw,length)));
                Thread.sleep(50);
            }
        } catch(Exception e) { status="DISCONNECTED: "+e.getClass().getSimpleName(); }
        finally { running=false; }
    }
    @Override public void close() throws IOException { running=false;if(socket!=null) socket.close(); }
}
