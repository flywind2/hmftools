package com.hartwig.hmftools.patientreporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import com.hartwig.hmftools.common.cosmic.CosmicGeneModel;
import com.hartwig.hmftools.common.cosmic.CosmicGenes;
import com.hartwig.hmftools.common.fusions.KnownFusionsModel;
import com.hartwig.hmftools.common.genepanel.HmfGenePanelSupplier;
import com.hartwig.hmftools.common.region.BEDFileLoader;
import com.hartwig.hmftools.patientreporter.algo.GeneModel;
import com.hartwig.hmftools.patientreporter.filters.DrupFilter;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.reference.IndexedFastaSequenceFile;

final class SequencedReportDataLoader {

    private SequencedReportDataLoader() {
    }

    @NotNull
    static SequencedReportData buildFromFiles(@NotNull String cosmicGeneFile, @NotNull String fusionPairsLocation,
            @NotNull String promiscuousFiveLocation, @NotNull String promiscuousThreeLocation, @NotNull String drupFilterFile,
            @NotNull String fastaFileLocation, @NotNull String highConfidenceBed) throws IOException {
        final GeneModel panelGeneModel = new GeneModel(HmfGenePanelSupplier.hmfPanelGeneList());
        final CosmicGeneModel cosmicGeneModel = CosmicGenes.readFromCSV(cosmicGeneFile);
        final KnownFusionsModel knownFusionsModel = KnownFusionsModel.fromInputStreams(new FileInputStream(fusionPairsLocation),
                new FileInputStream(promiscuousFiveLocation),
                new FileInputStream(promiscuousThreeLocation));
        final DrupFilter drupFilter = new DrupFilter(drupFilterFile);

        return ImmutableSequencedReportData.of(panelGeneModel,
                cosmicGeneModel,
                knownFusionsModel,
                drupFilter,
                new IndexedFastaSequenceFile(new File(fastaFileLocation)),
                BEDFileLoader.fromBedFile(highConfidenceBed));
    }
}