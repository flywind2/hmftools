package com.hartwig.hmftools.strelka.mnv;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;
import com.hartwig.hmftools.strelka.mnv.scores.VariantScore;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMRecord;
import htsjdk.variant.variantcontext.VariantContext;

public final class TestUtils {

    @NotNull
    public static SAMRecord buildSamRecord(final int alignmentStart, @NotNull final String cigar, @NotNull final String readString) {
        final StringBuilder qualityString = new StringBuilder();
        for (int i = 0; i < readString.length(); i++) {
            qualityString.append("A");
        }
        return buildSamRecord(alignmentStart, cigar, readString, qualityString.toString(), false);
    }

    @NotNull
    public static SAMRecord buildSamRecord(final int alignmentStart, @NotNull final String cigar, @NotNull final String readString,
            @NotNull final String qualities, final boolean negativeStrand) {
        final SAMRecord record = new SAMRecord(null);
        record.setAlignmentStart(alignmentStart);
        record.setCigarString(cigar);
        record.setReadString(readString);
        record.setReadNegativeStrandFlag(negativeStrand);
        record.setBaseQualityString(qualities);
        record.setMappingQuality(20);
        record.setDuplicateReadFlag(false);
        record.setReadUnmappedFlag(false);
        return record;
    }

    @NotNull
    static MNVScore build2VariantScores(@NotNull final List<VariantContext> variants,
            @NotNull final List<Pair<VariantScore, VariantScore>> scores) {
        MNVScore mnvScore = MNVScore.of(variants);
        assert variants.size() == 2;
        for (final Pair<VariantScore, VariantScore> variantScorePair : scores) {
            final Map<VariantContext, VariantScore> recordScores = Maps.newHashMap();
            recordScores.put(variants.get(0), variantScorePair.getLeft());
            recordScores.put(variants.get(1), variantScorePair.getRight());
            mnvScore = MNVScore.addReadScore(mnvScore, recordScores);
        }
        return mnvScore;
    }
}
