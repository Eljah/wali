package com.ubor.app;

import com.ubor.core.Types.Label;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Dependency-free dataset preparation. Annotations are explicit human bounding boxes, not model guesses. */
public final class DatasetTool {
    public static void main(String[] args)throws Exception {
        if(args.length<4)throw new IllegalArgumentException("split manifest.csv sessions.csv output.csv OR crop source-root boxes.csv output-dir");
        switch(args[0]) {
            case "split" -> split(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]));
            case "crop" -> crop(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]));
            default -> throw new IllegalArgumentException("split or crop");
        }
    }
    private static void split(Path manifest,Path sessionMap,Path output)throws IOException {
        Map<String,String> map=new HashMap<>();for(String s:Files.readAllLines(sessionMap)) {
            if(s.startsWith("session,")||s.startsWith("#")||s.isBlank())continue;String[] p=s.split(",");if(p.length!=2||!Set.of("train","val","test").contains(p[1])||map.put(p[0],p[1])!=null)throw new IOException("Invalid/duplicate session mapping");
        }
        List<String> out=new ArrayList<>();for(String s:Files.readAllLines(manifest)) {
            if(s.startsWith("image,")){out.add(s);continue;}if(s.isBlank()||s.startsWith("#"))continue;String[] p=s.split(",",-1);if(p.length<6||!map.containsKey(p[5]))throw new IOException("Missing session split");p[1]=map.get(p[5]);
            Path source=manifest.toAbsolutePath().getParent().resolve(p[0]);p[0]=output.toAbsolutePath().getParent().relativize(source).toString().replace('\\','/');
            out.add(String.join(",",p));
        }
        // Keep the destination beside its images; the ML manifest rejects paths escaping its root.
        Files.write(output,out,StandardOpenOption.CREATE_NEW);
    }
    private static void crop(Path sourceRoot,Path annotations,Path output)throws IOException {
        Path root=sourceRoot.toAbsolutePath().normalize();Files.createDirectories(output.resolve("images"));int i=0;
        try(PrintWriter manifest=new PrintWriter(Files.newBufferedWriter(output.resolve("manifest.csv"),StandardOpenOption.CREATE_NEW))) {
            manifest.println("image,split,label,v,w,session");
            for(String line:Files.readAllLines(annotations)) {
                if(line.startsWith("image,")||line.startsWith("#")||line.isBlank())continue;String[] p=line.split(",",-1);
                if(p.length!=8)throw new IOException("Expected image,x,y,width,height,label,session,split");
                Path source=root.resolve(p[0]).normalize();if(!source.startsWith(root))throw new IOException("Path traversal");
                var image=ImageIO.read(source.toFile());if(image==null)throw new IOException("Bad image");
                int x=Integer.parseInt(p[1]),y=Integer.parseInt(p[2]),w=Integer.parseInt(p[3]),h=Integer.parseInt(p[4]);Label label=Label.valueOf(p[5]);
                if(x<0||y<0||w<8||h<8||x+w>image.getWidth()||y+h>image.getHeight())throw new IOException("Crop outside image");
                if(!p[6].matches("[A-Za-z0-9_-]+")||!Set.of("train","val","test","unassigned").contains(p[7]))throw new IOException("Invalid session/split");
                String name=String.format("images/crop_%06d.png",i++);ImageIO.write(image.getSubimage(x,y,w,h),"PNG",output.resolve(name).toFile());manifest.printf("%s,%s,%s,0,0,%s%n",name,p[7],label,p[6]);
            }
        }
        System.out.println("Crops written="+i+". Use this manifest for CLASSIFIER, not POLICY training.");
    }
}
