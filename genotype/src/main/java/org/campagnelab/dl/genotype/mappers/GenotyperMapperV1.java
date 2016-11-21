package org.campagnelab.dl.genotype.mappers;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.campagnelab.dl.framework.mappers.ConfigurableFeatureMapper;
import org.campagnelab.dl.framework.mappers.FeatureNameMapper;
import org.campagnelab.dl.somatic.mappers.*;
import org.campagnelab.dl.somatic.mappers.functional.TraversalHelper;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.List;
import java.util.Properties;

/**
 * This m
 */
public class GenotyperMapperV1 extends NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder> {
    private NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder> delegate;
    private int sampleIndex;

    /**
     * Configure the feature mapper to map a specific sample
     */
    public void configure(Properties sbiProperties) {

        FeatureNameMapper[] countMappers = new FeatureNameMapper[20];
        FeatureNameMapper[] readIndexMappers = new FeatureNameMapper[20];
        for (int i = 0; i < 10; i++){
            countMappers[i] = (new SingleGenoTypeCountMapper(sampleIndex,i,true));
            readIndexMappers[i] = (new SingleReadIndexCountMapper(sampleIndex,i,true));
        }
        for (int i = 10; i < 20; i++){
            countMappers[i] = (new SingleGenoTypeCountMapper(sampleIndex,i,false));
            readIndexMappers[i] = (new SingleReadIndexCountMapper(sampleIndex,i,false));
        }
        delegate =
                new NamingConcatFeatureMapper<>(
                        new AbsoluteNormalizeMapper(
                                new NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>(countMappers)),
                        new InverseNormalizeMapper(
                                new NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>(countMappers)),
                        new AbsoluteNormalizeMapper(
                                new NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>(readIndexMappers)),
                        new InverseNormalizeMapper(
                                new NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>(readIndexMappers)),
                        new GenomicContextMapper(sbiProperties),
                        new DensityMapper("numVariationsInRead", 20, sbiProperties, baseInformationOrBuilder ->
                                TraversalHelper.forSampleCounts(sampleIndex, baseInformationOrBuilder, BaseInformationRecords.CountInfo::getNumVariationsInReadsList)),
                        new DensityMapper("readMappingQuality.forward", 10, sbiProperties, baseInformationOrBuilder ->
                                TraversalHelper.forSampleCounts(sampleIndex, baseInformationOrBuilder, BaseInformationRecords.CountInfo::getReadMappingQualityForwardStrandList)),
                        new DensityMapper("readMappingQuality.reverse", 10, sbiProperties, baseInformationOrBuilder ->
                                TraversalHelper.forSampleCounts(sampleIndex, baseInformationOrBuilder, BaseInformationRecords.CountInfo::getReadMappingQualityReverseStrandList)),
                        new DensityMapper("baseQuality.forward", 10, sbiProperties, baseInformationOrBuilder ->
                                TraversalHelper.forSampleCounts(sampleIndex, baseInformationOrBuilder, BaseInformationRecords.CountInfo::getQualityScoresForwardStrandList)),
                        new DensityMapper("baseQuality.reverse", 10, sbiProperties, baseInformationOrBuilder ->
                                TraversalHelper.forSampleCounts(sampleIndex, baseInformationOrBuilder, BaseInformationRecords.CountInfo::getQualityScoresReverseStrandList))
                )
        ;

    }

    public GenotyperMapperV1(int sampleIndex) {
        this.sampleIndex = sampleIndex;
    }

    @Override
    public String getFeatureName(int i) {
        return delegate.getFeatureName(i);
    }

    @Override
    public int numberOfFeatures() {
        return delegate.numberOfFeatures();
    }

    @Override
    public void prepareToNormalize(BaseInformationRecords.BaseInformationOrBuilder record, int indexOfRecord) {
        delegate.prepareToNormalize(record, indexOfRecord);
    }

    @Override
    public void mapFeatures(BaseInformationRecords.BaseInformationOrBuilder record, INDArray inputs, int indexOfRecord) {
        delegate.mapFeatures(record, inputs, indexOfRecord);
    }

    @Override
    public float produceFeature(BaseInformationRecords.BaseInformationOrBuilder record, int featureIndex) {
        return delegate.produceFeature(record, featureIndex);
    }

}
