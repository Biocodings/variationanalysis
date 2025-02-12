package org.campagnelab.dl.somatic.mappers;

import org.campagnelab.dl.framework.mappers.FeatureNameMapper;
import org.campagnelab.dl.framework.mappers.NoMaskFeatureMapper;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

/**
 * Produces feature that represent a density of values for a given number of bins..
 * Created by fac2003 on 10/21/16.
 */
public class DensityMapper extends NoMaskFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>
        implements FeatureNameMapper<BaseInformationRecords.BaseInformationOrBuilder> {

    protected Function<BaseInformationRecords.BaseInformationOrBuilder, List<BaseInformationRecords.NumberWithFrequency>> recordToValues;
    protected int minValue=Integer.MAX_VALUE;
    protected int maxValue=Integer.MIN_VALUE;
    protected float binWidth;
    protected String name;
    protected Function<Integer, Float> valueFunction;
    int numBins = 10;
    float[] bins;
    protected int[] indices;

    public DensityMapper(String name, int numBins, Properties sbiProperties,
                         Function<BaseInformationRecords.BaseInformationOrBuilder, List<BaseInformationRecords.NumberWithFrequency>> recordToValues
    ) {
        this(name, numBins, sbiProperties, recordToValues, Integer::floatValue);
    }

    public DensityMapper(String name1, String name2, int numBins, Properties sbiProperties,
                         Function<BaseInformationRecords.BaseInformationOrBuilder, List<BaseInformationRecords.NumberWithFrequency>> recordToValues
    ) {
        this(name1, name2, numBins, sbiProperties, recordToValues, Integer::floatValue);
    }


    /**
     * @param name
     * @param numBins number of bins to represent this density with. If -1, use a bin for every increment between minValue and maxValue.
     * @param sbiProperties
     * @param recordToValues
     * @param valueFunction
     */
    public DensityMapper(String name, int numBins, Properties sbiProperties,
                         Function<BaseInformationRecords.BaseInformationOrBuilder, List<BaseInformationRecords.NumberWithFrequency>> recordToValues,
                         Function<Integer, Float> valueFunction) {
        if (!propertiesPresent(sbiProperties, "stats." + name)) {
            throw new UnsupportedOperationException("The sbip file does not contain the statistics for " + name + " (stats." + name + ".min and stats." + name + ".max)");
        }
        this.minValue = (int)getMin(sbiProperties, "stats." + name);
        this.maxValue = (int)getMax(sbiProperties, "stats." + name);
        constructorHelper(name,numBins,recordToValues,valueFunction);
    }


    //handle case where there are two protobuf fields contributing to one map.
    public DensityMapper(String name1, String name2, int numBins, Properties sbiProperties,
                         Function<BaseInformationRecords.BaseInformationOrBuilder, List<BaseInformationRecords.NumberWithFrequency>> recordToValues,
                         Function<Integer, Float> valueFunction
    ) {

        if (!propertiesPresent(sbiProperties, "stats." + name1)) {
            throw new UnsupportedOperationException("The sbip file does not contain the statistics for " + name1 + " (stats." + name1 + ".min and stats." + name1 + ".max)");
        }
        if (!propertiesPresent(sbiProperties, "stats." + name2)) {
            throw new UnsupportedOperationException("The sbip file does not contain the statistics for " + name2 + " (stats." + name2 + ".min and stats." + name2 + ".max)");
        }
        //extend range to handle two fields
        this.minValue = Math.min((int)getMin(sbiProperties, "stats." + name1),(int)getMin(sbiProperties, "stats." + name2));
        this.maxValue = Math.max((int)getMax(sbiProperties, "stats." + name1),(int)getMax(sbiProperties, "stats." + name2));
        StringBuffer common = new StringBuffer(name1);
        int i = 0;
        for (i = 0; i < Math.min(name1.length(),name2.length()); i++){
            if (!(name1.charAt(i) == name2.charAt(i))){
                break;
            }
        }
        common.append("+" + name2.substring(i));
        constructorHelper(common.toString(),numBins,recordToValues,valueFunction);
    }


    protected void constructorHelper(String name, int numBins,
                                   Function<BaseInformationRecords.BaseInformationOrBuilder, List<BaseInformationRecords.NumberWithFrequency>> recordToValues,
                                   Function<Integer, Float> valueFunction){
        this.name = name;
        this.valueFunction = valueFunction;
        if (numBins == -1){
            this.numBins = (this.maxValue - this.minValue);
        } else {
            this.numBins = numBins;
        }
        bins = new float[this.numBins];
        this.recordToValues = recordToValues;
        this.binWidth = (valueFunction.apply(maxValue) - valueFunction.apply(minValue) )/ this.numBins;
    }



    @Override
    public int numberOfFeatures() {
        return numBins;
    }

    @Override
    public void prepareToNormalize(BaseInformationRecords.BaseInformationOrBuilder record, int indexOfRecord) {
        Arrays.fill(bins, 0);
        List<BaseInformationRecords.NumberWithFrequency> listOfValues = recordToValues.apply(record);
        float numElements = 0;
        for (BaseInformationRecords.NumberWithFrequency n : listOfValues) {
            int featureIndex = (int) ((valueFunction.apply(n.getNumber()) - valueFunction.apply(minValue)) / binWidth);
            if (featureIndex < 0 || featureIndex >= numBins) {
                //ignore points outside of min-max
            } else {
                bins[featureIndex] += n.getFrequency();
                numElements += n.getFrequency();
            }
        }
        // normalize the counts to produce a density:
        if (numElements > 0) {
            for (int featureIndex = 0; featureIndex < numBins; featureIndex++) {
                bins[featureIndex] /= numElements;
            }
        }
    }

    @Override
    public void mapFeatures(BaseInformationRecords.BaseInformationOrBuilder record, INDArray inputs, int indexOfRecord) {
        mapFeatures(record, bins, 0, indexOfRecord);
        for (int featureIndex = 0; featureIndex < numberOfFeatures(); featureIndex++) {
            indices[1] = featureIndex;
            inputs.putScalar(indices, bins[featureIndex]);
        }
    }

    @Override
    public float produceFeature(BaseInformationRecords.BaseInformationOrBuilder record, int featureIndex) {
        return bins[featureIndex];
    }

    public void mapFeatures(BaseInformationRecords.BaseInformationOrBuilder record, float[] inputs, int offset, int indexOfRecord) {
        // do not copy if inputs is bins (call from mapFeatures above)
        if (inputs != bins) {
            System.arraycopy(bins, 0, inputs, offset, numBins);
        }
    }

    @Override
    public String getFeatureName(int featureIndex) {
        float binMin = 0;
        float binMax = 0;
        for (int i = 0; i < numBins; i++) {
            if (i < featureIndex) {
                binMin += binWidth;
            }
            if (i <= featureIndex) {
                binMax += binWidth;
            }
        }
        return String.format("density_%s_%s_%s", name, Float.toString(binMin),  Float.toString(binMax));
    }


    protected boolean propertiesPresent(Properties sbiProperties, String s) {
        final boolean minPresent = sbiProperties.containsKey(s + ".min");
        final boolean maxPresent = sbiProperties.containsKey(s + ".max");
        return (minPresent && maxPresent);
    }

    protected float getMin(Properties sbiProperties, String propertyName) {
        return Float.parseFloat(sbiProperties.getProperty(propertyName + ".min"));
    }

    protected float getMax(Properties sbiProperties, String propertyName) {
        return Float.parseFloat(sbiProperties.getProperty(propertyName + ".max"));
    }

}
