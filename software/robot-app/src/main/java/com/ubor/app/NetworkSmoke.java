package com.ubor.app;
import com.ubor.core.*;
import static com.ubor.core.Types.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

/** Actual loopback TCP/HMAC exchange and operator-loss stop. Uses the logical plant, not real GPIO. */
public final class NetworkSmoke {
    public static void main(String[] args)throws Exception {
        try(Simulator sim=new Simulator()) { run(Path.of(args.length>0?args[0]:"network-report.txt"),sim); }
    }
    public static void run(Path out,Simulator sim)throws Exception {
        Files.createDirectories(out.toAbsolutePath().getParent());
        byte[] key=ControlProtocol.challenge();
        try(RobotService robot=new RobotService(sim,sim,null);ControlServer server=new ControlServer(robot,key,InetAddress.getLoopbackAddress(),0)) {
            robot.start();server.start();int replies=0;boolean enabled=false;String status="";
            try(Socket socket=new Socket(InetAddress.getLoopbackAddress(),server.port())) {
                socket.setSoTimeout(2000);DataInputStream in=new DataInputStream(socket.getInputStream());OutputStream send=socket.getOutputStream();byte[] nonce=ControlProtocol.readExactly(in,32);
                // Let JVM/camera threads initialize before attempting to reset any startup deadline latch.
                for(int k=0;k<40;k++) {
                    boolean reset=k>=5&&k<=8;boolean run=k>10;
                    Command c=new Command(k,System.nanoTime(),run,run,false,false,reset,false,run?.1:0,0,Label.PAPER);
                    send.write(ControlProtocol.encode(c,nonce,key));send.flush();int n=in.readInt();if(n<0||n>4096)throw new IOException("Bad status length");status=new String(ControlProtocol.readExactly(in,n),StandardCharsets.UTF_8);
                    int image=in.readInt();ControlProtocol.readExactly(in,image);enabled|=status.contains("\"enabled\":true");replies++;Thread.sleep(40);
                }
            }
            Thread.sleep(300);boolean stopped=!robot.decision().enabled();
            if(!enabled||!stopped||replies!=40)throw new AssertionError("Network exchange: enabled="+enabled+" stopped="+stopped+" "+status);
            Files.writeString(out,"backend="+sim.dynamicsName()+"\nPASS real TCP loopback: 40 authenticated command/telemetry replies\nPASS valid commands enabled simulated actuators\nPASS disconnect disabled within observation after 300 ms (not a precise latency measurement)\n"+robot.telemetry()+"\n");
        }
    }
}
