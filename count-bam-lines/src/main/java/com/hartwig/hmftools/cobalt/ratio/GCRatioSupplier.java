package com.hartwig.hmftools.cobalt.ratio;

import java.util.Optional;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.chromosome.Chromosome;
import com.hartwig.hmftools.common.cobalt.CobaltPosition;
import com.hartwig.hmftools.common.cobalt.ReadRatio;
import com.hartwig.hmftools.common.gc.GCMedianReadCount;
import com.hartwig.hmftools.common.gc.GCProfile;
import com.hartwig.hmftools.common.region.GenomeRegionSelector;
import com.hartwig.hmftools.common.region.GenomeRegionSelectorFactory;

class GCRatioSupplier {

    private final GCMedianReadCount tumorGCMedianReadCount;
    private final ListMultimap<String, ReadRatio> tumorRatios;
    private final GCMedianReadCount referenceGCMedianReadCount;
    private final ListMultimap<String, ReadRatio> referenceRatios;

    GCRatioSupplier(final Multimap<String, GCProfile> gcProfiles, final Multimap<Chromosome, ? extends CobaltPosition> reference) {

        final GenomeRegionSelector<GCProfile> gcProfileSelector = GenomeRegionSelectorFactory.create(gcProfiles);

        final GCRatioNormalization tumorRatiosBuilder = new GCRatioNormalization();
        final GCRatioNormalization referenceRatiosBuilder = new GCRatioNormalization();

        for (Chromosome chromosome : reference.keySet()) {
            for (CobaltPosition cobaltPosition : reference.get(chromosome)) {
                final Optional<GCProfile> optionalGCProfile = gcProfileSelector.select(cobaltPosition);
                if (optionalGCProfile.isPresent()) {
                    final GCProfile gcProfile = optionalGCProfile.get();
                    referenceRatiosBuilder.addPosition(chromosome, gcProfile, cobaltPosition.referenceReadCount());
                    tumorRatiosBuilder.addPosition(chromosome, gcProfile, cobaltPosition.tumorReadCount());
                }
            }
        }

        referenceGCMedianReadCount = referenceRatiosBuilder.gcMedianReadCount();
        referenceRatios = referenceRatiosBuilder.build(referenceGCMedianReadCount);

        tumorGCMedianReadCount = tumorRatiosBuilder.gcMedianReadCount();
        tumorRatios = tumorRatiosBuilder.build(tumorGCMedianReadCount);
    }

    ListMultimap<String, ReadRatio> referenceRatios() {
        return referenceRatios;
    }

    GCMedianReadCount referenceGCMedianReadCount() {
        return referenceGCMedianReadCount;
    }

    ListMultimap<String, ReadRatio> tumorRatios() {
        return tumorRatios;
    }

    GCMedianReadCount tumorGCMedianReadCount() {
        return tumorGCMedianReadCount;
    }

}
