package com.hartwig.hmftools.patientreporter.cfreport.chapters;

import java.util.List;

import com.hartwig.hmftools.common.purple.gene.GeneCopyNumber;
import com.hartwig.hmftools.patientreporter.AnalysedPatientReport;
import com.hartwig.hmftools.patientreporter.cfreport.ReportResources;
import com.hartwig.hmftools.patientreporter.cfreport.components.InlineBarChart;
import com.hartwig.hmftools.patientreporter.cfreport.components.TableUtil;
import com.hartwig.hmftools.patientreporter.cfreport.data.DataUtil;
import com.hartwig.hmftools.patientreporter.cfreport.data.GeneCopyNumbers;
import com.hartwig.hmftools.patientreporter.cfreport.data.GeneDisruptions;
import com.hartwig.hmftools.patientreporter.cfreport.data.GeneFusions;
import com.hartwig.hmftools.patientreporter.cfreport.data.GeneUtil;
import com.hartwig.hmftools.patientreporter.cfreport.data.SomaticVariants;
import com.hartwig.hmftools.patientreporter.structural.ReportableGeneDisruption;
import com.hartwig.hmftools.patientreporter.structural.ReportableGeneFusion;
import com.hartwig.hmftools.patientreporter.variants.ReportableVariant;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.VerticalAlignment;

import org.jetbrains.annotations.NotNull;

public class ActionableOrDriversChapter implements ReportChapter {

    @NotNull
    private final AnalysedPatientReport patientReport;

    public ActionableOrDriversChapter(@NotNull final AnalysedPatientReport patientReport) {
        this.patientReport = patientReport;
    }

    @Override
    @NotNull
    public String name() {
        return "Actionable or drivers";
    }

    @Override
    public void render(@NotNull Document reportDocument) {
        final boolean hasReliablePurityFit = patientReport.hasReliablePurityFit();

        reportDocument.add(createTumorVariantsTable(patientReport.reportableVariants(), hasReliablePurityFit));
        reportDocument.add(createGainsAndLossesTable(patientReport.geneCopyNumbers(), hasReliablePurityFit));
        reportDocument.add(createSomaticFusionsTable(patientReport.geneFusions(), hasReliablePurityFit));
        reportDocument.add(createDisruptionsTable(patientReport.geneDisruptions(), hasReliablePurityFit));
    }

    @NotNull
    private static Table createTumorVariantsTable(@NotNull List<ReportableVariant> reportableVariants, boolean hasReliablePurityFit) {
        final String title = "Tumor specific variants";
        if (reportableVariants.isEmpty()) {
            return TableUtil.createNoneReportTable(title);
        }

        Table contentTable = TableUtil.createReportContentTable(new float[] { 45, 75, 50, 60, 40, 60, 40, 50, 50, 35 },
                new Cell[] { TableUtil.getHeaderCell("Gene"), TableUtil.getHeaderCell("Variant"), TableUtil.getHeaderCell("Impact"),
                        TableUtil.getHeaderCell("Read depth").setTextAlignment(TextAlignment.CENTER), TableUtil.getHeaderCell("Hotspot"),
                        TableUtil.getHeaderCell("Ploidy (VAF)"), TableUtil.getHeaderCell(),
                        TableUtil.getHeaderCell("Clonality"), TableUtil.getHeaderCell("Biallelic"), TableUtil.getHeaderCell("Driver") });

        final List<ReportableVariant> sortedVariants = SomaticVariants.sort(reportableVariants);
        for (ReportableVariant variant : sortedVariants) {
            InlineBarChart chart = new InlineBarChart(hasReliablePurityFit ? variant.adjustedVAF() : 0, 0, 1);
            chart.enabled(hasReliablePurityFit);
            chart.setWidth(20);
            chart.setHeight(4);

            contentTable.addCell(TableUtil.getContentCell(SomaticVariants.geneDisplayString(variant)));
            contentTable.addCell(TableUtil.getContentCell(variant.hgvsCodingImpact()));
            contentTable.addCell(TableUtil.getContentCell(variant.hgvsProteinImpact()));
            contentTable.addCell(TableUtil.getContentCell(new Paragraph(
                    variant.alleleReadCount() + " / ").setFont(ReportResources.fontBold())
                    .add(new Text(String.valueOf(variant.totalReadCount())).setFont(ReportResources.fontRegular()))
                    .setTextAlignment(TextAlignment.CENTER)));
            contentTable.addCell(TableUtil.getContentCell(SomaticVariants.hotspotString(variant.hotspot())));
            contentTable.addCell(TableUtil.getContentCell(SomaticVariants.ploidyVaf(variant.adjustedCopyNumber(),
                    variant.minorAllelePloidy(),
                    variant.adjustedVAF(),
                    hasReliablePurityFit)));
            contentTable.addCell(TableUtil.getContentCell(chart).setVerticalAlignment(VerticalAlignment.MIDDLE));
            contentTable.addCell(TableUtil.getContentCell(SomaticVariants.clonalityString(variant.clonality(), hasReliablePurityFit)));
            contentTable.addCell(TableUtil.getContentCell(SomaticVariants.biallelicString(variant.biallelic(),
                    variant.driverCategory(),
                    hasReliablePurityFit)));
            contentTable.addCell(TableUtil.getContentCell(SomaticVariants.driverString(variant.driverLikelihood())));
        }

        contentTable.addCell(TableUtil.getLayoutCell(1, contentTable.getNumberOfColumns())
                .setPaddingTop(10)
                .add(new Paragraph("* Marked gene(s) are included in the DRUP study and indicate potential eligibility in "
                        + "DRUP. Please note that the marking is NOT based on the specific mutation reported for this sample, "
                        + "but only on a gene-level.").addStyle(ReportResources.subTextStyle())));

        if (SomaticVariants.hasNotifiableGermlineVariant(reportableVariants)) {
            contentTable.addCell(TableUtil.getLayoutCell(1, contentTable.getNumberOfColumns())
                    .add(new Paragraph("Marked variant(s) (#) are also present in the germline of the patient. "
                            + "Referral to a genetic specialist should be advised.").addStyle(ReportResources.subTextStyle())));
        }

        return TableUtil.createWrappingReportTable(title, contentTable);
    }

    @NotNull
    private static Table createGainsAndLossesTable(@NotNull List<GeneCopyNumber> copyNumbers, boolean hasReliablePurityFit) {
        final String title = "Tumor specific gains & losses";
        if (copyNumbers.isEmpty()) {
            return TableUtil.createNoneReportTable(title);
        }

        Table contentTable = TableUtil.createReportContentTable(new float[] { 60, 80, 100, 80, 45, 125 },
                new Cell[] { TableUtil.getHeaderCell("Chromosome"), TableUtil.getHeaderCell("Chromosome band"),
                        TableUtil.getHeaderCell("Gene"), TableUtil.getHeaderCell("Type"),
                        TableUtil.getHeaderCell("Copies").setTextAlignment(TextAlignment.RIGHT), TableUtil.getHeaderCell("")
                });

        final List<GeneCopyNumber> sortedCopyNumbers = GeneCopyNumbers.sort(copyNumbers);
        for (GeneCopyNumber copyNumber : sortedCopyNumbers) {
            Long reportableCopyNumber = Math.round(Math.max(0, copyNumber.minCopyNumber()));
            contentTable.addCell(TableUtil.getContentCell(copyNumber.chromosome()));
            contentTable.addCell(TableUtil.getContentCell(copyNumber.chromosomeBand()));
            contentTable.addCell(TableUtil.getContentCell(copyNumber.gene()));
            contentTable.addCell(TableUtil.getContentCell(GeneCopyNumbers.type(copyNumber)));
            contentTable.addCell(TableUtil.getContentCell(hasReliablePurityFit ? String.valueOf(reportableCopyNumber) : DataUtil.NA_STRING)
                    .setTextAlignment(TextAlignment.RIGHT));
            contentTable.addCell(TableUtil.getContentCell(""));
        }

        return TableUtil.createWrappingReportTable(title, contentTable);
    }

    @NotNull
    private static Table createSomaticFusionsTable(@NotNull List<ReportableGeneFusion> fusions, boolean hasReliablePurityFit) {
        final String title = "Somatic gene fusions";
        if (fusions.isEmpty()) {
            return TableUtil.createNoneReportTable(title);
        }

        Table contentTable = TableUtil.createReportContentTable(new float[] { 90, 82.5f, 82.5f, 37.5f, 37.5f, 40, 30, 100 },
                new Cell[] { TableUtil.getHeaderCell("Fusion"), TableUtil.getHeaderCell("5' Transcript"),
                        TableUtil.getHeaderCell("3' Transcript"), TableUtil.getHeaderCell("5' End"), TableUtil.getHeaderCell("3' Start"),
                        TableUtil.getHeaderCell("Copies").setTextAlignment(TextAlignment.RIGHT), TableUtil.getHeaderCell(""),
                        TableUtil.getHeaderCell("Source") });

        final List<ReportableGeneFusion> sortedFusions = GeneFusions.sort(fusions);
        for (ReportableGeneFusion fusion : sortedFusions) {
            contentTable.addCell(TableUtil.getContentCell(GeneFusions.name(fusion)));
            contentTable.addCell(TableUtil.getContentCell(fusion.geneStartTranscript()));
            contentTable.addCell(TableUtil.getContentCell(fusion.geneEndTranscript()));
            contentTable.addCell(TableUtil.getContentCell(fusion.geneContextStart()));
            contentTable.addCell(TableUtil.getContentCell(fusion.geneContextEnd()));
            contentTable.addCell(TableUtil.getContentCell(GeneUtil.ploidyToCopiesString(fusion.ploidy(), hasReliablePurityFit))
                    .setTextAlignment(TextAlignment.RIGHT));
            contentTable.addCell(TableUtil.getContentCell(""));
            contentTable.addCell(TableUtil.getContentCell(new Paragraph(fusion.source()).setAction(PdfAction.createURI(GeneFusions.sourceUrl(
                    fusion.source())))));
        }

        return TableUtil.createWrappingReportTable(title, contentTable);
    }

    @NotNull
    private static Table createDisruptionsTable(@NotNull List<ReportableGeneDisruption> disruptions, boolean hasReliablePurityFit) {
        final String title = "Tumor specific gene disruptions";
        if (disruptions.isEmpty()) {
            return TableUtil.createNoneReportTable(title);
        }

        Table contentTable = TableUtil.createReportContentTable(new float[] { 60, 80, 100, 80, 40, 65, 65 },
                new Cell[] { TableUtil.getHeaderCell("Location"), TableUtil.getHeaderCell("Gene"),
                        TableUtil.getHeaderCell("Disrupted range"), TableUtil.getHeaderCell("Type"),
                        TableUtil.getHeaderCell("Copies").setTextAlignment(TextAlignment.RIGHT),
                        TableUtil.getHeaderCell("Gene \nmin copies").setTextAlignment(TextAlignment.RIGHT),
                        TableUtil.getHeaderCell("Gene \nmax copies").setTextAlignment(TextAlignment.RIGHT) });

        final List<ReportableGeneDisruption> sortedDisruptions = GeneDisruptions.sort(disruptions);
        for (ReportableGeneDisruption disruption : sortedDisruptions) {
            contentTable.addCell(TableUtil.getContentCell(disruption.location()));
            contentTable.addCell(TableUtil.getContentCell(disruption.gene()));
            contentTable.addCell(TableUtil.getContentCell(disruption.range()));
            contentTable.addCell(TableUtil.getContentCell(disruption.type()));
            contentTable.addCell(TableUtil.getContentCell(GeneUtil.ploidyToCopiesString(disruption.ploidy(), hasReliablePurityFit))
                    .setTextAlignment(TextAlignment.RIGHT));
            contentTable.addCell(TableUtil.getContentCell(GeneDisruptions.getCopyNumberString(disruption.geneMinCopies(),
                    hasReliablePurityFit)).setTextAlignment(TextAlignment.RIGHT));
            contentTable.addCell(TableUtil.getContentCell(GeneDisruptions.getCopyNumberString(disruption.geneMaxCopies(),
                    hasReliablePurityFit)).setTextAlignment(TextAlignment.RIGHT));
        }

        return TableUtil.createWrappingReportTable(title, contentTable);
    }
}
