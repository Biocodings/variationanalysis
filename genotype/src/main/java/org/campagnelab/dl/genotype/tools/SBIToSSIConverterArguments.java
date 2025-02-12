package org.campagnelab.dl.genotype.tools;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.campagnelab.dl.framework.tools.arguments.ToolArguments;
import org.campagnelab.dl.genotype.mappers.SingleBaseGenotypeMapperV1;
import org.campagnelab.dl.genotype.segments.splitting.NoSplitStrategy;
import org.campagnelab.dl.genotype.segments.splitting.SingleCandidateIndelSplitStrategy;
import org.campagnelab.dl.genotype.segments.splitting.SplitStrategy;

/**
 * Arguments for the {@link SBIToSSIConverter} tool.
 *
 * @author manuele
 */
@Parameters(commandDescription = "Convert a set of Single Base Information data to Sequence Segment Information structures.")
public class SBIToSSIConverterArguments implements ToolArguments {

    @Parameter(required = true, names = {"-i", "--input-file"}, variableArity = true,
            description = "Input file with the SBI, must be provided in .sbi/.sbip format (produced with Goby3).")
    public String inputFile = null;

    @Parameter(names = {"-g", "--gap"}, description = "Gap between two segments The default is 1.")
    public int gap = 1;

    @Parameter(names = {"--parallel"}, description = "Enable parallel processing of the input SBI.")
    public boolean parallel = false;

    @Parameter(names = {"-o", "--output-basename"}, description = "Prefix for the output saved file. If not specified, the input basename is used.")
    public String ssiPrefix = null;

    @Parameter(names = "--ploidy", description = "Ploidy of the organism. This parameter determines the maximum number of alleles that can be called in the organism.")
    int ploidy = 2;

    @Parameter(names = "--feature-mapper", description = "Name of the class for the feature mapper. Used to convert an SBI record to features for storage in the SSI base.")
    public String featureMapperClassName = SingleBaseGenotypeMapperV1.class.getCanonicalName();

    @Parameter(names = "--map-features", description = "Use to map features.")
    public boolean mapFeatures = false;
    public boolean mapLabels = true;

    @Parameter(names = "--collect-statistics", description = "Collect and display statistics.")
    public boolean collectStatistics = false;

    @Parameter(names = "--snp-only", description = "Do not write indel genotypes to the output. This makes for a much simpler prediction problem to help diagnose problems.")
    public boolean snpOnly;

    @Parameter(names = "--verbose", description = "Be more verbose.")
    public boolean verbose;

    @Parameter(names = "--read-N", description = "Read at most N records from the SBI input, then stop.")
    public long readN=Long.MAX_VALUE;

    @Parameter(names = "--genomic-context-length", description = "Length of the genomic context (odd number, 1,3+).")
    public int genomicContextLength=1;

    @Parameter(names = "--sampling-rate", description = "Percentage of candidate indels that are not true indels to include in the output.")
    public double samplingRate=1;

    enum Strategy {
        NO_SPLIT,
        INDEL1
    }

    @Parameter(names = {"-s", "--split-strategy"}, description = "Strategy used to split segments that are too long. One of NO_SPLIT, or INDEL1")

    public Strategy strategy = Strategy.NO_SPLIT;

    public SplitStrategy getStrategy() {
        switch (strategy) {

            case INDEL1:
                return new SingleCandidateIndelSplitStrategy(windowFlankSize, 0, verbose);
            case NO_SPLIT:
            default:
                return new NoSplitStrategy();
        }
    }

    int windowFlankSize = 100;
}
