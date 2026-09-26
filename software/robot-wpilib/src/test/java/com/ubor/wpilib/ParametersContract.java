package com.ubor.wpilib;
import java.nio.file.*;
import java.util.*;
public final class ParametersContract {
    private static final List<String> passed=new ArrayList<>();
    private static void check(String s,boolean ok){if(!ok)throw new AssertionError(s);passed.add(s);System.out.println("PASS "+s);}
    public static void main(String[] args)throws Exception{
        Path config=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        WpiParameters d=WpiParameters.defaults();
        check("mass-assumption",d.massKg()==30);
        check("battery-not-FRC-default",d.batteryOpenVolts()==25.6);
        check("wheel-and-track-contract",d.wheelRadius()==.1&&d.trackWidth()==.5);
        check("configuration-matches-defaults",WpiParameters.load(config).equals(d));
        for(String invalid:List.of("mass.kg=-1","drive.free.rpm=NaN","coast.drag.fraction=1.1","track.width.m=0.8","unknown.parameter=123")){
            Path f=out.resolve("invalid.properties");Files.writeString(f,invalid);boolean rejected=false;
            try{WpiParameters.load(f);}catch(IllegalArgumentException e){rejected=true;}
            check("reject-"+invalid,rejected);Files.delete(f);
        }
        Files.write(out.resolve("passed.txt"),passed);
        Files.writeString(out.resolve("result.json"),"{\"status\":\"PASS\",\"assertions\":"+passed.size()+",\"uses_wpilib\":false}\n");
    }
}
