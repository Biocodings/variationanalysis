#!/usr/bin/env bash
. `dirname "${BASH_SOURCE[0]}"`/common.sh
if [ "$#" -ne 4 ]; then
   echo "Argument missing. expected arguments memory_size goby_alignment1 goby_alignment2 goby_genome"
   exit 1;
fi

if [ -e configure.sh ]; then
 echo "Loading configure.sh"
 source configure.sh
fi

memory_requirement=$1
#!/usr/bin/env bash
. `dirname "${BASH_SOURCE[0]}"`/setup.sh
ALIGNMENTS=$1" "$2
GENOME=$3
DATE=`date +%Y-%m-%d`
echo ${DATE} >DATE.txt

case ${ALIGNMENTS} in *.bam) OUTPUT_PREFIX=`basename ${ALIGNMENTS} .bam`;; esac
case ${ALIGNMENTS} in *.sam) OUTPUT_PREFIX=`basename ${ALIGNMENTS} .sam`;; esac
case ${ALIGNMENTS} in *.cram) OUTPUT_PREFIX=`basename ${ALIGNMENTS} .cram`;; esac
case ${ALIGNMENTS} in *.entries) OUTPUT_PREFIX=`basename ${ALIGNMENTS} .entries`;; esac
OUTPUT_PREFIX="${OUTPUT_PREFIX}-${DATE}"
echo "Will write results to ${OUTPUT_PREFIX}"

if [ -z "${DELETE_TMP}" ]; then
    DELETE_TMP="false"
    echo "DELETE_TMP set to ${DELETE_TMP}. Change the variable with export to clear the working directory."
fi

if [ -z "${SBI_SPLIT_OVERRIDE_DESTINATION+set}" ]; then
   export SBI_SPLIT_OVERRIDE_DESTINATION_OPTION=""
   echo "SBI_SPLIT_OVERRIDE_DESTINATION not set. Change the variable to chr21,chr22 to include only chr21 and chr22 in the test set."
else
    export SBI_SPLIT_OVERRIDE_DESTINATION_OPTION=" --destination-override test:${SBI_SPLIT_OVERRIDE_DESTINATION} "
    echo "Using SBI_SPLIT_OVERRIDE_DESTINATION=${SBI_SPLIT_OVERRIDE_DESTINATION} to put chromosomes ${SBI_SPLIT_OVERRIDE_DESTINATION} into test set."
fi
if [ -z "${SBI_GENOTYPE_VARMAP+set}" ]; then
  echo "You may provide a varmap to include specific sites in the result. Set SBI_GENOTYPE_VARMAP to the path of a varmap (use goby vcf-to-genotype-map to create)."
fi

if [ -z "${REF_SAMPLING_RATE+set}" ]; then
    # We keep only 1% of sites to create a training set. If a varmap is provided, sites in the varmap are always included, irrespective of
    # sampling rate.
    REF_SAMPLING_RATE="0.01"
    echo "REF_SAMPLING_RATE set to ${REF_SAMPLING_RATE}. Change the variable to influence what percent of reference sites are sampled."
else
 echo "REF_SAMPLING_RATE set to ${REF_SAMPLING_RATE}."
fi

export SBI_GENOME=${GENOME}
rm -rf tmp
mkdir -p tmp

export OUTPUT_BASENAME=tmp/genotype_full_called.sbi

parallel-genotype-sbi.sh 10g ${ALIGNMENTS} 2>&1 | tee parallel-genotype-sbi.log
dieIfError "Failed to generate .sbi file"

export OUTPUT_BASENAME=${OUTPUT_PREFIX}

mutate.sh 20g -i tmp/genotype_full_called.sbi  -o tmp/genotype_full_called_mutated.sbi

randomize.sh ${memory_requirement} -i tmp/genotype_full_called_mutated.sbi  \
  -o tmp/genotype_full_called_mutated_randomized -b 100000 -c 100   --random-seed 2378237 |tee randomize.log
dieIfError "Failed to randomize"

split.sh ${memory_requirement} -i tmp/genotype_full_called_mutated_randomized.sbi \
  --random-seed 2378237 \
  -f 0.8 -f 0.1 -f 0.1 \
  -o "${OUTPUT_BASENAME}-" \
   -s train -s test -s validation ${SBI_SPLIT_OVERRIDE_DESTINATION_OPTION} | tee split.log
dieIfError "Failed to split"

if [ ${DELETE_TMP} = "true" ]; then
   rm -rf tmp
fi

export DATASET="${OUTPUT_BASENAME}-"
