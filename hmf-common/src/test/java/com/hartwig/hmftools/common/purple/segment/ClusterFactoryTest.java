package com.hartwig.hmftools.common.purple.segment;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.cobalt.CobaltRatio;
import com.hartwig.hmftools.common.pcf.ImmutablePCFPosition;
import com.hartwig.hmftools.common.pcf.PCFPosition;
import com.hartwig.hmftools.common.pcf.PCFSource;
import com.hartwig.hmftools.common.purple.PurpleDatamodelTest;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantType;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

public class ClusterFactoryTest {
    private static final String CHROM = "1";

    private ClusterFactory victim;
    private static final int WINDOW = 1000;

    @Before
    public void setup() {
        victim = new ClusterFactory(WINDOW);
    }

    @Test
    public void testBoundaries() {
        final List<ClusterVariantLeg> sv = variants(37383599, 37387153);
        final List<PCFPosition> ratios = createRatioBreaks(36965001, 37381001, 37382001, 37384001, 37387001, 37389001);
        final List<CobaltRatio> cobalt = cobalt(37380001, true, false, true, true, true, true, true);

        final List<Cluster> clusters = victim.cluster(sv, ratios, cobalt);
        assertEquals(4, clusters.size());
        assertRatioInCluster(clusters.get(0), 36964002, 36965001);
        assertRatioInCluster(clusters.get(1), 37380002, 37381001, 37382001);
        assertCluster(clusters.get(2), 37382002L, 37383599L, 37383599L, 37384001L, 37384001L);
        assertCluster(clusters.get(3), 37386002L, 37387153L, 37387153L, 37387001L, 37389001L);
    }

    @Test
    public void testWindowStartWithRatios() {
        final List<CobaltRatio> cobalt = cobalt(37380001, true, false, true, true, true, true, true);

        assertEquals(37380002, victim.start(37381001, 6, cobalt));
        assertEquals(37380002, victim.start(37381002, 6, cobalt));
        assertEquals(37380002, victim.start(37381999, 6, cobalt));
        assertEquals(37380002, victim.start(37382000, 6, cobalt));

        assertEquals(37380002, victim.start(37382001, 6, cobalt));
        assertEquals(37380002, victim.start(37382002, 6, cobalt));
        assertEquals(37380002, victim.start(37382999, 6, cobalt));
        assertEquals(37380002, victim.start(37383000, 6, cobalt));

        assertEquals(37382002, victim.start(37383001, 6, cobalt));
        assertEquals(37382002, victim.start(37383002, 6, cobalt));
        assertEquals(37382002, victim.start(37383999, 6, cobalt));
        assertEquals(37382002, victim.start(37384000, 6, cobalt));
    }

    @Test
    public void testWindowStartWithoutRatios() {
        final List<CobaltRatio> cobalt = Lists.newArrayList();

        assertEquals(37380002, victim.start(37381001, -1, cobalt));
        assertEquals(37380002, victim.start(37381002, -1, cobalt));
        assertEquals(37380002, victim.start(37381999, -1, cobalt));
        assertEquals(37380002, victim.start(37382000, -1, cobalt));

        assertEquals(37381002, victim.start(37382001, -1, cobalt));
        assertEquals(37381002, victim.start(37382002, -1, cobalt));
        assertEquals(37381002, victim.start(37382999, -1, cobalt));
        assertEquals(37381002, victim.start(37383000, -1, cobalt));

        assertEquals(37382002, victim.start(37383001, -1, cobalt));
        assertEquals(37382002, victim.start(37383002, -1, cobalt));
        assertEquals(37382002, victim.start(37383999, -1, cobalt));
        assertEquals(37382002, victim.start(37384000, -1, cobalt));
    }

    @Test
    public void testWindowEndWithRatios() {
        final List<CobaltRatio> cobalt = cobalt(37380001, true, false, true, true, true, true, true);

        assertEquals(37381000, victim.end(37380001, 0, cobalt));

        assertEquals(37383000, victim.end(37380002, 0, cobalt));
        assertEquals(37383000, victim.end(37381001, 0, cobalt));
        assertEquals(37383000, victim.end(37382000, 0, cobalt));
        assertEquals(37383000, victim.end(37382001, 0, cobalt));

        assertEquals(37384000, victim.end(37382002, 0, cobalt));
        assertEquals(37384000, victim.end(37383000, 0, cobalt));
        assertEquals(37384000, victim.end(37383001, 0, cobalt));

        assertEquals(37385000, victim.end(37383002, 0, cobalt));
    }

    @Test
    public void testWindowEndWithoutRatios() {
        final List<CobaltRatio> cobalt = Lists.newArrayList();
        assertEquals(37381000, victim.end(37380001, 0, cobalt));

        assertEquals(37382000, victim.end(37380002, 0, cobalt));
        assertEquals(37382000, victim.end(37381001, 0, cobalt));

        assertEquals(37383000, victim.end(37382000, 0, cobalt));
        assertEquals(37383000, victim.end(37382001, 0, cobalt));

        assertEquals(37384000, victim.end(37382002, 0, cobalt));
        assertEquals(37384000, victim.end(37383001, 0, cobalt));

        assertEquals(37385000, victim.end(37383002, 0, cobalt));
    }

    @Test
    public void testDefaultClusterBounds() {
        final ClusterVariantLeg sv = createSVPosition(15532);
        final List<Cluster> clusters = victim.cluster(Lists.newArrayList(sv), Collections.emptyList(), Collections.emptyList());
        assertEquals(1, clusters.size());
        assertVariantInCluster(clusters.get(0), 14002, 15532);
    }

    @Test
    public void testClusterBoundsWithRatios() {
        final List<ClusterVariantLeg> sv = variants(15532);
        final List<CobaltRatio> ratios = createRatios();
        final List<Cluster> clusters = victim.cluster(sv, Collections.emptyList(), ratios);
        assertEquals(1, clusters.size());
        assertVariantInCluster(clusters.get(0), 12002, 15532);
    }

    @Test
    public void testTwoSVInsideCluster() {
        final List<ClusterVariantLeg> sv = variants(15532, 16771);
        final List<Cluster> clusters = victim.cluster(sv, Collections.emptyList(), Collections.emptyList());
        assertEquals(1, clusters.size());
        assertVariantsInCluster(clusters.get(0), 14002, 15532, 16771);
    }

    @Test
    public void testTwoSVOutsideCluster() {
        final List<ClusterVariantLeg> sv = variants(15532, 17881);
        final List<Cluster> clusters = victim.cluster(sv, Collections.emptyList(), Collections.emptyList());
        assertEquals(2, clusters.size());
        assertVariantInCluster(clusters.get(0), 14002, 15532);
        assertVariantInCluster(clusters.get(1), 16002, 17881);
    }

    @Test
    public void testTwoSVInsideClusterWithRatio() {
        final List<ClusterVariantLeg> sv = variants(15532, 18881);
        final List<CobaltRatio> ratios = createRatios();
        final List<Cluster> clusters = victim.cluster(sv, Collections.emptyList(), ratios);
        assertEquals(1, clusters.size());
        assertVariantsInCluster(clusters.get(0), 12002, 15532, 18881);
    }

    private List<CobaltRatio> createRatios() {
        return cobalt(11001, true, true, false, false, true, false, false, true);
    }

    private static void assertCluster(final Cluster cluster, long start, Long firstVariant, Long finalVariant, Long firstRatio,
            Long finalRatio) {
        assertEquals(start, cluster.start());
        assertEquals(Math.max(finalVariant == null ? 0 : finalVariant, finalRatio == null ? 0 : finalRatio), cluster.end());
        assertEquals(firstVariant, cluster.firstVariant());
        assertEquals(finalVariant, cluster.finalVariant());
        assertEquals(firstRatio, cluster.firstRatio());
        assertEquals(finalRatio, cluster.finalRatio());
    }

    private static void assertRatioInCluster(final Cluster cluster, long start, long position) {
        assertCluster(cluster, start, null, null, position, position);
    }

    private static void assertRatioInCluster(final Cluster cluster, long start, long firstPosition, long finalPosition) {
        assertCluster(cluster, start, null, null, firstPosition, finalPosition);
    }

    private static void assertVariantInCluster(final Cluster cluster, long start, long position) {
        assertCluster(cluster, start, position, position, null, null);
    }

    private static void assertVariantsInCluster(final Cluster cluster, long start, long firstPosition, long finalPosition) {
        assertCluster(cluster, start, firstPosition, finalPosition, null, null);
    }

    private static CobaltRatio cobalt(long position, boolean useable) {
        return ratio(position, useable ? 1 : -1);
    }

    private static CobaltRatio ratio(long position, double ratio) {
        return PurpleDatamodelTest.cobalt(CHROM, position, ratio).build();
    }

    private static ClusterVariantLeg createSVPosition(long position) {
        return ImmutableClusterVariantLeg.builder()
                .chromosome(CHROM)
                .position(position)
                .type(StructuralVariantType.BND)
                .homology("")
                .orientation((byte) 1)
                .build();
    }

    @NotNull
    private static List<CobaltRatio> cobalt(long startPosition, boolean... usable) {
        final List<CobaltRatio> result = Lists.newArrayList();
        int offset = 0;
        for (boolean isUsable : usable) {
            result.add(cobalt(startPosition + offset, isUsable));
            offset += WINDOW;
        }
        return result;
    }

    @NotNull
    private static List<ClusterVariantLeg> variants(long... positions) {
        final List<ClusterVariantLeg> result = Lists.newArrayList();
        for (long position : positions) {
            result.add(createSVPosition(position));
        }

        return result;
    }

    @NotNull
    private static List<PCFPosition> createRatioBreaks(long... positions) {
        final List<PCFPosition> result = Lists.newArrayList();
        for (long position : positions) {
            result.add(ratio(position));
        }

        return result;
    }

    @NotNull
    private static PCFPosition ratio(long position) {
        return ImmutablePCFPosition.builder().chromosome(CHROM).position(position).source(PCFSource.TUMOR_RATIO).build();
    }
}
