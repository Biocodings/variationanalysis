package org.campagnelab.dl.somatic.learning;

import com.beust.jcommander.Parameter;
import org.campagnelab.dl.framework.tools.TrainingArguments;
import org.campagnelab.dl.somatic.learning.architecture.SixDenseLayersNarrower2;
import org.campagnelab.dl.somatic.mappers.FeatureMapperV20;

/**
 * Arguments specific to somatic model training.
 */
public class SomaticTrainingArguments extends TrainingArguments {
    @Parameter(names = "--trio", description = "Use to train trio models. The training and validation datasets must have three samples, parents first, patient last.")
    public boolean isTrio = false;
    @Parameter(names = "--auc-clip-max-observations", description = "The maximum number of observations to sample when evaluating the AUC. ")
    public int aucClipMaxObservations = 10000;

    @Override
    protected String defaultArchitectureClassname() {
        return SixDenseLayersNarrower2.class.getCanonicalName();
    }

    @Override
    protected String defaultFeatureMapperClassname() {
        return FeatureMapperV20.class.getCanonicalName();
    }
}
