package org.campagnelab.dl.genotype.tools;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.logging.ProgressLogger;
import org.campagnelab.dl.framework.mappers.FeatureMapper;
import org.campagnelab.dl.framework.tools.arguments.AbstractTool;
import org.campagnelab.dl.genotype.learning.architecture.graphs.GenotypeSegmentsLSTM;
import org.campagnelab.dl.genotype.learning.domains.GenotypeDomainDescriptor;
import org.campagnelab.dl.genotype.mappers.NumDistinctAllelesLabelMapper;
import org.campagnelab.dl.somatic.storage.RecordReader;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.dl.varanalysis.protobuf.SegmentInformationRecords;
import org.campagnelab.goby.baseinfo.BasenameUtils;
import org.campagnelab.goby.baseinfo.SequenceSegmentInformationWriter;
import org.campagnelab.goby.util.FileExtensionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.function.Function;


/**
 * Tool to convert from SBI to SSI format.
 *
 * @author manuele
 */
public class SBIToSSIConverter extends AbstractTool<SBIToSSIConverterArguments> {

    static private Logger LOG = LoggerFactory.getLogger(SBIToSSIConverter.class);
    SequenceSegmentInformationWriter writer = null;

    SegmentList segmentList;
    private Function<SegmentList.Segment, SegmentList.Segment> function;
    private Function<BaseInformationRecords.BaseInformation, SegmentInformationRecords.Base.Builder> fillInFeaturesFunction;

    public static void main(String[] args) {
        SBIToSSIConverter tool = new SBIToSSIConverter();
        tool.parseArguments(args, "SBIToSSIConverter", tool.createArguments());
        tool.execute();
    }

    @Override
    public SBIToSSIConverterArguments createArguments() {
        return new SBIToSSIConverterArguments();
    }

    @Override
    public void execute() {
        if (args().inputFile.isEmpty()) {
            System.err.println("You must provide input SBI files.");
        }
        int gap = args().gap;
        GenotypeDomainDescriptor domainDescriptor = null;
        try {
            RecordReader sbiReader = new RecordReader(new File(args().inputFile).getAbsolutePath());

            Properties sbiProperties = sbiReader.getProperties();
            Properties domainProperties = new Properties();
            domainProperties.put("net.architecture.classname", GenotypeSegmentsLSTM.class.getCanonicalName());
            domainProperties.put(NumDistinctAllelesLabelMapper.PLOIDY_PROPERTY, Integer.toString(args().ploidy));
            domainProperties.put("input.featureMapper", args().featureMapperClassName);
            domainProperties.put("genomicContextLength", "1");
            domainProperties.put("indelSequenceLength", "1");

            domainDescriptor = new GenotypeDomainDescriptor(domainProperties, sbiProperties);
        } catch (IOException e) {
            System.err.println("Unable to initialized genotype domain descriptor.");
            e.printStackTrace();
        }


        FeatureMapper featureMapper = domainDescriptor.getFeatureMapper("input");
        //LabelMapper labelMapper=domainDescriptor.getFeatureMapper("input");

        function = segment -> {
            for (BaseInformationRecords.BaseInformation record : segment.records) {

            }
            return segment;
        };
        FloatList features = new FloatArrayList();
        fillInFeaturesFunction = baseInformation -> {
            SegmentInformationRecords.Base.Builder builder = SegmentInformationRecords.Base.newBuilder();
            featureMapper.prepareToNormalize(baseInformation, 0);
            if (baseInformation.getTrueGenotype().length() > 3) {
                System.out.println("Indel:" + baseInformation.getTrueGenotype());
            }
            features.clear();
            for (int featureIndex = 0; featureIndex < featureMapper.numberOfFeatures(); featureIndex++) {
                features.add(featureMapper.produceFeature(baseInformation, featureIndex));
            }
            builder.addAllFeatures(features);
            return builder;
        };

        try {
            if (args().ssiPrefix != null)
                writer = new SequenceSegmentInformationWriter(args().ssiPrefix);
            else
                writer = new SequenceSegmentInformationWriter(BasenameUtils.getBasename(args().inputFile,
                        FileExtensionHelper.COMPACT_SEQUENCE_BASE_INFORMATION));
            RecordReader sbiReader = new RecordReader(new File(args().inputFile).getAbsolutePath());
            ProgressLogger pg = new ProgressLogger(LOG);
            pg.expectedUpdates = sbiReader.getTotalRecords();
            pg.itemsName="records";
            pg.start();
            for (BaseInformationRecords.BaseInformation sbiRecord : sbiReader) {
                manageRecord(sbiRecord, gap);
                pg.lightUpdate();
            }
            pg.stop();
            closeOutput();
        } catch (IOException e) {
            System.err.println("Failed to parse " + args().inputFile);
            e.printStackTrace();
        }

    }

    private void manageRecord(BaseInformationRecords.BaseInformation record, int gap) {
        if (segmentList == null) {
            segmentList = new SegmentList(record, this.writer, function, fillInFeaturesFunction);
        } else {
            if (this.isValid(record)) {
                if (!this.isSameSegment(record, gap)) {
                    segmentList.newSegment(record);
                } else {
                    segmentList.add(record);
                }
            }
        }
    }

    /**
     * Checks if this record should be considered.
     *
     * @param record
     * @return
     */
    private boolean isValid(BaseInformationRecords.BaseInformation record) {
        final int[] sum = {0};
        record.getSamplesList().forEach(sample -> {
                    sample.getCountsList().forEach(counts -> {
                        sum[0] += counts.getGenotypeCountForwardStrand();
                        sum[0] += counts.getGenotypeCountReverseStrand();
                    });
                }
        );
        //System.out.println("Sum for the counts is " + sum[0]);
        return (sum[0] > 0);
    }

    /**
     * Checks if the record belongs to the current segment.
     *
     * @param record
     * @param gap
     * @return
     */
    private boolean isSameSegment(BaseInformationRecords.BaseInformation record, int gap) {
        final boolean valid = (record.getPosition() - segmentList.getCurrentLocation() <= gap) &&
                record.getReferenceIndex() == segmentList.getCurrentReferenceIndex();
    /*if (!valid) {
        System.out.printf("not valid, actual gap=%d%n",record.getPosition() - segmentList.getCurrentLocation());
    }*/
        return valid;

    }

    /**
     * Closes the list and serializes the output SSI.
     */
    private void closeOutput() {
        segmentList.close();
        try {
            writer.close();
        } catch (IOException e) {
            System.err.println("Failed to close the SSI file");
            e.printStackTrace();
        } finally {
            writer = null;
        }
    }


}
