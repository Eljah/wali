package com.ubor.ml;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

/** Identical preprocessing during training and runtime: RGB NCHW, 64x64, [0,1]. */
public final class Images {
    public static final int SIZE=64;
    private Images(){}
    public static INDArray tensor(BufferedImage source) {
        BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(source,0,0,SIZE,SIZE,null);g.dispose();
        float[] data=new float[3*SIZE*SIZE];
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++) {int rgb=image.getRGB(x,y),i=y*SIZE+x;data[i]=((rgb>>16)&255)/255f;data[SIZE*SIZE+i]=((rgb>>8)&255)/255f;data[2*SIZE*SIZE+i]=(rgb&255)/255f;}
        return Nd4j.create(data,new long[]{1,3,SIZE,SIZE});
    }
}
