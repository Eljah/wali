package com.ubor.core;

import java.io.*;
import java.nio.*;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static com.ubor.core.Types.*;

/** Fixed-length HMAC packets on TCP. Unique server challenge binds each session against cross-session replay.
 *  Integrity/authentication only: HMAC does not encrypt video or telemetry. Use a private network/VPN.
 */
public final class ControlProtocol {
    public static final int CHALLENGE_SIZE=32, BODY_SIZE=34, TAG_SIZE=32, PACKET_SIZE=BODY_SIZE+TAG_SIZE;
    private static final int MAGIC=0x55424F52;
    private ControlProtocol() {}
    public static byte[] challenge() { byte[] n=new byte[CHALLENGE_SIZE];new SecureRandom().nextBytes(n);return n; }
    public static byte[] keyFromEnvironment() {
        String k=System.getenv("UBOR_KEY");
        if(k==null || k.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32)
            throw new IllegalArgumentException("Set UBOR_KEY to a secret of at least 32 UTF-8 bytes");
        return k.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    public static byte[] encode(Command c,byte[] nonce,byte[] key) throws GeneralSecurityException {
        int flags=(c.deadman()?1:0)|(c.arm()?2:0)|(c.auto()?4:0)|(c.collect()?8:0)|(c.reset()?16:0)|(c.stop()?32:0);
        ByteBuffer b=ByteBuffer.allocate(PACKET_SIZE).order(ByteOrder.BIG_ENDIAN);
        b.putInt(MAGIC).put((byte)1).putLong(c.sequence()).putInt(flags).put((byte)c.label().ordinal()).putDouble(c.linear()).putDouble(c.angular());
        byte[] body=java.util.Arrays.copyOf(b.array(),BODY_SIZE);
        b.put(tag(body,nonce,key));return b.array();
    }
    public static Command decode(byte[] p,byte[] nonce,byte[] key,long lastSequence,long receivedNanos) throws GeneralSecurityException {
        if(p.length!=PACKET_SIZE) throw new SecurityException("Packet length");
        byte[] body=java.util.Arrays.copyOf(p,BODY_SIZE),supplied=java.util.Arrays.copyOfRange(p,BODY_SIZE,PACKET_SIZE);
        if(!MessageDigest.isEqual(tag(body,nonce,key),supplied)) throw new SecurityException("HMAC");
        ByteBuffer b=ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN);
        if(b.getInt()!=MAGIC || b.get()!=1) throw new SecurityException("Protocol version");
        long seq=b.getLong();int f=b.getInt(),label=b.get()&255;
        if(seq<=lastSequence || (f&~63)!=0 || label>=Label.values().length) throw new SecurityException("Replay or flags");
        return new Command(seq,receivedNanos,(f&1)!=0,(f&2)!=0,(f&4)!=0,(f&8)!=0,(f&16)!=0,(f&32)!=0,
                b.getDouble(),b.getDouble(),Label.values()[label]);
    }
    private static byte[] tag(byte[] body,byte[] nonce,byte[] key) throws GeneralSecurityException {
        if(key.length<32 || nonce.length!=32) throw new IllegalArgumentException("Secret / challenge length");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));mac.update(nonce);return mac.doFinal(body);
    }
    public static byte[] readExactly(InputStream in,int count) throws IOException {
        if(count<0||count>1_000_000) throw new IOException("Bound exceeded");
        byte[] b=in.readNBytes(count);if(b.length!=count) throw new EOFException("Short frame");return b;
    }
}
