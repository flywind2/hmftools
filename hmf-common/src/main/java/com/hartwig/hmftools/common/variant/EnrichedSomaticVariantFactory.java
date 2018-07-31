package com.hartwig.hmftools.common.variant;

import static com.hartwig.hmftools.common.variant.ImmutableEnrichedSomaticVariant.Builder;
import static com.hartwig.hmftools.common.variant.ImmutableEnrichedSomaticVariant.builder;

import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.gene.CanonicalTranscript;
import com.hartwig.hmftools.common.purple.repeat.RepeatContext;
import com.hartwig.hmftools.common.purple.repeat.RepeatContextFactory;
import com.hartwig.hmftools.common.region.GenomeRegion;
import com.hartwig.hmftools.common.region.GenomeRegionSelector;
import com.hartwig.hmftools.common.region.GenomeRegionSelectorFactory;
import com.hartwig.hmftools.common.variant.cosmic.CosmicAnnotation;
import com.hartwig.hmftools.common.variant.snpeff.SnpEffAnnotation;

import org.apache.commons.math3.util.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.reference.ReferenceSequence;

public class EnrichedSomaticVariantFactory {

    private static final Logger LOGGER = LogManager.getLogger(EnrichedSomaticVariantFactory.class);

    @NotNull
    private final GenomeRegionSelector<GenomeRegion> highConfidenceSelector;
    @NotNull
    private final IndexedFastaSequenceFile reference;
    @NotNull
    private final ClonalityFactory clonalityFactory;
    @NotNull
    private final TranscriptAnnotationSelector transcriptAnnotationSelector;

    public EnrichedSomaticVariantFactory(@NotNull final Multimap<String, GenomeRegion> highConfidenceRegions,
            @NotNull final IndexedFastaSequenceFile reference, @NotNull final ClonalityFactory clonalityFactory,
            @NotNull final List<CanonicalTranscript> canonicalTranscripts) {
        this.highConfidenceSelector = GenomeRegionSelectorFactory.create(highConfidenceRegions);
        this.reference = reference;
        this.clonalityFactory = clonalityFactory;
        this.transcriptAnnotationSelector = new TranscriptAnnotationSelector(canonicalTranscripts);
    }

    @NotNull
    public List<EnrichedSomaticVariant> enrich(@NotNull List<PurityAdjustedSomaticVariant> variants) {
        final List<EnrichedSomaticVariant> result = Lists.newArrayList();

        for (PurityAdjustedSomaticVariant variant : variants) {
            result.add(enrich(variant));
        }

        return result;
    }

    @NotNull
    private EnrichedSomaticVariant enrich(@NotNull PurityAdjustedSomaticVariant variant) {
        final Builder builder = createBuilder(variant);

        highConfidenceSelector.select(variant).ifPresent(x -> inHighConfidenceRegion(builder));
        addTrinucleotideContext(builder, variant, reference);
        addGenomeContext(builder, variant, reference);
        addCanonicalEffect(builder, variant, transcriptAnnotationSelector);
        addCanonicalCosmicID(builder, variant, transcriptAnnotationSelector);
        builder.clonality(clonalityFactory.fromSample(variant));

        return builder.build();
    }

    @NotNull
    private static Builder createBuilder(@NotNull final SomaticVariant variant) {
        return builder().from(variant)
                .trinucleotideContext(Strings.EMPTY)
                .microhomology(Strings.EMPTY)
                .repeatCount(0)
                .repeatSequence(Strings.EMPTY)
                .highConfidenceRegion(false)
                .clonality(Clonality.UNKNOWN);
    }

    private static void addCanonicalEffect(@NotNull final Builder builder, @NotNull final SomaticVariant variant,
            @NotNull TranscriptAnnotationSelector selector) {
        final Optional<SnpEffAnnotation> canonicalSnpEffAnnotation = selector.canonical(variant.gene(), variant.snpEffAnnotations());
        if (canonicalSnpEffAnnotation.isPresent()) {
            final SnpEffAnnotation annotation = canonicalSnpEffAnnotation.get();
            builder.canonicalEffect(annotation.consequenceString());
            builder.canonicalCodingEffect(CodingEffect.effect(annotation.consequences()));
        } else {
            builder.canonicalEffect(Strings.EMPTY);
            builder.canonicalCodingEffect(CodingEffect.UNDEFINED);
        }
    }

    @VisibleForTesting
    static void addCanonicalCosmicID(@NotNull final Builder builder, @NotNull final SomaticVariant variant,
            @NotNull TranscriptAnnotationSelector selector) {
        final Optional<CosmicAnnotation> canonicalCosmicAnnotation = selector.canonical(variant.gene(), variant.cosmicAnnotations());
        if (canonicalCosmicAnnotation.isPresent()) {
            final CosmicAnnotation annotation = canonicalCosmicAnnotation.get();
            builder.canonicalCosmicID(annotation.id());
        } // KODU: Fallback to standard COSMIC ID if there are no COSMIC annotations. Can be removed once all runs are on pipeline v4.
        else if (variant.isCOSMIC()) {
            builder.canonicalCosmicID(variant.cosmicIDs().get(0));
        }
    }

    private static void addGenomeContext(@NotNull final Builder builder, @NotNull final SomaticVariant variant,
            @NotNull IndexedFastaSequenceFile reference) {
        final Pair<Integer, String> relativePositionAndRef = relativePositionAndRef(variant, reference);
        final Integer relativePosition = relativePositionAndRef.getFirst();
        final String sequence = relativePositionAndRef.getSecond();
        if (variant.type().equals(VariantType.INDEL)) {
            builder.microhomology(Microhomology.microhomology(relativePosition, sequence, variant.ref()));
        }
        getRepeatContext(variant, relativePosition, sequence).ifPresent(x -> builder.repeatSequence(x.sequence()).repeatCount(x.count()));
    }

    @NotNull
    public static Pair<Integer, String> relativePositionAndRef(@NotNull final SomaticVariant variant,
            @NotNull final IndexedFastaSequenceFile reference) {
        final int chromosomeLength = reference.getSequenceDictionary().getSequence(variant.chromosome()).getSequenceLength();
        long positionBeforeEvent = variant.position();
        long start = Math.max(positionBeforeEvent - 100, 1);
        long end = Math.min(positionBeforeEvent + 100, chromosomeLength - 1);
        int relativePosition = (int) (positionBeforeEvent - start);
        final String sequence;
        if (start < chromosomeLength && end < chromosomeLength) {
            sequence = reference.getSubsequenceAt(variant.chromosome(), start, end).getBaseString();
        } else {
            sequence = Strings.EMPTY;
            LOGGER.warn("Requested base sequence outside of chromosome region!");
        }
        return new Pair<>(relativePosition, sequence);
    }

    @NotNull
    public static Optional<RepeatContext> getRepeatContext(@NotNull final SomaticVariant variant, int relativePosition,
            @NotNull String sequence) {
        if (variant.type().equals(VariantType.INDEL)) {
            return RepeatContextFactory.repeats(relativePosition + 1, sequence);
        } else if (variant.type().equals(VariantType.SNP)) {
            Optional<RepeatContext> priorRepeat = RepeatContextFactory.repeats(relativePosition - 1, sequence);
            Optional<RepeatContext> postRepeat = RepeatContextFactory.repeats(relativePosition + 1, sequence);
            return max(priorRepeat, postRepeat);
        } else {
            return Optional.empty();
        }
    }

    @NotNull
    private static Optional<RepeatContext> max(@NotNull final Optional<RepeatContext> optionalPrior,
            @NotNull final Optional<RepeatContext> optionalPost) {
        if (!optionalPrior.isPresent()) {
            return optionalPost;
        }

        if (!optionalPost.isPresent()) {
            return optionalPrior;
        }

        final RepeatContext prior = optionalPrior.get();
        final RepeatContext post = optionalPost.get();

        if (post.sequence().length() > prior.sequence().length()) {
            return optionalPost;
        } else if (post.sequence().length() == prior.sequence().length() && post.count() > prior.count()) {
            return optionalPost;
        }

        return optionalPrior;
    }

    private static void addTrinucleotideContext(@NotNull final Builder builder, @NotNull final SomaticVariant variant,
            @NotNull IndexedFastaSequenceFile reference) {
        final int chromosomeLength = reference.getSequenceDictionary().getSequence(variant.chromosome()).getSequenceLength();
        if (variant.position() < chromosomeLength) {
            final ReferenceSequence sequence =
                    reference.getSubsequenceAt(variant.chromosome(), Math.max(1, variant.position() - 1), variant.position() + 1);
            builder.trinucleotideContext(sequence.getBaseString());
        } else {
            LOGGER.warn("Requested ref sequence beyond contig length! variant = " + variant);
        }
    }

    @NotNull
    private static Builder inHighConfidenceRegion(@NotNull final Builder builder) {
        return builder.highConfidenceRegion(true);
    }
}
