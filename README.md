# VariationAnalysis

[![Join the chat at https://gitter.im/CampagneLaboratory/variationanalysis](https://badges.gitter.im/CampagneLaboratory/variationanalysis.svg)](https://gitter.im/CampagneLaboratory/variationanalysis?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This project provides:

- A deep learning model training and evaluation framework (codename Matcha).
Builds on [DL4J](http://deeplearning4j.org)
and provides abstractions and organizing principles useful when training
and evaluating models in practice (see [doc](./docs/doc.md)). Supports both CPU and GPU model training
and inference.
We distribute the CPU version on maven, but you can build the project and choose
the GPU maven profile to compile a CUDA version on the appropriate hardware.
- A tool to train DL models for calling somatic variations. An example
of using the framework for a specific domain.
- A preview of a tool to train DL models for calling genotypes.
This is a straightforward application
of the Matcha framework and takes advantage of ground-truth genotypes
established for specific quality control samples.

# Download
The project needs to be cloned from github at this time if you need to train models. Binary releases are included in the Goby binary distributions (see [http://campagnelab.org/software/goby/download-goby/]). This makes it possible to download pre-trained models and call genotypes directly with goby. Pre-trained models are distributed at [https://github.com/CampagneLaboratory/genotype-dl-models].

# Tutorials

We provide a [tutorial](./SOMATIC-TUTORIAL.md) for the somatic calling models.
It is strongly recommended that you read the tutorial at this time. It
demonstrates an end-to-end application where we collect data, train a model,
and use it in an application. 

A [second tutorial](./GENOTYPE-TUTORIAL-LONG.md) describes how to train genotype models. 

Two of the tools demonstrated in the tutorial have been developed with
the framework:

 - train-somatic.sh. This tool implements training with early stopping.
 - predict.sh. This tool uses a trained model to predict on a dataset.
 It provides features to filter results which are useful for error
 analysis.
 - search-hyper-params.sh. A tool to help determine which hyper-parameters
 result in models with higher performance. It takes the number of models to
 evaluate and a train-somatic.sh command line, generates random combinations
 of parameter values, and run train-somatic.sh. Performance is written in the
 model-conditions.txt file after each model is trained. This makes it easy to
 pick conditions that work best.

 The somatic tools also include:

 - show.sh. This tool also helps with error analysis. It shows input records
 in various formats for the examples identified by the predict.sh tool.
 - split.sh. This  tool splits a training set into different files. Useful
 to create training, validation and test splits.
 - randomize.sh. This tool takes a number of training sets and shuffles the records
 in them. Useful to remove any order (i.e., correlation due to genomic position).
 Scales to files with hundreds of million of examples.

Interested in genotype calling with deep learning models? Have a look at the preview
 of the genotype module ([tutorial](./GENOTYPE-TUTORIAL-SHORT.md) and [doc](./docs/genotype/genotype.md)).

[![framework javadocs](http://www.javadoc.io/badge/org.campagnelab.dl/framework.svg?label=framework_javadoc)](http://www.javadoc.io/doc/org.campagnelab.dl/framework)
[![somatic javadocs](http://www.javadoc.io/badge/org.campagnelab.dl/somatic.svg?label=somatic_javadoc)](http://www.javadoc.io/doc/org.campagnelab.dl/somatic)
[![genotype javadocs](http://www.javadoc.io/badge/org.campagnelab.dl/genotype.svg?label=genotype_javadoc)](http://www.javadoc.io/doc/org.campagnelab.dl/somatic)

See the project on [GitHub](https://github.com/CampagneLaboratory/variationanalysis) --- Learn more about the [Campagne Laboratory](http://campagnelab.org)
