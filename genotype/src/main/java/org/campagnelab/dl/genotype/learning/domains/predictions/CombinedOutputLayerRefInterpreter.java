package org.campagnelab.dl.genotype.learning.domains.predictions;

import org.campagnelab.dl.framework.domains.prediction.PredictionInterpreter;
import org.campagnelab.dl.genotype.mappers.CombinedLabelsMapperRef;
import org.campagnelab.dl.genotype.predictions.CombinedOutputLayerPrediction;
import org.campagnelab.dl.varanalysis.protobuf.BaseInformationRecords;
import org.jetbrains.annotations.NotNull;
import org.nd4j.linalg.api.ndarray.INDArray;

/**
 * Interpret model output to determine if a site is homozygous/het and what hte homosygous genotype is.
 * Created by rct66 on 11/12/16.
 */
public class CombinedOutputLayerRefInterpreter extends SortingCountInterpreter<CombinedOutputLayerPrediction>
        implements PredictionInterpreter<BaseInformationRecords.BaseInformation, CombinedOutputLayerPrediction> {

    private static final int MAX_GENOTYPES = CombinedLabelsMapperRef.NUM_LABELS;
    public static final String NO_CALL = ".";
    private String[] toSequences;
    private int referenceGenotypeIndex;
    private double probability;

    public CombinedOutputLayerRefInterpreter() {
        super(true);
        // the default toSequences are integers, because we don't have access to exact genotypes (we have no record),
        // but integers are sufficient to calculate performance stats.
        toSequences = new String[MAX_GENOTYPES];
        for (int genotypeIndex = 0; genotypeIndex < MAX_GENOTYPES; genotypeIndex++) {
            toSequences[genotypeIndex] = Integer.toString(genotypeIndex);
        }
        // the other genotype can never match:
        toSequences[3] = "-1";
    }


    @Override
    public CombinedOutputLayerPrediction interpret(INDArray trueLabels, INDArray output, int exampleIndex) {
        CombinedOutputLayerPrediction p = new CombinedOutputLayerPrediction();
        p.trueGenotype = reconstructGenotype(trueLabels, exampleIndex);
        p.predictedGenotype = reconstructGenotype(output, exampleIndex);
        p.overallProbability=probability;
        return p;
    }

    @Override
    public CombinedOutputLayerPrediction interpret(BaseInformationRecords.BaseInformation record, INDArray output) {
        sortedCountRecord = sort(record);
        CombinedOutputLayerPrediction pred = new CombinedOutputLayerPrediction();
        pred.inspectRecord(sortedCountRecord);
        pred.predictedGenotype = getPrediction(sortedCountRecord, output);
        pred.overallProbability = probability;
        return pred;
    }

    public String getPrediction(BaseInformationRecords.BaseInformation record, INDArray output) {

        for (int genotypeIndex = 0; genotypeIndex < MAX_GENOTYPES; genotypeIndex++) {
            toSequences[genotypeIndex] = record.getSamples(0).getCounts(genotypeIndex).getToSequence();
            if (record.getSamples(0).getCounts(genotypeIndex).getMatchesReference()) {
                referenceGenotypeIndex = genotypeIndex;
            }

        }

        return reconstructGenotype(output, 0);
    }

    @NotNull
    private String reconstructGenotype(INDArray output, int predictionIndex) {
        probability = -1;
        int maxIndex = -1;
        for (int i = 0; i < CombinedLabelsMapperRef.NUM_LABELS; i++) {
            double outputDouble = output.getDouble(predictionIndex, i);
            if (probability < outputDouble) {
                maxIndex = i;
                probability = outputDouble;
            }
        }
        String homozygAllele;
        int altIndex;
        switch (maxIndex) {
            case 0:
                homozygAllele = toSequences[referenceGenotypeIndex];
                return homozygAllele + "/" + homozygAllele;
            case 1:
                if (referenceGenotypeIndex == 0) {
                    altIndex = 1;
                } else {
                    altIndex = 0;
                }
                String firstAllele = toSequences[referenceGenotypeIndex];
                String secondAllele = toSequences[altIndex];
                return firstAllele + "/" + secondAllele;
            case 2:
                if (referenceGenotypeIndex == 0) {
                    altIndex = 1;
                } else {
                    altIndex = 0;
                }
                homozygAllele = toSequences[altIndex];
                return homozygAllele + "/" + homozygAllele;
            default:
                // other site predicted, mark as wrong
                return NO_CALL;
        }
    }
}
