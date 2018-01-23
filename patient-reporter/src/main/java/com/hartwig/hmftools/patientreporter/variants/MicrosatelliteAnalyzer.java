package com.hartwig.hmftools.patientreporter.variants;

import static com.hartwig.hmftools.common.variant.EnrichedSomaticVariantFactory.getRepeatContext;
import static com.hartwig.hmftools.common.variant.EnrichedSomaticVariantFactory.relativePositionAndRef;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import com.hartwig.hmftools.common.purple.repeat.RepeatContext;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.VariantType;

import org.apache.commons.math3.util.Pair;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class },
             allParameters = true)
public abstract class MicrosatelliteAnalyzer {

    @NotNull
    public abstract IndexedFastaSequenceFile reference();

    @NotNull
    public static MicrosatelliteAnalyzer of(@NotNull final String fastaFileLocation) throws FileNotFoundException {
        return ImmutableMicrosatelliteAnalyzer.of(new IndexedFastaSequenceFile(new File(fastaFileLocation)));
    }

    public double analyzeVariants(@NotNull final List<SomaticVariant> variants) {
        double indelCount = 0;
        for (final SomaticVariant variant : variants) {
            if (isPassIndel(variant)) {
                final Pair<Integer, String> relativePositionAndRef = relativePositionAndRef(variant, reference());
                if (getRepeatContext(variant, relativePositionAndRef.getFirst(), relativePositionAndRef.getSecond()).filter(
                        this::repeatContextIsRelevant).isPresent()) {
                    indelCount++;
                }
            }
        }
        return indelCount / 3095;
    }

    private boolean isPassIndel(@NotNull final SomaticVariant variant) {
        return variant.filter().equals("PASS") && variant.type() == VariantType.INDEL && variant.ref().length() < 50
                && variant.alt().length() < 50;
    }

    private boolean repeatContextIsRelevant(@NotNull final RepeatContext repeatContext) {
        final int repeatCount = repeatContext.count();
        final int repeatSequenceLength = repeatContext.sequence().length();
        return repeatCount > 0 && ((repeatSequenceLength >= 2 && repeatSequenceLength <= 4) || (repeatSequenceLength == 1
                && repeatCount >= 5));
    }
}
