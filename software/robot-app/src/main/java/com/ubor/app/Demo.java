package com.ubor.app;

import com.ubor.core.*;
import static com.ubor.core.Types.*;
import java.nio.file.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.util.*;

/** Fast deterministic world integration. ORACLE is not a neural-network validation. */
public final class Demo {
    public record Result(int collected,int remaining,String lastReason,double x,double y) {}
    public static Result run(Path out,double seconds) throws Exception {
        try(Simulator world=new Simulator()) { return run(out,seconds,world); }
    }
    public static Result run(Path out,double seconds,Simulator world) throws Exception {
        if(!Double.isFinite(seconds)||seconds<=0||seconds>3600)throw new IllegalArgumentException("seconds must be in (0,3600]");
        Files.createDirectories(out);SafetyController safety=new SafetyController();DriveControl drive=new DriveControl();
        long now=1_000_000_000L;Decision d=Decision.stopped(Mode.DISARMED,"BOOT");
        try(PrintWriter log=new PrintWriter(Files.newBufferedWriter(out.resolve("trajectory.csv")))) {
            log.println("t_s,x_m,y_m,heading_rad,mode,reason,v_m_s,w_rad_s,left_duty,right_duty,brush_duty,belt_duty,collected,battery_v,left_a,right_a,brush_a,belt_a,left_counts,right_counts,left_mps,right_mps");
            for(int k=0;k<Math.round(seconds/.02);k++) {
                now+=20_000_000L;world.advance(now);Sensors s=world.read(now);
                Command c=new Command(k,now,true,true,true,false,false,false,0,0,Label.UNKNOWN);
                d=safety.step(now,c,s,world.oracle(now));Actuation a=drive.update(d,s);world.apply(a,now);
                log.printf(Locale.ROOT,"%.2f,%.5f,%.5f,%.5f,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%.5f,%.5f,%.5f,%.5f,%.5f,%d,%d,%.6f,%.6f%n",k*.02,world.x,world.y,world.heading,d.mode(),d.reason(),d.linear(),d.angular(),a.left(),a.right(),a.brush(),a.belt(),world.deposited(),s.batteryV(),s.leftA(),s.rightA(),s.brushA(),s.beltA(),s.leftCounts(),s.rightCounts(),world.leftSpeed,world.rightSpeed);
                if(k%500==0)ImageIO.write(world.mapImage(1200,700),"PNG",out.resolve(String.format("map_%04d.png",k)).toFile());
            }
        }
        ImageIO.write(world.mapImage(1200,700),"PNG",out.resolve("final_map.png").toFile());
        ImageIO.write(world.camera(),"PNG",out.resolve("synthetic_camera.png").toFile());
        Result r=new Result(world.deposited(),(int)world.objects().stream().filter(o->o.stage!=2).count(),d.reason(),world.x,world.y);
        Files.writeString(out.resolve("result.json"),String.format(Locale.ROOT,"{\n  \"mode\":\"SIM_ORACLE_NOT_ML\",\n  \"simulated_seconds\":%.1f,\n  \"collected\":%d,\n  \"remaining\":%d,\n  \"last_reason\":\"%s\",\n  \"physical_hardware\":false,\n  \"electrical_circuit_simulation\":false\n}\n",seconds,r.collected,r.remaining,r.lastReason));
        Files.writeString(out.resolve("physics-backend.txt"),world.dynamicsName()+"\n");
        return r;
    }
}
