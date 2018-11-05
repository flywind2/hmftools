package com.hartwig.hmftools.common.variant.hotspot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.chromosome.Chromosome;
import com.hartwig.hmftools.common.chromosome.HumanChromosome;

import org.jetbrains.annotations.NotNull;

public final class VariantHotspotFile {

    private static final String DELIMITER = "\t";

    private VariantHotspotFile() {
    }

    @NotNull
    public static Multimap<Chromosome, VariantHotspot> read(@NotNull final String fileName) throws IOException {
        return fromLines(Files.readAllLines(new File(fileName).toPath()));
    }

    @NotNull
    private static Multimap<Chromosome, VariantHotspot> fromLines(@NotNull List<String> lines) {
        Multimap<Chromosome, VariantHotspot> result = ArrayListMultimap.create();
        for (String line : lines) {
            VariantHotspot position = fromString(line);
            if (HumanChromosome.contains(position.chromosome())) {
                result.put(HumanChromosome.fromString(position.chromosome()), position);
            }
        }

        return result;
    }

    @NotNull
    private static VariantHotspot fromString(@NotNull final String line) {
        String[] values = line.split(DELIMITER);
        return ImmutableVariantHotspot.builder()
                .chromosome(values[0])
                .position(Long.valueOf(values[1]))
                .ref(values[2])
                .alt(values[3])
                .build();
    }
}
