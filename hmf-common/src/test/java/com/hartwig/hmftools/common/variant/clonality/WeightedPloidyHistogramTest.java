package com.hartwig.hmftools.common.variant.clonality;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.numeric.Doubles;

import org.apache.commons.math3.distribution.BinomialDistribution;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class WeightedPloidyHistogramTest {

    @Test
    public void testBucketWithOffset() {
        final WeightedPloidyHistogram negativeOffset = new WeightedPloidyHistogram(10, 0.05, -0.02);
        assertEquals(1, negativeOffset.bucket(0.05));
        assertEquals(2, negativeOffset.bucket(0.06));
        assertEquals(2, negativeOffset.bucket(0.08));
        assertEquals(2, negativeOffset.bucket(0.10));
        assertEquals(3, negativeOffset.bucket(0.11));

        final WeightedPloidyHistogram positiveOffset = new WeightedPloidyHistogram(10, 0.05, 0.02);
        assertEquals(0, positiveOffset.bucket(0.04));
        assertEquals(1, positiveOffset.bucket(0.05));
        assertEquals(1, positiveOffset.bucket(0.07));
        assertEquals(1, positiveOffset.bucket(0.09));
        assertEquals(2, positiveOffset.bucket(0.10));
    }

    @Test
    public void testBucketNoOffset() {

        final WeightedPloidyHistogram victim = new WeightedPloidyHistogram(10, 0.01);
        assertEquals(0, victim.bucket(0.00499));
        assertEquals(1, victim.bucket(0.00500));
        assertEquals(1, victim.bucket(0.01499));
        assertEquals(2, victim.bucket(0.01500));

        assertEquals(0.00, victim.ploidy(0), 0.01);
        assertEquals(0.01, victim.ploidy(1), 0.01);
        assertEquals(0.02, victim.ploidy(2), 0.01);
    }

    @Test
    public void testHistogramConstruction() {
        final WeightedPloidyHistogram victim = new WeightedPloidyHistogram(10, 0.01);
        final List<ModifiableWeightedPloidy> ploidies = readResource("ploidies.tsv");
        double[] histogram = victim.histogram(ploidies);

        assertEquals(1, histogram[victim.bucket(0.18)], 0.1);
        assertEquals(328, histogram[victim.bucket(0.97)], 0.1);
        assertEquals(320, histogram[victim.bucket(1)], 0.1);
        assertEquals(100, histogram[victim.bucket(1.8)], 0.1);
    }

    @Test
    public void testHistogramConstructionWithNoOffset() {
        final WeightedPloidyHistogram victim = new WeightedPloidyHistogram(10, 0.05);
        final List<ModifiableWeightedPloidy> ploidies = readResource("ploidies.tsv");
        double[] histogram = victim.histogram(ploidies);

        assertEquals(2, histogram[victim.bucket(0.20)], 0.1);
        assertEquals(6, histogram[victim.bucket(0.25)], 0.1);
        assertEquals(38, histogram[victim.bucket(0.3)], 0.1);

    }

    @Test
    public void testHistogramConstructionWithOffset() {
        final WeightedPloidyHistogram victim = new WeightedPloidyHistogram(10, 0.05, 0.02);
        final List<ModifiableWeightedPloidy> ploidies = readResource("ploidies.tsv");
        double[] histogram = victim.histogram(ploidies);

        assertEquals(1, histogram[victim.bucket(0.17)], 0.1);
        assertEquals(1, histogram[victim.bucket(0.22)], 0.1);
        assertEquals(16, histogram[victim.bucket(0.27)], 0.1);
    }

    @Test
    public void testHistogramPeak() {
        assertEquals(2,  WeightedPloidyHistogram.peakBucket(1, new double[]{10, 11, 1, 18, 8}));
        assertEquals(3,  WeightedPloidyHistogram.peakBucket(1, new double[]{10, 11, 0, 18, 8}));

        assertEquals(1,  WeightedPloidyHistogram.peakBucket(2, new double[]{10, 11, 0, 18, 8}));
    }

    @Test
    public void testPeakLikelihood() {
        final PeakModelFactory victim = new PeakModelFactory(10, 0.05);
        final WeightedPloidy ploidy = create(2, 35, 50);
        assertEquals(0.06, victim.ploidyLikelihood(1.8, ploidy), 0.001);
    }

    @NotNull
    static List<ModifiableWeightedPloidy> readResource(@NotNull final String file) {
        final InputStream inputStream = WeightedPloidyHistogramTest.class.getResourceAsStream("/clonality/" + file);
        final List<String> lines = new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.toList());

        final List<ModifiableWeightedPloidy> result = Lists.newArrayList();
        for (String line : lines) {
            String[] values = line.split("\t");

            ModifiableWeightedPloidy ploidy = ModifiableWeightedPloidy.create()
                    .setPloidy(Double.valueOf(values[0]))
                    .setAlleleReadCount(Integer.valueOf(values[1]))
                    .setTotalReadCount(Integer.valueOf(values[2]))
                    .setWeight(1);

            if (Doubles.lessThan(Double.valueOf(values[0]), 10)) {
                result.add(ploidy.setDistribution(new BinomialDistribution(ploidy.totalReadCount(), ploidy.alleleFrequency())));
            }
        }

        return result;
    }

    private static WeightedPloidy create(double ploidy, int alleleReadCount, int totalReadCount) {
        return ModifiableWeightedPloidy.create()
                .setPloidy(ploidy)
                .setAlleleReadCount(alleleReadCount)
                .setTotalReadCount(totalReadCount)
                .setDistribution(new BinomialDistribution(totalReadCount, 1d * alleleReadCount / totalReadCount));
    }

}
