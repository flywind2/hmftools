package com.hartwig.hmftools.common.variant.enrich;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hartwig.hmftools.common.variant.VariantContextFromString;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import htsjdk.variant.variantcontext.VariantContext;

public class NearIndelPonEnrichmentTest {

    @Test
    public void testOverlapIndel() {
        final String ponRef = "GATTACA";
        final String variantRef = "TAT";

        final VariantContext pon = create(100, ponRef);
        // Max should be 100 + 7 - 1 + 10
        // Min should be 100 - 3 + 1 - 10

        assertOverlap(false, pon, 86, variantRef);
        assertOverlap(false, pon, 87, variantRef);
        assertOverlap(true, pon, 88, variantRef);
        assertOverlap(true, pon, 89, variantRef);

        assertOverlap(true, pon, 99, variantRef);
        assertOverlap(true, pon, 100, variantRef);
        assertOverlap(true, pon, 101, variantRef);

        assertOverlap(true, pon, 115, variantRef);
        assertOverlap(true, pon, 116, variantRef);
        assertOverlap(false, pon, 117, variantRef);
        assertOverlap(false, pon, 118, variantRef);
    }

    @Test
    public void testNormal() {
        final String ponRef = "TAT";
        final String variantRef = "TAT";

        final VariantContext pon = create(100, ponRef);
        assertOverlap(true, pon, 100, variantRef);
    }

    @Test
    public void testPonIsSNP() {
        final String ponRef = "T";
        final String variantRef = "TAT";

        final VariantContext pon = create(100, ponRef);
        assertOverlap(false, pon, 100, variantRef);
    }

    @Test
    public void testVariantIsSNP() {
        final String ponRef = "TAT";
        final String variantRef = "T";

        final VariantContext pon = create(100, ponRef);
        assertOverlap(false, pon, 100, variantRef);
    }

    @Test
    public void testIsValidMinCount() {
        VariantContext variant =  createPonEntry( "TAC", 10);
        assertTrue(NearIndelPonEnrichment.isValidPonEntry(8, "SOMATIC_PON_COUNT", variant));
        assertTrue(NearIndelPonEnrichment.isValidPonEntry(9, "SOMATIC_PON_COUNT", variant));
        assertFalse(NearIndelPonEnrichment.isValidPonEntry(10, "SOMATIC_PON_COUNT", variant));
        assertFalse(NearIndelPonEnrichment.isValidPonEntry(11, "SOMATIC_PON_COUNT", variant));
    }

    @Test
    public void testIsValidlIndel() {
        VariantContext variant =  createPonEntry( "T", 10);
        assertFalse(NearIndelPonEnrichment.isValidPonEntry(8, "SOMATIC_PON_COUNT", variant));
    }

    private void assertOverlap(boolean expected, @NotNull VariantContext pon, int variantStart, @NotNull final String variantRef) {
        final VariantContext variant = create(variantStart, variantRef);
        assertEquals(expected, NearIndelPonEnrichment.overlapsPon(pon, variant));
    }

    @NotNull
    final VariantContext create(int start, @NotNull final String ref) {

        final String line = "11\t" + start + "\tCOSM123;COSM456\t" + ref
                + "\tC\t.\tPASS\tCOSM2ENST=COSM123|GENE_TRANS1|c.1A>G|p.E1E|1,COSM456|GENE_TRANS2|c.2A>G|p.E2E|1\tGT:AD:DP\t0/1:73,17:91";
        return VariantContextFromString.decode(line);
    }

    @NotNull
    final VariantContext createPonEntry(@NotNull final String ref, int somaticPonCount) {

        final String line = "1\t16841689\t.\t"+ref+"\tG\t.\tPASS\tSOMATIC_PON_COUNT="+somaticPonCount+";SOMATIC_PON_HET=34;SOMATIC_PON_HOM=0\t";
        return VariantContextFromString.decode(line);
    }

}