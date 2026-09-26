package com.ubor.ml;
import com.ubor.core.Types.Label;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** The recorder deliberately emits UNASSIGNED splits. A human splits complete sessions first. */
public final class Manifest {
    public record Row(Path image,String split,Label label,double v,double w,String session){}
    public static List<Row> read(Path manifest)throws IOException {
        List<Row> rows=new ArrayList<>();Map<String,String> sessionSplit=new HashMap<>();Map<Path,String> images=new HashMap<>();
        List<String> lines=Files.readAllLines(manifest);
        if(lines.isEmpty()||!lines.get(0).startsWith("image,split,label,v,w,session"))throw new IOException("Bad manifest header");
        Path root=manifest.toAbsolutePath().getParent().normalize();
        for(int i=1;i<lines.size();i++) {
            if(lines.get(i).isBlank())continue;String[] p=lines.get(i).split(",",-1);if(p.length<6)throw new IOException("Bad row "+i);
            if(!Set.of("train","val","test").contains(p[1]))throw new IOException("Assign complete sessions to train/val/test before training");
            Path image=root.resolve(p[0]).normalize();if(!image.startsWith(root)||!Files.isRegularFile(image))throw new IOException("Bad image path at row "+i);
            if(images.put(image,p[1])!=null)throw new IOException("Duplicate image "+image);
            String previous=sessionSplit.putIfAbsent(p[5],p[1]);if(previous!=null&&!previous.equals(p[1]))throw new IOException("SESSION LEAKAGE "+p[5]);
            double v=Double.parseDouble(p[3]),w=Double.parseDouble(p[4]);if(!Double.isFinite(v)||!Double.isFinite(w)||Math.abs(v)>.3||Math.abs(w)>1)throw new IOException("Policy labels out of bounds");
            rows.add(new Row(image,p[1],Label.valueOf(p[2]),v,w,p[5]));
        }
        for(String s:List.of("train","val","test"))if(rows.stream().noneMatch(r->r.split.equals(s)))throw new IOException("Empty split "+s);
        return rows;
    }
}
