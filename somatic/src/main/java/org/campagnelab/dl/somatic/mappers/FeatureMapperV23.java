package org.campagnelab.dl.somatic.mappers;

import org.campagnelab.dl.framework.mappers.ConfigurableFeatureMapper;
import org.campagnelab.dl.somatic.mappers.functional.TraversalHelper;
import org.campagnelab.dl.somatic.mappers.trio.FeatureMapperV25Trio;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;


/**
 * Same as V20, with to information for each count, arranged as a sequence.
 */
public class FeatureMapperV23 extends NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>
        implements ConfigurableFeatureMapper {
    private NamingConcatFeatureMapper delegate;
    static private Logger LOG = LoggerFactory.getLogger(FeatureMapperV23.class);

    private String recordTo(final int contextLength, BaseInformationRecords.BaseInformationOrBuilder record, int countIndex) {
        return MappingFunctions.recordTo(contextLength, record, countIndex);
    }

    /**
     * Configure the feature mapper for a specific set of sbi files. This method accesses the properties of the reader.
     *
     * @param sbiProperties properties from an sbi reader.
     */
    public void configure(Properties sbiProperties) {


        delegate = new NamingConcatFeatureMapper<BaseInformationRecords.BaseInformationOrBuilder>(
                new SimpleFeatureCalculator(true),
                new IndelFeatures(),
                new GenomicContextMapper(sbiProperties),
                // we reuse the genomic context mapper to map the to field for the first four genotypes (by decreasing count):
                new GenomicPositionMapper(30, 250000000),
                new GenomicContextMapper(1, record -> recordTo(1, record, 0)),
                new GenomicContextMapper(1, record -> recordTo(1, record, 1)),
                new GenomicContextMapper(1, record -> recordTo(1, record, 2)),
                new GenomicContextMapper(1, record -> recordTo(1, record, 3)),
                new ReadIndexFeaturesFix(),
                new FractionDifferences4(),
                new MagnitudeFeatures2(),
                new DensityMapper("numVariationsInRead", 20, sbiProperties, baseInformationOrBuilder ->
                        TraversalHelper.forAllSampleCounts(baseInformationOrBuilder, BaseInformationRecords.CountInfo::getNumVariationsInReadsList)),
                new DensityMapper("readMappingQuality.forward", 10, sbiProperties, baseInformationOrBuilder ->
                        TraversalHelper.forAllSampleCounts(baseInformationOrBuilder, BaseInformationRecords.CountInfo::getReadMappingQualityForwardStrandList)),
                new DensityMapper("readMappingQuality.reverse", 10, sbiProperties, baseInformationOrBuilder ->
                        TraversalHelper.forAllSampleCounts(baseInformationOrBuilder, BaseInformationRecords.CountInfo::getReadMappingQualityReverseStrandList)),
                new DensityMapper("baseQuality.forward", 10, sbiProperties, baseInformationOrBuilder ->
                        TraversalHelper.forAllSampleCounts(baseInformationOrBuilder, BaseInformationRecords.CountInfo::getQualityScoresForwardStrandList)),
                new DensityMapper("baseQuality.reverse", 10, sbiProperties, baseInformationOrBuilder ->
                        TraversalHelper.forAllSampleCounts(baseInformationOrBuilder, BaseInformationRecords.CountInfo::getQualityScoresReverseStrandList)),
                new DensityMapper("insertSizes", 10, sbiProperties, (BaseInformationRecords.BaseInformationOrBuilder baseInformationOrBuilder) -> {
                    return TraversalHelper.forAllSampleCounts(baseInformationOrBuilder, BaseInformationRecords.CountInfo::getInsertSizesList);
                },
                        insertSize -> (float) Math.log10(insertSize))

        );


    }

    @Override
    public void setSampleIndex(int sampleIndex) {
        LOG.warn("This mapper does not support configurable sampleIndex. It uses exactly one sample sbi files.");
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
