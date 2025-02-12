package org.campagnelab.dl.genotype.learning;

import org.campagnelab.dl.framework.domains.DomainDescriptor;
import org.campagnelab.dl.framework.tools.TrainModel;
import org.campagnelab.dl.framework.tools.TrainingArguments;
import org.campagnelab.dl.genotype.learning.domains.GenotypeDomainDescriptor;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.campagnelab.goby.baseinfo.SequenceBaseInformationReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * Train a Genotype model. Implemented by specializing the framework TrainModel class.
 */
public class TrainModelG extends TrainModel<BaseInformationRecords.BaseInformation> {
    static private Logger LOG = LoggerFactory.getLogger(TrainModelG.class);

    public static void main(String[] args) {

        TrainModelG tool = new TrainModelG();
        tool.parseArguments(args, "TrainModelG", tool.createArguments());
        if (tool.args().trainingSets.size() == 0) {
            System.out.println("Please add exactly one training set to the args().");
            return;
        }
        assert !tool.args().errorEnrichment : "This tool does not support error enrichment";
        tool.execute();
        tool.writeModelingConditions(tool.getRecordingArguments());
    }

    @Override
    public TrainingArguments createArguments() {
        return new GenotypeTrainingArguments();
    }

    @Override
    protected DomainDescriptor<BaseInformationRecords.BaseInformation> domainDescriptor() {
        return new GenotypeDomainDescriptor((GenotypeTrainingArguments) args());
    }

    @Override
    public Properties getReaderProperties(String trainingSet) throws IOException {

        SequenceBaseInformationReader reader = new SequenceBaseInformationReader(trainingSet);
        final Properties properties = reader.getProperties();
        reader.close();
      //  properties.setProperty("stats.genomicContextSize.min", getGenomicContextArg());
      //  properties.setProperty("stats.genomicContextSize.max", getGenomicContextArg());
        return properties;

    }

    private String getGenomicContextArg() {

        int genomicContextLength = ((GenotypeTrainingArguments) args()).genomicContextLength;
        if ((genomicContextLength % 2) == 0) {
            throw new RuntimeException("The genomic context length must be an odd number, usually in the range 21-61");
        }
        return Integer.toString(genomicContextLength);
    }
}
