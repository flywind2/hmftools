package com.hartwig.hmftools.patientreporter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.actionability.EvidenceItem;
import com.hartwig.hmftools.common.chord.ChordAnalysis;
import com.hartwig.hmftools.common.chord.ChordFileReader;
import com.hartwig.hmftools.common.ecrf.projections.PatientTumorLocation;
import com.hartwig.hmftools.common.ecrf.projections.PatientTumorLocationFunctions;
import com.hartwig.hmftools.common.lims.LimsGermlineReportingChoice;
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumber;
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumberFile;
import com.hartwig.hmftools.common.purple.purity.FittedPurityFile;
import com.hartwig.hmftools.common.purple.purity.FittedPurityStatus;
import com.hartwig.hmftools.common.purple.purity.PurityContext;
import com.hartwig.hmftools.common.purple.qc.PurpleQC;
import com.hartwig.hmftools.common.purple.qc.PurpleQCFile;
import com.hartwig.hmftools.common.purple.qc.PurpleQCStatus;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.SomaticVariantFactory;
import com.hartwig.hmftools.common.variant.structural.annotation.ReportableDisruption;
import com.hartwig.hmftools.common.variant.structural.annotation.ReportableDisruptionFile;
import com.hartwig.hmftools.common.variant.structural.annotation.ReportableGeneFusion;
import com.hartwig.hmftools.common.variant.structural.annotation.ReportableGeneFusionFile;
import com.hartwig.hmftools.patientreporter.actionability.ClinicalTrialFactory;
import com.hartwig.hmftools.patientreporter.actionability.ReportableEvidenceItemFactory;
import com.hartwig.hmftools.patientreporter.copynumber.CopyNumberAnalysis;
import com.hartwig.hmftools.patientreporter.copynumber.CopyNumberAnalyzer;
import com.hartwig.hmftools.patientreporter.copynumber.HomozygousDisruptionAnalyzer;
import com.hartwig.hmftools.patientreporter.structural.ReportableDriverCatalog;
import com.hartwig.hmftools.patientreporter.structural.SvAnalysis;
import com.hartwig.hmftools.patientreporter.structural.SvAnalyzer;
import com.hartwig.hmftools.common.variant.ReportableVariant;
import com.hartwig.hmftools.patientreporter.variants.ReportVariantAnalysis;
import com.hartwig.hmftools.patientreporter.variants.ReportableVariantAnalyzer;
import com.hartwig.hmftools.patientreporter.variants.germline.BachelorFile;
import com.hartwig.hmftools.patientreporter.variants.germline.FilterGermlineVariants;
import com.hartwig.hmftools.patientreporter.variants.germline.GermlineVariant;
import com.hartwig.hmftools.patientreporter.variants.germline.InterpretGermlineVariant;
import com.hartwig.hmftools.patientreporter.variants.somatic.SomaticVariantAnalysis;
import com.hartwig.hmftools.patientreporter.variants.somatic.SomaticVariantAnalyzer;
import com.hartwig.hmftools.patientreporter.viralInsertion.InterpretViralInsertion;
import com.hartwig.hmftools.patientreporter.viralInsertion.ViralInsertion;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AnalysedPatientReporter {

    private static final Logger LOGGER = LogManager.getLogger(AnalysedPatientReporter.class);

    @NotNull
    private final AnalysedReportData reportData;

    AnalysedPatientReporter(@NotNull final AnalysedReportData reportData) {
        this.reportData = reportData;
    }

    @NotNull
    AnalysedPatientReport run(@NotNull SampleMetadata sampleMetadata, @NotNull String purplePurityTsv, @NotNull String purpleGeneCnvTsv,
            @NotNull String somaticVariantVcf, @NotNull String linxFusionTsv, @NotNull String linxDisruptionTsv,
            @NotNull String bachelorTSV, @NotNull String chordPredictionTxt, @NotNull String circosFile,
            @NotNull String linxViralInsertionTsv, @NotNull String linxDriversCatalogTsv, @NotNull String reportDatesTsv, @NotNull String purpleQCFile,
            @Nullable String comments, boolean correctedReport) throws IOException {
        PatientTumorLocation patientTumorLocation =
                PatientTumorLocationFunctions.findPatientTumorLocationForSample(reportData.patientTumorLocations(),
                        sampleMetadata.tumorSampleId());

        SampleReport sampleReport = SampleReportFactory.fromLimsAndHospitalModel(sampleMetadata,
                reportData.limsModel(),
                reportData.hospitalModel(),
                patientTumorLocation);

        List<ReportableDriverCatalog> reportableDriverCatalogs = analyzeDriverCatalog(linxDriversCatalogTsv);

        CopyNumberAnalysis copyNumberAnalysis = analyzeCopyNumbers(purplePurityTsv, purpleGeneCnvTsv, patientTumorLocation);
        SomaticVariantAnalysis somaticVariantAnalysis =
                analyzeSomaticVariants(sampleMetadata.tumorSampleId(), somaticVariantVcf, copyNumberAnalysis.exomeGeneCopyNumbers());

        ChordAnalysis chordAnalysis = analyzeChord(chordPredictionTxt);
        List<InterpretGermlineVariant> germlineVariantsToReport = analyzeGermlineVariants(sampleMetadata.tumorSampleBarcode(),
                bachelorTSV,
                copyNumberAnalysis,
                somaticVariantAnalysis,
                chordAnalysis);

        ReportVariantAnalysis reportableVariantsAnalysis =
                ReportableVariantAnalyzer.mergeSomaticAndGermlineVariants(somaticVariantAnalysis.variantsToReport(),
                        somaticVariantAnalysis.driverCatalog(),
                        reportData.driverGeneView(),
                        germlineVariantsToReport,
                        reportData.germlineReportingModel(),
                        reportData.limsModel().germlineReportingChoice(sampleMetadata.tumorSampleBarcode()),
                        reportData.actionabilityAnalyzer(),
                        patientTumorLocation);

        SvAnalysis svAnalysis = analyzeStructuralVariants(linxFusionTsv, linxDisruptionTsv, patientTumorLocation);
        List<ViralInsertion> viralInsertions = analyzeViralInsertions(linxViralInsertionTsv);

        String clinicalSummary = reportData.summaryModel().findSummaryForSample(sampleMetadata.tumorSampleId());

        List<EvidenceItem> allEvidenceItems = Lists.newArrayList();
        allEvidenceItems.addAll(reportableVariantsAnalysis.evidenceItems());
        allEvidenceItems.addAll(copyNumberAnalysis.evidenceItems());
        allEvidenceItems.addAll(svAnalysis.evidenceItems());

        List<EvidenceItem> nonTrials = ReportableEvidenceItemFactory.extractNonTrials(allEvidenceItems);
        AnalysedPatientReport report = ImmutableAnalysedPatientReport.builder()
                .sampleReport(sampleReport)
                .hasReliablePurityFit(copyNumberAnalysis.hasReliablePurityFit())
                .impliedPurity(copyNumberAnalysis.purity())
                .averageTumorPloidy(copyNumberAnalysis.ploidy())
                .clinicalSummary(clinicalSummary)
                .tumorSpecificEvidence(nonTrials.stream().filter(EvidenceItem::isOnLabel).collect(Collectors.toList()))
                .clinicalTrials(ClinicalTrialFactory.extractOnLabelTrials(allEvidenceItems))
                .offLabelEvidence(nonTrials.stream().filter(item -> !item.isOnLabel()).collect(Collectors.toList()))
                .reportableVariants(reportableVariantsAnalysis.variantsToReport())
                .microsatelliteIndelsPerMb(somaticVariantAnalysis.microsatelliteIndelsPerMb())
                .tumorMutationalLoad(somaticVariantAnalysis.tumorMutationalLoad())
                .tumorMutationalBurden(somaticVariantAnalysis.tumorMutationalBurden())
                .chordAnalysis(chordAnalysis)
                .gainsAndLosses(copyNumberAnalysis.reportableGainsAndLosses())
                .geneFusions(svAnalysis.reportableFusions())
                .geneDisruptions(svAnalysis.reportableDisruptions())
                .reportableDriverCatalogs(reportableDriverCatalogs)
                .viralInsertion(viralInsertions)
                .circosPath(circosFile)
                .comments(Optional.ofNullable(comments))
                .isCorrectedReport(correctedReport)
                .signaturePath(reportData.signaturePath())
                .logoRVAPath(reportData.logoRVAPath())
                .logoCompanyPath(reportData.logoCompanyPath())
                .build();

        printReportState(report);
        generateOutputReportDates(reportDatesTsv, purplePurityTsv, sampleReport.sampleMetadata().tumorSampleId(), purpleQCFile);

        return report;
    }

    private static void generateOutputReportDates(@NotNull String reportDatesTsv, @NotNull String purplePurityTsv, @NotNull String sampleId,
            @NotNull String purpleQCFile) throws IOException {

        LOGGER.info("Writing report date to tsv file");
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
        String reportDate = formatter.format(new Date());

        PurityContext purityContext = FittedPurityFile.read(purplePurityTsv);
        final PurpleQC purpleQC = PurpleQCFile.read(purpleQCFile);


        String purity = new DecimalFormat("#'%'").format(purityContext.bestFit().purity() * 100);
        FittedPurityStatus status = purityContext.status();
        PurpleQCStatus qcStatus = purpleQC.status();

        String stringForFile =
                "\t" + sampleId + "\t" + reportDate + "\t" + "sequence report" + "\t" + purity + "\t" + status + "\t" + qcStatus + "\n";

        BufferedWriter writer = new BufferedWriter(new FileWriter(reportDatesTsv, true));
        writer.write(stringForFile);
        writer.close();
    }

    @NotNull
    public List<ReportableDriverCatalog> analyzeDriverCatalog(@NotNull String linxDriversCatalogTsv) throws IOException {
        return HomozygousDisruptionAnalyzer.interpetDriverCatalog(linxDriversCatalogTsv);
    }

    @NotNull
    public List<ViralInsertion> analyzeViralInsertions(@NotNull String linxViralInsertionTsv) throws IOException {
        return InterpretViralInsertion.interpretVirals(linxViralInsertionTsv);

    }

    @NotNull
    private CopyNumberAnalysis analyzeCopyNumbers(@NotNull String purplePurityTsv, @NotNull String purpleGeneCnvTsv,
            @Nullable PatientTumorLocation patientTumorLocation) throws IOException {
        PurityContext purityContext = FittedPurityFile.read(purplePurityTsv);

        LOGGER.info("Loaded purple sample data from {}", purplePurityTsv);
        LOGGER.info(" Purple purity: {}", new DecimalFormat("#'%'").format(purityContext.bestFit().purity() * 100));
        LOGGER.info(" Purple average tumor ploidy: {}", purityContext.bestFit().ploidy());
        LOGGER.info(" Purple status: {}", purityContext.status());
        LOGGER.info(" WGD happened: {}", purityContext.wholeGenomeDuplication() ? "yes" : "no");

        List<GeneCopyNumber> exomeGeneCopyNumbers = GeneCopyNumberFile.read(purpleGeneCnvTsv);
        LOGGER.info("Loaded {} gene copy numbers from {}", exomeGeneCopyNumbers.size(), purpleGeneCnvTsv);

        return CopyNumberAnalyzer.run(purityContext, exomeGeneCopyNumbers, reportData.actionabilityAnalyzer(), patientTumorLocation);
    }

    @NotNull
    private SomaticVariantAnalysis analyzeSomaticVariants(@NotNull String sample, @NotNull String somaticVariantVcf,
            @NotNull List<GeneCopyNumber> exomeGeneCopyNumbers) throws IOException {

        final List<SomaticVariant> variants = SomaticVariantFactory.passOnlyInstance().fromVCFFile(sample, somaticVariantVcf);
        LOGGER.info("Loaded {} PASS somatic variants from {}", variants.size(), somaticVariantVcf);

        return SomaticVariantAnalyzer.run(variants, reportData.driverGeneView(), exomeGeneCopyNumbers);
    }

    @NotNull
    private List<InterpretGermlineVariant> analyzeGermlineVariants(@NotNull String sampleBarcode, @NotNull String bachelorTSV,
            @NotNull CopyNumberAnalysis copyNumberAnalysis, @NotNull SomaticVariantAnalysis somaticVariantAnalysis,
            @NotNull ChordAnalysis chordAnalysis) throws IOException {

        List<GermlineVariant> variants =
                BachelorFile.loadBachelorCsv(bachelorTSV).stream().filter(GermlineVariant::passFilter).collect(Collectors.toList());
        LOGGER.info("Loaded {} PASS germline variants from {}", variants.size(), bachelorTSV);

        LimsGermlineReportingChoice germlineChoice = reportData.limsModel().germlineReportingChoice(sampleBarcode);
        if (germlineChoice == LimsGermlineReportingChoice.UNKNOWN) {
            LOGGER.info(" No germline reporting choice known. No germline variants will be reported!");
            return Lists.newArrayList();
        } else {
            LOGGER.info(" Patient has given the following germline consent: {}", germlineChoice);
            return FilterGermlineVariants.filterGermlineVariantsForReporting(variants,
                    reportData.driverGeneView(),
                    reportData.germlineReportingModel(),
                    copyNumberAnalysis.exomeGeneCopyNumbers(),
                    somaticVariantAnalysis.variantsToReport(),
                    chordAnalysis);
        }
    }

    @NotNull
    private SvAnalysis analyzeStructuralVariants(@NotNull String linxFusionTsv, @NotNull String linxDisruptionTsv,
            @Nullable PatientTumorLocation patientTumorLocation) throws IOException {
        List<ReportableGeneFusion> fusions = ReportableGeneFusionFile.read(linxFusionTsv);
        LOGGER.info("Loaded {} fusions from {}", fusions.size(), linxFusionTsv);

        List<ReportableDisruption> disruptions = ReportableDisruptionFile.read(linxDisruptionTsv);
        LOGGER.info("Loaded {} disruptions from {}", disruptions.size(), linxDisruptionTsv);

        return SvAnalyzer.run(fusions, disruptions, reportData.actionabilityAnalyzer(), patientTumorLocation);
    }

    @NotNull
    private static ChordAnalysis analyzeChord(@NotNull String chordPredictionTxt) throws IOException {
        ChordAnalysis chord = ChordFileReader.read(chordPredictionTxt);
        LOGGER.info("Loaded CHORD analysis from {}", chordPredictionTxt);
        return chord;
    }

    private static void printReportState(@NotNull AnalysedPatientReport report) {
        LocalDate tumorArrivalDate = report.sampleReport().tumorArrivalDate();
        String formattedTumorArrivalDate =
                tumorArrivalDate != null ? DateTimeFormatter.ofPattern("dd-MMM-yyyy").format(tumorArrivalDate) : "N/A";

        LOGGER.info("Printing clinical and laboratory data for {}", report.sampleReport().tumorSampleId());
        LOGGER.info(" Tumor sample arrived at HMF on {}", formattedTumorArrivalDate);
        LOGGER.info(" Primary tumor location: {}{}",
                report.sampleReport().primaryTumorLocationString(),
                !report.sampleReport().cancerSubTypeString().isEmpty()
                        ? " (" + report.sampleReport().cancerSubTypeString() + ")"
                        : Strings.EMPTY);
        LOGGER.info(" Shallow seq purity: {}", report.sampleReport().purityShallowSeq());
        LOGGER.info(" Lab SOPs used: {}", report.sampleReport().labProcedures());
        LOGGER.info(" Clinical summary present: {}", (!report.clinicalSummary().isEmpty() ? "yes" : "no"));

        List<ReportableVariant> variantsWithNotify =
                report.reportableVariants().stream().filter(ReportableVariant::notifyClinicalGeneticist).collect(Collectors.toList());
        LOGGER.info("Printing genomic analysis results for {}:", report.sampleReport().tumorSampleId());
        LOGGER.info(" Somatic variants to report: {}", report.reportableVariants().size());
        LOGGER.info("  Variants for which to notify clinical geneticist: {}", variantsWithNotify.size());
        LOGGER.info(" Microsatellite Indels per Mb: {}", report.microsatelliteIndelsPerMb());
        LOGGER.info(" Tumor mutational load: {}", report.tumorMutationalLoad());
        LOGGER.info(" Tumor mutational burden: {}", report.tumorMutationalBurden());
        LOGGER.info(" CHORD analysis HRD prediction: {}", report.chordAnalysis().hrdValue());
        LOGGER.info(" Number of gains and losses to report: {}", report.gainsAndLosses().size());
        LOGGER.info(" Gene fusions to report : {}", report.geneFusions().size());
        LOGGER.info(" Gene disruptions to report : {}", report.geneDisruptions().size());

        LOGGER.info("Printing actionability results for {}", report.sampleReport().tumorSampleId());
        LOGGER.info(" Tumor-specific evidence items found: {}", report.tumorSpecificEvidence().size());
        LOGGER.info(" Off-label evidence items found: {}", report.offLabelEvidence().size());
        LOGGER.info(" Clinical trials matched to molecular profile: {}", report.clinicalTrials().size());
    }
}
