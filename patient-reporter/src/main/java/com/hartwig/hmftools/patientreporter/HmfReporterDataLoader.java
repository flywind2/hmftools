package com.hartwig.hmftools.patientreporter;

import java.io.IOException;

import com.hartwig.hmftools.common.cosmic.census.CosmicGeneModel;
import com.hartwig.hmftools.common.cosmic.census.CosmicGenes;
import com.hartwig.hmftools.common.cosmic.fusions.CosmicFusionModel;
import com.hartwig.hmftools.common.cosmic.fusions.CosmicFusions;
import com.hartwig.hmftools.common.exception.HartwigException;
import com.hartwig.hmftools.common.gene.GeneModel;
import com.hartwig.hmftools.hmfslicer.HmfGenePanelSupplier;
import com.hartwig.hmftools.patientreporter.filters.DrupFilter;

import org.jetbrains.annotations.NotNull;

public final class HmfReporterDataLoader {
    private HmfReporterDataLoader() {
    }

    @NotNull
    public static HmfReporterData buildFromFiles(@NotNull final String drupFilterFile, @NotNull final String cosmicFile,
            @NotNull final String fusionFile) throws IOException, HartwigException {
        final GeneModel geneModel = new GeneModel(HmfGenePanelSupplier.hmfGeneMap());
        final DrupFilter drupFilter = new DrupFilter(drupFilterFile);
        final CosmicGeneModel cosmicGeneModel = CosmicGenes.buildModelFromCsv(cosmicFile);
        final CosmicFusionModel fusionModel = CosmicFusions.readFromCSV(fusionFile);
        return ImmutableHmfReporterData.of(geneModel, cosmicGeneModel, drupFilter, fusionModel);
    }
}
