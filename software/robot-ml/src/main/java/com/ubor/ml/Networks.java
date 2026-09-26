package com.ubor.ml;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

public final class Networks {
    private Networks(){}
    public static MultiLayerNetwork create(boolean policy) {
        MultiLayerConfiguration cfg=new NeuralNetConfiguration.Builder().seed(713).weightInit(WeightInit.XAVIER).updater(new Adam(.001)).l2(.0001).list()
            .layer(new ConvolutionLayer.Builder(5,5).stride(2,2).nIn(3).nOut(16).activation(Activation.RELU).build())
            .layer(new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX).kernelSize(2,2).stride(2,2).build())
            .layer(new ConvolutionLayer.Builder(3,3).nOut(32).activation(Activation.RELU).build())
            .layer(new GlobalPoolingLayer.Builder().poolingType(PoolingType.AVG).build())
            .layer(new DenseLayer.Builder().nOut(64).activation(Activation.RELU).build())
            .layer(new OutputLayer.Builder(policy?LossFunctions.LossFunction.MSE:LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                    .nOut(policy?2:6).activation(policy?Activation.TANH:Activation.SOFTMAX).build())
            .setInputType(InputType.convolutional(64,64,3)).build();
        MultiLayerNetwork model=new MultiLayerNetwork(cfg);model.init();return model;
    }
}
