package com.ubor.app;

import com.ubor.core.Types.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;

/** Linux joydev /dev/input/jsN. No native JNI library and no Python process. */
public final class LinuxJoystick implements AutoCloseable {
    private final Path path;private final InputStream in;
    private final double[] axes=new double[16];private final boolean[] buttons=new boolean[32];
    private volatile boolean connected=true;private boolean armed,auto,stop;
    public LinuxJoystick(Path path) throws IOException {
        this.path=path;in=Files.newInputStream(path);
        Thread reader=new Thread(this::read,"joystick-events");reader.setDaemon(true);reader.start();
    }
    private void read() {
        try {
            while(connected) {
                byte[] b=in.readNBytes(8);if(b.length!=8)throw new EOFException();
                ByteBuffer v=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);v.getInt();short value=v.getShort();int type=v.get()&255,index=v.get()&255;
                synchronized(this) {
                    // Initial button values never arm the robot. A deliberate new button edge is required.
                    boolean init=(type&128)!=0;type&=127;
                    if(type==2&&index<axes.length) axes[index]=Math.max(-1,Math.min(1,value/32767.0));
                    if(type==1&&index<buttons.length) {
                        boolean pressed=value!=0;boolean edge=pressed&&!buttons[index];buttons[index]=pressed;
                        if(!init&&edge) {
                            if(index==0&&!buttons[4])armed=true;
                            if(index==1){armed=false;auto=false;}
                            if(index==2&&!buttons[4])auto=!auto;
                            if(index==3){stop=true;armed=false;}
                        }
                    }
                }
            }
        } catch(IOException e) { System.err.println("Joystick disconnected: "+e.getMessage()); }
        finally { synchronized(this){connected=false;armed=false;java.util.Arrays.fill(buttons,false);} }
    }
    private static double deadzone(double x) {return Math.abs(x)<.12?0:Math.copySign((Math.abs(x)-.12)/.88,x);}
    public synchronized Command command() {
        boolean alive=connected&&Files.exists(path);
        boolean reset=alive&&buttons[6]&&!buttons[4];
        if(reset){stop=false;armed=false;auto=false;}
        // Button 5 pickup deliberately uses UNKNOWN: this gamepad alone cannot authorize litter classification.
        // Use the desktop label selector for manual pickup; gamepad supports automatic pickup with validated vision.
        return new Command(0,System.nanoTime(),alive&&buttons[4],alive&&armed,auto,false,reset,stop,
                -.30*deadzone(axes[1]),-deadzone(axes[0]),Label.UNKNOWN);
    }
    public boolean connected(){return connected;}
    @Override public void close()throws IOException {connected=false;in.close();}
}
