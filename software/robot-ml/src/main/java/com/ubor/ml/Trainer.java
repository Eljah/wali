package com.ubor.ml;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.security.MessageDigest;

/** Java-only offline training. Never runs in the control worker or on an armed robot. */
public final class Trainer {
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("Trainer classifier|policy manifest.csv output.zip epochs");
        boolean policy=switch(args[0]){case "classifier"->false;case "policy"->true;default->throw new IllegalArgumentException("Model type");};
        Path file=Path.of(args[1]),output=Path.of(args[2]);int epochs=Integer.parseInt(args[3]);if(epochs<1||epochs>500)throw new IllegalArgumentException("epochs 1..500");
        List<Manifest.Row> rows=Manifest.read(file),train=new ArrayList<>(rows.stream().filter(r->r.split().equals("train")).toList());
        if(!policy)for(com.ubor.core.Types.Label l:com.ubor.core.Types.Label.values())if(train.stream().noneMatch(r->r.label()==l))throw new IllegalArgumentException("Missing training class "+l);
        Path parent=output.toAbsolutePath().getParent();Files.createDirectories(parent);MultiLayerNetwork net=Networks.create(policy);Random random=new Random(713);double best=Double.POSITIVE_INFINITY;
        try(PrintWriter curve=new PrintWriter(Files.newBufferedWriter(Path.of(output+".learning.csv")))) {
            curve.println("epoch,validation_loss");
            for(int epoch=0;epoch<epochs;epoch++) {
                Collections.shuffle(train,random);
                for(int from=0;from<train.size();from+=8) {
                    int n=Math.min(8,train.size()-from);INDArray[] x=new INDArray[n],y=new INDArray[n];
                    for(int j=0;j<n;j++){Manifest.Row row=train.get(from+j);var image=ImageIO.read(row.image().toFile());if(image==null)throw new IOException("Unreadable image "+row.image());x[j]=Images.tensor(image);y[j]=target(row,policy);}
                    net.fit(new DataSet(Nd4j.concat(0,x),Nd4j.concat(0,y)));
                }
                double val=loss(net,rows,"val",policy);curve.printf(Locale.ROOT,"%d,%.8f%n",epoch+1,val);curve.flush();
                if(val<best){best=val;ModelSerializer.writeModel(net,output.toFile(),true);}
                System.out.printf(Locale.ROOT,"epoch=%d validation=%.6f%n",epoch+1,val);
            }
        }
        MultiLayerNetwork chosen=ModelSerializer.restoreMultiLayerNetwork(output.toFile());
        Properties metadata=new Properties();metadata.setProperty("format","UBOR-RGB64-NCHW-01");metadata.setProperty("kind",args[0]);metadata.setProperty("labels","NONE,PAPER,PLASTIC_BOTTLE,METAL_CAN,HAZARD,UNKNOWN");
        metadata.setProperty("approved.for.hardware","false");metadata.setProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(output))));
        metadata.setProperty("validation.loss",Double.toString(best));metadata.setProperty("test.loss",Double.toString(loss(chosen,rows,"test",policy)));
        try(OutputStream o=Files.newOutputStream(Path.of(output+".properties"))){metadata.store(o,"Review held-out metrics and target latency; do NOT auto-approve hardware deployment");}
        evaluate(chosen,rows,policy,Path.of(output+".test.txt"));
    }
    private static INDArray target(Manifest.Row row,boolean policy) {
        if(policy)return Nd4j.create(new float[]{(float)(row.v()/.3),(float)row.w()},new long[]{1,2});
        INDArray y=Nd4j.zeros(1,6);y.putScalar(0,row.label().ordinal(),1);return y;
    }
    private static double loss(MultiLayerNetwork n,List<Manifest.Row> rows,String split,boolean policy)throws IOException {
        double sum=0;int count=0;for(var r:rows)if(r.split().equals(split)){sum+=n.score(new DataSet(Images.tensor(ImageIO.read(r.image().toFile())),target(r,policy)));count++;}return sum/count;
    }
    private static void evaluate(MultiLayerNetwork n,List<Manifest.Row> rows,boolean policy,Path report)throws IOException {
        int[][] matrix=new int[6][6];double v=0,w=0;int count=0;
        for(var r:rows)if(r.split().equals("test")){INDArray y=n.output(Images.tensor(ImageIO.read(r.image().toFile())),false);if(policy){v+=Math.abs(y.getDouble(0,0)*.3-r.v());w+=Math.abs(y.getDouble(0,1)-r.w());}else matrix[r.label().ordinal()][y.argMax(1).getInt(0)]++;count++;}
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(report))) {
            out.println("Held-out session TEST split; sample count="+count);
            if(policy){out.println("MAE_v_m_per_s="+v/count);out.println("MAE_w_rad_per_s="+w/count);}
            else {double macro=0;int correct=0;out.println("rows=true, columns=predicted: NONE,PAPER,PLASTIC_BOTTLE,METAL_CAN,HAZARD,UNKNOWN");
                for(int i=0;i<6;i++){out.println(Arrays.toString(matrix[i]));int actual=0,pred=0;for(int j=0;j<6;j++){actual+=matrix[i][j];pred+=matrix[j][i];}int tp=matrix[i][i];correct+=tp;double f1=actual+pred==0?0:2.0*tp/(actual+pred);macro+=f1;out.println("class="+i+" recall="+(actual==0?"UNDEFINED":Double.toString((double)tp/actual))+" F1="+f1);}
                out.println("accuracy="+(double)correct/count);out.println("macro_F1_including_absent_classes_as_0="+macro/6);
            }
            out.println("Classification metrics do not measure object localization or physical pickup success.");
        }
    }
}
