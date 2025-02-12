package org.campagnelab.dl.genotype.learning.architecture.graphs;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.campagnelab.dl.framework.architecture.graphs.ComputationGraphAssembler;
import org.campagnelab.dl.framework.domains.DomainDescriptor;
import org.campagnelab.dl.framework.mappers.MappedDimensions;
import org.campagnelab.dl.framework.models.ModelPropertiesHelper;
import org.campagnelab.dl.framework.tools.TrainingArguments;
import org.campagnelab.dl.genotype.tools.SegmentTrainingArguments;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.LearningRatePolicy;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.activations.impl.ActivationSoftmax;
import org.nd4j.linalg.lossfunctions.ILossFunction;

/**
 * A computational graph with an LSTM over bases in a genomic segment [input is 2D].
 * <p>
 *
 * @author Fabien Campagne
 */
public class GenotypeSegmentsLSTM extends GenotypeAssembler implements ComputationGraphAssembler {

    private final String[] outputNames;
    private static final WeightInit WEIGHT_INIT = WeightInit.XAVIER;
    private static final LearningRatePolicy LEARNING_RATE_POLICY = LearningRatePolicy.Score;
    private static final GenotypeSixDenseLayersWithIndelLSTM.OutputType DEFAULT_OUTPUT_TYPE = GenotypeSixDenseLayersWithIndelLSTM.OutputType.DISTINCT_ALLELES;
    private SegmentTrainingArguments arguments;
    private int numLayers;
    private FeedForwardDenseLayerAssembler layerAssembler;
    LearningRatePolicy learningRatePolicy = LearningRatePolicy.Poly;
    private ObjectArrayList<String> componentNames = new ObjectArrayList<>();

    public GenotypeSegmentsLSTM() {

        outputNames = new String[]{"genotype"};

    }

    private SegmentTrainingArguments args() {
        return (SegmentTrainingArguments) arguments;
    }


    @Override
    public void setArguments(TrainingArguments arguments) {
        this.arguments = (SegmentTrainingArguments) arguments;
        this.numLayers = this.arguments.numLayers;
        layerAssembler = new FeedForwardDenseLayerAssembler(arguments);

    }


    @Override
    public ComputationGraph createComputationalGraph(DomainDescriptor domainDescriptor) {
        // the number of elements per time-step (# mapped features for each base):
        final int[] numInputs = domainDescriptor.getNumInputs("input");

        final MappedDimensions dimensions = domainDescriptor.getFeatureMapper("input").dimensions();
        int numTimeSteps = dimensions.dimensions[1];
        int numLSTMInputs = dimensions.dimensions[0];
        System.out.printf("GenotypeSegmentsLSTM getNumInputs: sequence-length=%d, num-float-per-base=%d%n",
                numTimeSteps, numLSTMInputs);
        int numHiddenNodes = domainDescriptor.getNumHiddenNodes("lstm");
        int numLSTMLayers = Math.max(1, args().numLayers);
        FeedForwardDenseLayerAssembler assembler = new FeedForwardDenseLayerAssembler(args());
        assembler.setLearningRatePolicy(LEARNING_RATE_POLICY);
        assembler.initializeBuilder(getInputNames());
        //  assembler.setInputTypes(getInputTypes(domainDescriptor));

        ComputationGraphConfiguration.GraphBuilder build = assembler.getBuild();

        build.setInputTypes(InputType.recurrent(numLSTMInputs, numTimeSteps));
        String lstmInputName = "input";
        String lstmLayerName = "no layer";
        String topLayerName = "n/a";
        int countInput = args().useInputLink ? numLSTMInputs : 0;
        if (args().useInputLink) {
            componentNames.add("input");
        }
        for (int i = 0; i < numLSTMLayers; i++) {
            lstmLayerName = "lstm_" + lstmInputName + "_" + i;
            String lstmPreviousLayerName = i == 0 ? lstmInputName : "lstm_" + lstmInputName + "_" + (i - 1);
            int numLSTMInputConditional = i == 0 ? numLSTMInputs : numHiddenNodes;

            BaseRecurrentLayer.Builder rnnBuilder;
            switch (arguments.rnnKind) {
                case CUDNN_LSTM:
                    rnnBuilder = new LSTM.Builder();
                    ((LSTM.Builder) rnnBuilder).nOut(numHiddenNodes).learningRateDecayPolicy(LEARNING_RATE_POLICY);
                    break;
                case DL4J_BidirectionalGraves:
                    rnnBuilder = new GravesBidirectionalLSTM.Builder();
                    ((GravesBidirectionalLSTM.Builder) rnnBuilder).nOut(numHiddenNodes).learningRateDecayPolicy(LEARNING_RATE_POLICY);

                    break;
                case DL4J_Graves:
                    rnnBuilder = new GravesLSTM.Builder();
                    ((GravesLSTM.Builder) rnnBuilder).nOut(numHiddenNodes).learningRateDecayPolicy(LEARNING_RATE_POLICY);
                    break;
                default:
                    throw new InternalError("RNN kind not supported: " + arguments.rnnKind);
            }

            build.addLayer(lstmLayerName, rnnBuilder
                    .nIn(numLSTMInputConditional)
                    .updater(Updater.ADAM)
                    .activation(Activation.TANH).learningRateDecayPolicy(LEARNING_RATE_POLICY)
                    .build(), lstmPreviousLayerName);
            countInput += numHiddenNodes;
            componentNames.add(lstmLayerName);

        }

        final int genotypeNumOutputs = domainDescriptor.getNumOutputs("genotype")[0];
        build.addLayer("genotype", new RnnOutputLayer.Builder(
                        domainDescriptor.getOutputLoss("genotype"))
                        .weightInit(WEIGHT_INIT)
                        .nIn(countInput)
                        .activation(new ActivationSoftmax()).weightInit(WEIGHT_INIT).learningRateDecayPolicy(learningRatePolicy)
                        .updater(Updater.ADAM).learningRateDecayPolicy(LEARNING_RATE_POLICY)
                        .nOut(genotypeNumOutputs).build(),
                // feed in inputs from all previous layers:
                componentNames.toArray(new String[componentNames.size()]));

     /*   int metadataOutputSize = domainDescriptor.getNumOutputs("metadata")[0];
        build.addLayer("metadata", new OutputLayer.Builder(
                domainDescriptor.getOutputLoss("metadata"))
                .weightInit(WEIGHT_INIT)
                .activation("identity").learningRateDecayPolicy(learningRatePolicy)
                .nIn(countInput)
                .nOut(metadataOutputSize).build(), componentNames.toArray(new String[componentNames.size()]));
*/
        ComputationGraphConfiguration conf = build.setOutputs(outputNames).build();
        // use workspaces for both training and inference phases:
        conf.setTrainingWorkspaceMode(WorkspaceMode.SINGLE);
        conf.setInferenceWorkspaceMode(WorkspaceMode.SEPARATE);
        conf.validate();

        final ComputationGraph computationGraph = new ComputationGraph(conf);

        return computationGraph;
    }

    private InputType getInputTypes(DomainDescriptor domainDescriptor) {
        final MappedDimensions inputDimensions = domainDescriptor.getFeatureMapper("input").dimensions();
        System.out.printf("GenotypeSegmentsLSTM dimensions: sequence-length=%d, num-float-per-base=%d%n",
                inputDimensions.numElements(1), inputDimensions.numElements(2));
        return InputType.recurrent(inputDimensions.numElements(1), inputDimensions.numElements(2));
    }

    @Override
    public void setNumInputs(String inputName, int... dimension) {

    }

    @Override
    public void setNumOutputs(String outputName, int... dimension) {

    }

    @Override
    public void setNumHiddenNodes(String componentName, int numHiddenNodes) {

    }

    @Override
    public String[] getInputNames() {
        return new String[]{"input"};

    }

    @Override
    public String[] getOutputNames() {
        return outputNames;
    }

    @Override
    public String[] getComponentNames() {
        return componentNames.toArray(new String[componentNames.size()]);
    }

    @Override
    public void setLossFunction(String outputName, ILossFunction lossFunction) {
    }

    @Override
    public void saveProperties(ModelPropertiesHelper helper) {
    }
}
