package com.hartwig.hmftools.patientreporter.variants;

import static com.hartwig.hmftools.common.variant.snpeff.AnnotationTestFactory.createVariantAnnotationBuilder;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.hartwig.hmftools.common.region.GenomeRegion;
import com.hartwig.hmftools.common.region.GenomeRegionFactory;
import com.hartwig.hmftools.common.region.hmfslicer.HmfGenomeRegion;
import com.hartwig.hmftools.common.region.hmfslicer.ImmutableHmfGenomeRegion;
import com.hartwig.hmftools.common.region.hmfslicer.Strand;
import com.hartwig.hmftools.common.slicing.Slicer;
import com.hartwig.hmftools.common.slicing.SlicerFactory;
import com.hartwig.hmftools.common.variant.ImmutableSomaticVariantImpl;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.SomaticVariantTestBuilderFactory;
import com.hartwig.hmftools.common.variant.VariantConsequence;
import com.hartwig.hmftools.common.variant.snpeff.ImmutableVariantAnnotation;
import com.hartwig.hmftools.common.variant.snpeff.VariantAnnotation;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class ConsequenceDeterminerTest {

    private static final String CHROMOSOME = "X";
    private static final long POSITION = 150L;
    private static final long WRONG_POSITION = 1500L;
    private static final String GENE = "GENE";

    private static final String TRANSCRIPT = "TRANS";
    private static final int TRANSCRIPT_VERSION = 1;

    private static final String REF = "R";
    private static final String ALT = "A";
    private static final String COSMIC_ID = "123";
    private static final int ALLELE_READ_COUNT = 1;
    private static final int TOTAL_READ_COUNT = 2;

    private static final String HGVS_CODING = "c.RtoA";
    private static final String HGVS_PROTEIN = "p.RtoA";

    private static final String CHROMOSOME_BAND = "p1";
    private static final List<Integer> ENTREZ_ID = Collections.singletonList(11);
    private static final String GENE_ID = "ENSG0000";
    private static final long GENE_START = 1;
    private static final long GENE_END = 42;

    @Test
    public void worksAsExpected() {
        final SortedSetMultimap<String, GenomeRegion> regionMap = TreeMultimap.create();
        GenomeRegion testRegion = GenomeRegionFactory.create(CHROMOSOME, POSITION - 10, POSITION + 10);
        regionMap.put(testRegion.chromosome(), testRegion);

        final Slicer slicer = SlicerFactory.fromRegions(regionMap);
        final Map<String, HmfGenomeRegion> transcriptMap = Maps.newHashMap();
        transcriptMap.put(TRANSCRIPT, hmfRegion());

        final ConsequenceDeterminer determiner = new ConsequenceDeterminer(slicer, transcriptMap);

        final VariantConsequence rightConsequence = VariantConsequence.MISSENSE_VARIANT;
        final VariantConsequence wrongConsequence = VariantConsequence.OTHER;

        final ImmutableVariantAnnotation.Builder annotationBuilder = createVariantAnnotationBuilder().featureID(TRANSCRIPT).
                featureType(ConsequenceDeterminer.FEATURE_TYPE_TRANSCRIPT).gene(GENE).hgvsCoding(HGVS_CODING).
                hgvsProtein(HGVS_PROTEIN);
        final VariantAnnotation rightAnnotation = annotationBuilder.consequences(Lists.newArrayList(rightConsequence)).build();
        final VariantAnnotation wrongAnnotation = annotationBuilder.consequences(Lists.newArrayList(wrongConsequence)).build();

        final ImmutableSomaticVariantImpl.Builder variantBuilder = SomaticVariantTestBuilderFactory.create().
                chromosome(CHROMOSOME).ref(REF).alt(ALT).cosmicID(COSMIC_ID).
                totalReadCount(TOTAL_READ_COUNT).alleleReadCount(ALLELE_READ_COUNT);

        final SomaticVariant rightVariant = variantBuilder.position(POSITION).
                annotations(Lists.newArrayList(rightAnnotation)).build();
        final SomaticVariant wrongConsequenceVariant = variantBuilder.position(POSITION).
                annotations(Lists.newArrayList(wrongAnnotation)).build();
        final SomaticVariant wrongPositionVariant = variantBuilder.position(WRONG_POSITION).
                annotations(Lists.newArrayList(rightAnnotation)).build();

        final List<VariantReport> variants =
                determiner.run(Lists.newArrayList(rightVariant, wrongConsequenceVariant, wrongPositionVariant));
        assertEquals(1, variants.size());

        final VariantReport variant = variants.get(0);
        assertEquals(GENE, variant.gene());
        assertEquals(CHROMOSOME + ":" + POSITION, variant.variant().chromosomePosition());
        assertEquals(REF, variant.variant().ref());
        assertEquals(ALT, variant.variant().alt());
        assertEquals(TRANSCRIPT + "." + TRANSCRIPT_VERSION, variant.transcript());
        assertEquals(HGVS_CODING, variant.hgvsCoding());
        assertEquals(HGVS_PROTEIN, variant.hgvsProtein());
        assertEquals(rightConsequence.readableSequenceOntologyTerm(), variant.consequence());
        assertEquals(COSMIC_ID, variant.cosmicID());
        assertEquals(TOTAL_READ_COUNT, variant.totalReadCount());
        assertEquals(ALLELE_READ_COUNT, variant.alleleReadCount());
    }

    @NotNull
    private static HmfGenomeRegion hmfRegion() {
        return ImmutableHmfGenomeRegion.builder()
                .chromosome(CHROMOSOME)
                .start(POSITION - 10)
                .end(POSITION + 10)
                .gene(GENE)
                .transcriptID(TRANSCRIPT)
                .transcriptVersion(TRANSCRIPT_VERSION)
                .chromosomeBand(CHROMOSOME_BAND)
                .entrezId(ENTREZ_ID)
                .geneID(GENE_ID)
                .geneStart(GENE_START)
                .geneEnd(GENE_END)
                .codingStart(0)
                .codingEnd(0)
                .strand(Strand.FORWARD)
                .build();
    }
}
