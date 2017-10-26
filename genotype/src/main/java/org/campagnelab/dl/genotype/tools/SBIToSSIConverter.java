package org.campagnelab.dl.genotype.tools;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.logging.ProgressLogger;
import org.campagnelab.dl.framework.mappers.FeatureMapper;
import org.campagnelab.dl.framework.tools.arguments.AbstractTool;
import org.campagnelab.dl.genotype.helpers.GenotypeHelper;
import org.campagnelab.dl.genotype.learning.architecture.graphs.GenotypeSegmentsLSTM;
import org.campagnelab.dl.genotype.learning.domains.GenotypeDomainDescriptor;
import org.campagnelab.dl.genotype.mappers.NumDistinctAllelesLabelMapper;
import org.campagnelab.dl.genotype.segments.*;
import org.campagnelab.dl.genotype.segments.splitting.NoSplitStrategy;
import org.campagnelab.dl.genotype.segments.splitting.SingleCandidateIndelSplitStrategy;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.StreamSupport;


/**
 * Tool to convert from SBI to SSI format.
 *
 * @author manuele
 */
public class SBIToSSIConverter extends AbstractTool<SBIToSSIConverterArguments> {

    static private Logger LOG = LoggerFactory.getLogger(SBIToSSIConverter.class);
    private static SequenceSegmentInformationWriter writer;
    private static Function<Segment, Segment> processSegmentFunction;
    private static FillInFeaturesFunction fillInFeaturesFunction;
    private static ThreadLocal<SegmentHelper> segmentHelper;

    // any genomic site that has strictly more indel supporting reads than the below threshold will be marked has candidateIndel.
    private static int candidateIndelThreshold = 0;

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
        Consumer<Segment> segmentConsumer= segment -> {
            try {
                segment.writeTo(writer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        segmentHelper = new ThreadLocal<SegmentHelper>() {
            @Override
            protected SegmentHelper initialValue() {
                return new SegmentHelper( processSegmentFunction, fillInFeaturesFunction, segmentConsumer ,
                        new SingleCandidateIndelSplitStrategy(100,candidateIndelThreshold),
                        args().collectStatistics );
            }
        };
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
        if (args().snpOnly) {
            processSegmentFunction = segment -> {
                // remove all indels
                segment.recordList.removeWhere(record -> record.getTrueGenotype().length() > 3);
                return segment;
            };
        } else {
            processSegmentFunction = segment -> {
                segment.populateTrueGenotypes();
                BaseInformationRecords.BaseInformation previous = null;
                BaseInformationRecords.BaseInformation buildFrom = null;

                for (BaseInformationRecords.BaseInformation record : segment.recordList) {

                    buildFrom = record;
                    if (record.getTrueGenotype().length() > 3) {
                        Set<String> alleles = GenotypeHelper.getAlleles(record.getTrueGenotype());
                        for (String allele : alleles) {
                            if (allele.length() > 1) {
                                String insertionOrDeletion = getInsertedDeleted(allele);
                                int offset = 1;
                                for (char insertedDeleted : insertionOrDeletion.toCharArray()) {
                                    if (previous == null)
                                        previous = buildFrom;
                                    else
                                        previous = segment.recordList.insertAfter(previous, buildFrom, insertedDeleted, offset++);
                                }
                            }
                        }
                        // long genotypes need to be trimmed to the first base after this point. We don't do it here because
                        // it would modify the data structure currently traversed.
                    }

                    previous = record;
                }

                segment.recordList.forEach(record -> {
                    int longestIndelLength = 0;
                    for (BaseInformationRecords.SampleInfo sample : record.getSamplesList()) {
                        for (BaseInformationRecords.CountInfo count : sample.getCountsList()) {
                            if (count.getIsIndel()) {
                                longestIndelLength = Math.max(longestIndelLength, count.getFromSequence().length());
                                longestIndelLength = Math.max(longestIndelLength, count.getToSequence().length());
                            }
                        }

                    }
                    for (int offset = 1; offset < longestIndelLength; offset++) {
                        BaseInformationRecords.BaseInformation.Builder copy = record.toBuilder();
                        //    System.out.printf("record position: %d %n",record.getPosition());
                        copy = segment.recordList.adjustCounts(copy, offset);
                        segment.insertAfter(record, copy);
                    }


                });

                return segment;
            };
        }
        SegmentLabelMapper labelMapper = new SegmentLabelMapper(args().ploidy);


        fillInFeaturesFunction = new MyFillInFeaturesFunction(featureMapper, labelMapper, arguments);

        try {
            if (args().ssiPrefix != null)
                writer = new SequenceSegmentInformationWriter(args().ssiPrefix);
            else
                writer = new SequenceSegmentInformationWriter(BasenameUtils.getBasename(args().inputFile,
                        FileExtensionHelper.COMPACT_SEQUENCE_BASE_INFORMATION));
            Properties props = new Properties();
            labelMapper.writeMap(props);
            writer.appendProperties(props);
            RecordReader sbiReader = new RecordReader(new File(args().inputFile).getAbsolutePath());
            ProgressLogger pg = new ProgressLogger(LOG);
            pg.displayFreeMemory = true;
            pg.expectedUpdates = sbiReader.getTotalRecords();
            pg.itemsName = "records";
            pg.start();
            final int[] totalRecords = {0};

            StreamSupport.stream(sbiReader.spliterator(), args().parallel).forEach(sbiRecord -> {
                try {
                    manageRecord(sbiRecord, gap);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                pg.lightUpdate();
                totalRecords[0]++;
            });

            System.out.printf("Total record managed: %d %n", totalRecords[0]);
            pg.stop();
            closeOutput();
        } catch (IOException e) {
            System.err.println("Failed to parse " + args().inputFile);
            e.printStackTrace();
        }

    }

    private boolean hasCandidateIndel(BaseInformationRecords.BaseInformation baseInformation) {
        return SegmentUtil.hasCandidateIndel(baseInformation, candidateIndelThreshold);
    }

    private String trimTrueGenotype(String trueGenotype) {
        int secondBaseIndex = trueGenotype.indexOf("|");
        final String trimmed = trueGenotype.charAt(0) + "|" + trueGenotype.charAt(secondBaseIndex - 1);
        return trimmed;
    }

    private String getInsertedDeleted(String allele) {

        if (allele.charAt(1) == '-') {
            // number of deletions in allele.
            int deletionCount = 0;
            // a certain number of bases are deleted in this allele.
            for (int i = 1; i < allele.length(); i++) {
                if (allele.charAt(i) == '-') {
                    deletionCount++;
                } else {
                    break;
                }
            }
            return allele.substring(1, deletionCount);
        } else {
            return allele.substring(1, allele.length() - 2);
        }
    }

    private void manageRecord(BaseInformationRecords.BaseInformation record, int gap) throws IOException {

        if (this.isValid(record)) {
            final SegmentHelper segmentHelper = SBIToSSIConverter.segmentHelper.get();
            if (!this.isSameSegment(record, gap)) {
                segmentHelper.newSegment(record);
            } else {
                segmentHelper.add(record);
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
        if (segmentHelper == null) {
            return false;
        }
        final boolean valid = (record.getPosition() - segmentHelper.get().getCurrentLocation() <= gap) &&
                record.getReferenceIndex() == segmentHelper.get().getCurrentReferenceIndex();
    /*if (!valid) {
        System.out.printf("not valid, actual gap=%d%n",record.getPosition() - segmentList.getCurrentLocation());
    }*/
        return valid;

    }

    /**
     * Closes the list and serializes the output SSI.
     */
    private void closeOutput() throws IOException {
        segmentHelper.get().close();
        try {
            writer.close();
        } catch (IOException e) {
            System.err.println("Failed to close the SSI file");
            e.printStackTrace();
        } finally {
            writer = null;
        }
        segmentHelper.get().printStats();
    }


}
