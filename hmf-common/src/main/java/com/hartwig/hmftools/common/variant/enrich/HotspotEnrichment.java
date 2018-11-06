package com.hartwig.hmftools.common.variant.enrich;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.chromosome.Chromosome;
import com.hartwig.hmftools.common.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.variant.Hotspot;
import com.hartwig.hmftools.common.variant.ImmutableSomaticVariantImpl;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspotFile;

import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;

public class HotspotEnrichment implements SomaticEnrichment {

    private static final int DISTANCE = 5;
    private static final String HOTSPOT_TAG = "HOTSPOT";

    private final Multimap<Chromosome, VariantHotspot> hotspots;

    @NotNull
    public static HotspotEnrichment fromHotspotsFile(@NotNull String hotspotsFile) throws IOException {
        return new HotspotEnrichment(VariantHotspotFile.read(hotspotsFile));
    }

    @VisibleForTesting
    HotspotEnrichment(@NotNull final Multimap<Chromosome, VariantHotspot> hotspots) {
        this.hotspots = hotspots;
    }

    @NotNull
    @Override
    public ImmutableSomaticVariantImpl.Builder enrich(@NotNull final ImmutableSomaticVariantImpl.Builder builder,
            @NotNull final VariantContext context) {
        if (context.hasAttribute(HOTSPOT_TAG)) {
            return builder.hotspot(Hotspot.HOTSPOT);
        }

        if (HumanChromosome.contains(context.getContig())) {
            final Chromosome chromosome = HumanChromosome.fromString(context.getContig());
            if (hotspots.containsKey(chromosome)) {
                final Collection<VariantHotspot> chromosomeHotspots = hotspots.get(chromosome);

                if (chromosomeHotspots.stream().anyMatch(x -> exactMatch(x, context))) {
                    return builder.hotspot(Hotspot.HOTSPOT);
                }

                if (chromosomeHotspots.stream().anyMatch(x -> overlaps(x, context))) {
                    return builder.hotspot(Hotspot.NEAR_HOTSPOT);
                }
            }
        }

        return builder.hotspot(Hotspot.NON_HOTSPOT);
    }

    private static boolean overlaps(@NotNull final VariantHotspot hotspot, @NotNull final VariantContext variant) {
        int variantStart = variant.getStart();
        int variantEnd = variant.getStart() + variant.getReference().length() - 1 + DISTANCE;

        long ponStart = hotspot.position();
        long ponEnd = hotspot.position() + hotspot.ref().length() - 1 + DISTANCE;

        return variantStart <= ponEnd && variantEnd >= ponStart;
    }

    private static boolean exactMatch(@NotNull final VariantHotspot hotspot, @NotNull final VariantContext variant) {
        return hotspot.position() == variant.getStart() && hotspot.ref().equals(variant.getReference().getBaseString())
                && variant.getAlternateAlleles().stream().map(Allele::getBaseString).collect(Collectors.toList()).contains(hotspot.alt());
    }
}
