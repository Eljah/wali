package com.ubor.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bus drivers are plain Java; Pi4J only implements the byte-transport boundary. */
public final class RegisterDevices {
    private RegisterDevices() {}
    @FunctionalInterface public interface SpiBus { byte[] transfer(byte[] tx) throws IOException; }
    @FunctionalInterface public interface I2cBus { void write(byte[] tx) throws IOException; }
    public static final class Mcp3008 {
        private final SpiBus bus;
        public Mcp3008(SpiBus bus) { this.bus=bus; }
        public int read(int channel) throws IOException {
            if(channel<0 || channel>7) throw new IllegalArgumentException("ADC channel");
            byte[] rx=bus.transfer(new byte[]{1,(byte)((8+channel)<<4),0});
            if(rx.length!=3) throw new IOException("Short MCP3008 transfer");
            return ((rx[1]&3)<<8)|(rx[2]&255);
        }
        public double volts(int channel) throws IOException { return read(channel)*3.3/1023; }
    }
    public static final class Mcp23s17 {
        private final SpiBus bus;
        public Mcp23s17(SpiBus bus) { this.bus=bus; }
        private void reg(int addr,int value) throws IOException { bus.transfer(new byte[]{0x40,(byte)addr,(byte)value}); }
        public void init() throws IOException {
            reg(0x0A,0x08); // BANK=0, hardware addressing enabled, A2:A0 physically strapped to 0.
            reg(0x0C,0xF0); reg(0x0D,0xFF); // Pull up unused GPIO inputs.
            reg(0x14,0);    // Output latch first, then direction, avoiding a boot pulse.
            reg(0x00,0xF0);// GPA0..3 PH signals; GPA4..7 inputs. Port B remains inputs.
        }
        public void directions(int mask) throws IOException { reg(0x14,mask&15); }
    }
    public static final class Pca9685 {
        private final I2cBus bus;
        public Pca9685(I2cBus bus) { this.bus=bus; }
        private void reg(int addr,int value) throws IOException { bus.write(new byte[]{(byte)addr,(byte)value}); }
        public void init() throws IOException {
            reg(0,0x10); // Sleep before PRE_SCALE. Nominal 25 MHz oscillator, 1017 Hz with prescale 5.
            reg(0xFE,5); reg(1,0x04); // Totem-pole, outputs low when OE high (OUTNE=00).
            reg(0,0x20); // Auto-increment, wake.
            try { Thread.sleep(2); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IOException(e); }
            reg(0,0xA0); // Restart + AI.
            allOff();
        }
        public void duty(int channel,double value) throws IOException {
            if(channel<0||channel>15||!Double.isFinite(value)||value<0||value>1) throw new IllegalArgumentException("PWM");
            int addr=6+4*channel;
            if(value==0) { bus.write(new byte[]{(byte)addr,0,0,0,0x10});return; }
            if(value==1) { bus.write(new byte[]{(byte)addr,0,0x10,0,0});return; }
            int off=(int)Math.round(value*4095);
            bus.write(new byte[]{(byte)addr,0,0,(byte)off,(byte)(off>>8)});
        }
        public void allOff() throws IOException { reg(0xFD,0x10);reg(0xFB,0); }
        // ALL_LED writes broadcast into per-channel registers; never clear them after individual duty writes.
    }
    public static final class Ls7366r {
        private final SpiBus bus;
        private boolean initialized;
        private int previous;
        private long accumulated;
        public Ls7366r(SpiBus bus) { this.bus=bus; }
        public void init() throws IOException {
            bus.transfer(new byte[]{(byte)0x88,0x03}); // MDR0: x4 quadrature, free running.
            bus.transfer(new byte[]{(byte)0x90,0x00}); // MDR1: four bytes, counting enabled.
            bus.transfer(new byte[]{0x20}); // Clear counter.
            initialized=false;accumulated=0;
        }
        public long count() throws IOException {
            byte[] rx=bus.transfer(new byte[]{0x60,0,0,0,0}); // RD CNTR, MSB first.
            if(rx.length!=5) throw new IOException("Short counter transfer");
            int raw=ByteBuffer.wrap(rx,1,4).order(ByteOrder.BIG_ENDIAN).getInt();
            if(initialized) accumulated+=(int)(raw-previous); // deliberate signed 32-bit wrap delta
            previous=raw;initialized=true;return accumulated;
        }
    }
    /** DRV8874 PH/EN control: zero every PWM before changing a PH line. */
    public static final class FourMotors {
        private final Mcp23s17 directions;
        private final Pca9685 pwm;
        private int previousMask=-1;
        public FourMotors(Mcp23s17 directions,Pca9685 pwm) { this.directions=directions;this.pwm=pwm; }
        public void set(Types.Actuation a) throws IOException {
            if(!a.enabled()) { pwm.allOff();previousMask=-1;return; }
            double[] d={a.left(),a.right(),a.brush(),a.belt()};
            int mask=0;for(int i=0;i<4;i++) if(d[i]>=0) mask|=1<<i;
            if(mask!=previousMask) {
                pwm.allOff();
                for(int i=0;i<4;i++) pwm.duty(i,0);
                directions.directions(mask);
                // Not a semiconductor deadtime guarantee. Speed reversal is separately slew-limited.
                previousMask=mask;
            }
            for(int i=0;i<4;i++) pwm.duty(i,Math.abs(d[i]));
            // Per-channel writes already replace the broadcast full-off values.
        }
    }
}
