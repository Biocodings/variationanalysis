package org.campagnelab.dl.genotype.helpers;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.apache.commons.lang3.StringUtils;
import org.campagnelab.dl.genotype.predictions.GenotypePrediction;
import org.campagnelab.goby.algorithmic.algorithm.EquivalentIndelRegionCalculator;
import org.campagnelab.goby.algorithmic.dsv.SampleCountInfo;
import org.campagnelab.goby.algorithmic.indels.EquivalentIndelRegion;
import org.campagnelab.goby.alignments.processors.ObservedIndel;
import org.campagnelab.goby.predictions.MergeIndelFrom;
import org.campagnelab.goby.util.Variant;

import java.util.Iterator;
import java.util.Set;

/**
 * Created by fac2003 on 12/25/16.
 */
public class GenotypeHelper {


    public static boolean isVariant(String trueGenotype, String referenceBase) {
        return isVariant(true, trueGenotype, referenceBase);
    }


    public static boolean isVariant(Set<String> genotype, String referenceBase) {
        return isVariant(true, genotype, referenceBase);
    }

    public static boolean isNoCall(String genotype) {
        return ("N".equals(genotype) || "N|N".equals(genotype) || "N/N".equals(genotype));
    }

    public static boolean isHeterozygote(String genotype) {
        return getAlleles(genotype).size() == 2;
    }

    public static boolean isVariant(boolean considerIndels, String trueGenotype, String reference) {
        return isVariant(considerIndels, getAlleles(trueGenotype), reference);
    }

    /**
     * @param considerIndels
     * @param genotypeSet    set of "to's"/true genotypes. requires deletions to be padded, since length 1 genotypes will be assumed to be snp/ref.
     * @param reference
     * @return
     */
    public static boolean isVariant(boolean considerIndels, Set<String> genotypeSet, String reference) {


        //handle special case where a homozygous deletion like AG->A is not caught and matches ref
        //such cases chould not be counted as variants.
        boolean matchesRef = false;


        if (genotypeSet.size() == 1) {
            String allele = genotypeSet.iterator().next();
            // When there is only one allele, only the first bases need to match with the reference,
            // up to the length of the reference:
            // i.e., genotype: TC/TC ref: T, or genotype: TCA/TCA ref: TC
            if (considerIndels) {
                matchesRef = (reference.equals(allele));
            } else {
                //just check first base in this case.
                if (reference.charAt(0) == (allele.charAt(0))) {
                    matchesRef = true;
                }
            }

        }
        if (genotypeSet.size() > 1) {
            if (considerIndels) {
                return true;
            } else {
                String referenceBase = reference.substring(0, 1);
                return !referenceBase.equals(genotypeSet.stream().map(allele -> Character.toString(allele.charAt(0))).distinct().findFirst().get());
            }
        }
        return !matchesRef;
    }


    /**
     * this method assumes we want to consider indels, since we are abandoning snp-only development.
     *
     * @param genotypeSet
     * @return
     */
    public static boolean isVariant(Set<Variant.FromTo> genotypeSet) {
        for (Variant.FromTo ft : genotypeSet) {
            if (!ft.getFrom().equals(ft.getTo())) {
                // at least one of the to do not match the from, so this site has a variant.
                return true;
            }
        }
        return false;
    }

    public static Set<String> getAlleles(String genotype) {
        String trueGenotype = genotype.toUpperCase();
        return GenotypePrediction.alleles(trueGenotype);
    }

    public static String fromAlleles(Set<String> alleles) {
        StringBuffer sb = new StringBuffer();
        if (alleles.size() > 1) {
            for (String allele : alleles) {
                sb.append(allele + "|");
            }
            return sb.substring(0, sb.length() - 1);
        } else {
            for (String allele : alleles) {
                return (allele + "|" + allele).toUpperCase();
            }
            return ".|.";
        }

    }

    public static Set<String> fromTosToAlleles(Set<Variant.FromTo> alleles) {
        Set<String> toSet = new ObjectArraySet<>(alleles.size());
        for (Variant.FromTo allele : alleles) {
            toSet.add(allele.getTo());
        }
        return toSet;

    }

    public static String fromFromTos(Set<Variant.FromTo> alleles) {
        return fromAlleles(fromTosToAlleles(alleles));
    }


    /**
     * genotype must have two alleles.
     *
     * @param gobyFormatReference
     * @param gobyFormatGenotype  genotype in goby format. ref/snps must have single base in ref and to.
     * @return
     */
    public static boolean isIndel(String gobyFormatReference, String gobyFormatGenotype) {
        if (gobyFormatGenotype != null) {
            return (gobyFormatGenotype.length() > 3 || gobyFormatReference.length() > 1);
        }
        return false;
    }


    /**
     * Check if a set of alleles contains an indel.
     * genotype must have two alleles
     *
     * @param mergedGenotypeSet a merged format genotype set, all from's should be the same via merge (to's and from's same length).
     * @return
     */
    public static boolean isIndel(MergeIndelFrom mergedGenotypeSet) {
        int numFromDashes = StringUtils.countMatches(mergedGenotypeSet.getFrom(), "-");
        for (String to : mergedGenotypeSet.getTos()) {
            int numToDashes = StringUtils.countMatches(to, "-");
            if (numFromDashes != numToDashes) {
                return true;
            }
        }
        return false;
    }


    public static boolean matchingGenotypesWithN(String a, String b) {
        if (isNoCall(a) || isNoCall(b)) {
            return true;
        }
        return matchingGenotypes(a, b);
    }

    public static boolean matchingGenotypes(String a, String b) {

        Set<String> allelesA = getAlleles(a);
        Set<String> allelesB = getAlleles(b);
        boolean allelesMatch = allelesA.equals(allelesB);
        if (allelesMatch) {
            return true;
        }
        if (allelesA.size() == 1 && allelesB.size() == 1) {

            String oneA = allelesA.iterator().next();
            String oneB = allelesB.iterator().next();
            if (a.contains("-") || b.contains("-")) {
                // do not allow prefix match for indels.
                return false;
            }
            if ((Character.isDigit(oneB.charAt(0)) && Integer.parseInt(oneB) >= SampleCountInfo.BASE_MAX_INDEX)) {
                return false;
            }
            if ((Character.isDigit(oneA.charAt(0)) && Integer.parseInt(oneA) >= SampleCountInfo.BASE_MAX_INDEX)) {
                return false;
            }
            if (oneA.length() > oneB.length()) {
                return oneA.startsWith(oneB);
            }
            if (oneB.length() > oneA.length()) {
                return oneB.startsWith(oneA);
            }
        }
        return false;
    }

    /**
     * Return true iff the true genotype has an allele matching toSequence.
     *
     * @param trueGenotype a true genotype, e.g., A/T
     * @param testAllele   the sequence of an allele, e.g., A
     * @return True if and only if one of the true genotype alleles is matching testAllele.
     */
    public static boolean genotypeHasAllele(String trueGenotype, String testAllele) {
        Set<String> alleles = getAlleles(trueGenotype);
        Iterator<String> iterator = alleles.iterator();
        while (iterator.hasNext()) {
            String oneTrueAllele = iterator.next();
            if (matchingGenotypes(oneTrueAllele, testAllele)) {
                return true;
            }
        }
        return false;
    }


    public static String pad(String s, int maxLength) {
        StringBuffer toPad = new StringBuffer(s);
        for (int i = 1; i <= maxLength; i++) {
            if (toPad.length() < maxLength) {
                toPad.append("-");
            }
        }
        return toPad.toString();
    }

    public static int maxLength(Set<String> strings) {
        int max = 0;
        for (String s : strings) {
            if (s.length() > max) {
                max = s.length();
            }
        }
        return max;
    }


    public static String padMulti(String genotype, int maxLength) {
        StringBuffer toPad = new StringBuffer();
        Set<String> alleles = getAlleles(genotype);
        for (String allele : alleles) {
            toPad.append(pad(allele, maxLength) + "|");
        }
        //remove final |
        toPad.deleteCharAt(toPad.length() - 1);
        return toPad.toString();
    }

    public static boolean genotypeHasAlleleOrIndel(Set<Variant.FromTo> trueAlleles, String fromSequence, String toSequence) {
        boolean hasTo = false;


        Set<Variant.FromTo> extendedTrueAlleles = new ObjectArraySet<>(trueAlleles);

        for (Variant.FromTo allele : trueAlleles) {
            //handle true genotype extended further than necessary with insertions
            //eg: true: A--TGTG -> ATGTGTG, genotype : A--TG -> ATGTG
            if (fromSequence.contains("-") && allele.getFrom().length() > fromSequence.length() && allele.getTo().length() > toSequence.length() && fromSequence.equals(allele.getFrom().substring(0, fromSequence.length())) && toSequence.equals(allele.getTo().substring(0, toSequence.length()))) {
                extendedTrueAlleles.add(new Variant.FromTo(allele.getFrom().substring(0, fromSequence.length()), allele.getTo().substring(0, toSequence.length())));
            }

        }

        for (Variant.FromTo oneTrueAllele : extendedTrueAlleles) {
            String fromSequenceForCandidate = fromSequence;
            String toSequenceForCandidate = toSequence;
            // if from to have a common tail, first remove the tail (i.e., C-A AA/ CAA AA would yield from=C-A to =CAA)
            // this can happen because the equivalent indel calculator may add a longer tail to remove ambiguities
            if (fromSequence.length() == toSequence.length()) {
                int length = fromSequence.length();
                if (length > oneTrueAllele.getFrom().length()) {
                    for (int i = length - 1; i >= oneTrueAllele.getFrom().length(); i--) {
                        if (fromSequence.charAt(i) == toSequence.charAt(i)) {
                            length--;
                        }
                    }
                    if (length != fromSequence.length()) {
                        fromSequenceForCandidate = fromSequence.substring(0, length);
                        toSequenceForCandidate = toSequence.substring(0, length);
                    }
                }

            }
            Variant.FromTo candidate = new Variant.FromTo(fromSequenceForCandidate, toSequenceForCandidate);
            hasTo |= (oneTrueAllele.equals(candidate));
        }
        return hasTo;
    }


    class callTruePair {
        String trueTo; //true allele
        String ref; //vcf's "from" string
        String from; //goby's from
        String to; //goby's to
        int posRef;
        int referenceIndex;

        /*
        *extend true allele to match length of its ref. eg: ATC A -> ATC A--
        * This is a required step before using observeIndels, which requires placeholder dashes
         */
        void padTrueAndRef() {
            int maxLen = Math.max(trueTo.length(), ref.length());
            trueTo = pad(trueTo, maxLen);
            ref = pad(ref, maxLen);
        }

        void trueToToEquivalent() {

            if (trueTo.length() < 2 || ref.length() < 2) {
                //snp encountered, in an indel case one will be longer and the other should have been padded.
                return;
            }
            String trueToAffix = trueTo.substring(1);
            String refAffix = ref.substring(1);


            ObservedIndel indel = new ObservedIndel(posRef, posRef, refAffix, "T");
            EquivalentIndelRegion result = equivalentIndelRegionCalculator.determine(3, indel);


//            assertEquals(3, result.referenceIndex);
//            assertEquals(4, result.startPosition);
//            assertEquals(7, result.endPosition);
//            assertEquals("-TT", result.from);
//            assertEquals("TTT", result.to);
//            assertEquals("AAAC", result.flankLeft);
//            assertEquals("GGGG", result.flankRight);
//            assertEquals("AAAC-TTGGGG", result.fromInContext());
//            assertEquals("AAACTTTGGGG", result.toInContext());
        }

    }

    private EquivalentIndelRegionCalculator equivalentIndelRegionCalculator;


}
