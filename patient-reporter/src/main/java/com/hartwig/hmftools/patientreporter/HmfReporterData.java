package com.hartwig.hmftools.patientreporter;

import com.google.common.collect.Multimap;
import com.hartwig.hmftools.common.cosmic.CosmicGeneModel;
import com.hartwig.hmftools.common.fusions.KnownFusionsModel;
import com.hartwig.hmftools.common.gene.GeneModel;
import com.hartwig.hmftools.common.region.GenomeRegion;
import com.hartwig.hmftools.patientreporter.filters.DrupFilter;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class HmfReporterData {

    @NotNull
    public abstract GeneModel panelGeneModel();

    @NotNull
    public abstract CosmicGeneModel cosmicGeneModel();

    @NotNull
    public abstract KnownFusionsModel knownFusionsModel();

    @NotNull
    public abstract DrupFilter drupFilter();

    @NotNull
    public abstract IndexedFastaSequenceFile refGenomeFastaFile();

    @NotNull
    public abstract Multimap<String, GenomeRegion> highConfidenceRegions();
}
