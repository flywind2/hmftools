package com.hartwig.hmftools.linx.fusion;

import static java.lang.Math.abs;

import static com.hartwig.hmftools.common.variant.structural.annotation.GeneFusion.REPORTABLE_TYPE_KNOWN;
import static com.hartwig.hmftools.linx.chaining.LinkFinder.getMinTemplatedInsertionLength;
import static com.hartwig.hmftools.linx.fusion.DisruptionFinder.DRUP_TSG_GENES_FILE;
import static com.hartwig.hmftools.linx.fusion.DisruptionFinder.markNonDisruptiveTranscripts;
import static com.hartwig.hmftools.linx.fusion.FusionFinder.couldBeReportable;
import static com.hartwig.hmftools.linx.fusion.FusionFinder.determineReportableFusion;
import static com.hartwig.hmftools.linx.fusion.FusionFinder.validFusionTranscript;
import static com.hartwig.hmftools.linx.fusion.KnownFusionData.FIVE_GENE;
import static com.hartwig.hmftools.linx.fusion.KnownFusionData.THREE_GENE;
import static com.hartwig.hmftools.linx.gene.SvGeneTranscriptCollection.PRE_GENE_PROMOTOR_DISTANCE;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_END;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_START;
import static com.hartwig.hmftools.linx.types.SvVarData.isStart;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.common.variant.structural.annotation.EnsemblGeneData;
import com.hartwig.hmftools.common.variant.structural.annotation.ExonData;
import com.hartwig.hmftools.common.variant.structural.annotation.FusionAnnotations;
import com.hartwig.hmftools.common.variant.structural.annotation.FusionChainInfo;
import com.hartwig.hmftools.common.variant.structural.annotation.FusionTermination;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneAnnotation;
import com.hartwig.hmftools.common.variant.structural.annotation.GeneFusion;
import com.hartwig.hmftools.common.variant.structural.annotation.ImmutableFusionAnnotations;
import com.hartwig.hmftools.common.variant.structural.annotation.ImmutableFusionChainInfo;
import com.hartwig.hmftools.common.variant.structural.annotation.ImmutableFusionTermination;
import com.hartwig.hmftools.common.variant.structural.annotation.Transcript;
import com.hartwig.hmftools.common.variant.structural.annotation.TranscriptData;
import com.hartwig.hmftools.linx.gene.SvGeneTranscriptCollection;
import com.hartwig.hmftools.linx.rna.RnaFusionMapper;
import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.chaining.SvChain;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.types.SvLinkedPair;
import com.hartwig.hmftools.linx.types.SvVarData;
import com.hartwig.hmftools.linx.LinxConfig;
import com.hartwig.hmftools.linx.visualiser.file.VisualiserWriter;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;
import com.hartwig.hmftools.patientdb.dao.StructuralVariantFusionDAO;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FusionDisruptionAnalyser
{
    private FusionFinder mFusionFinder;
    private DisruptionFinder mDisruptionFinder;
    private FusionWriter mFusionWriter;

    private String mSampleId;
    private String mOutputDir;
    private SvGeneTranscriptCollection mGeneTransCollection;
    private Map<String, List<SvBreakend>> mChrBreakendMap;
    private List<String> mKnownFusionGenes;
    private LinxConfig mConfig;

    private boolean mSkipFusionCheck;
    private boolean mLogReportableOnly;
    private boolean mLogAllPotentials;
    private boolean mSkipUnphasedFusions;
    private List<GeneFusion> mFusions;

    private List<Transcript> mDisruptions;

    private PerformanceCounter mPerfCounter;

    private RnaFusionMapper mRnaFusionMapper;
    private VisualiserWriter mVisWriter;

    private List<String> mRestrictedGenes;

    public static final String SAMPLE_RNA_FILE = "sample_rna_file";
    public static final String SKIP_FUSION_OUTPUT = "skip_fusion_output";
    public static final String PRE_GENE_BREAKEND_DISTANCE = "fusion_gene_distance";
    public static final String RESTRICTED_GENE_LIST = "restricted_fusion_genes";
    public static final String LOG_REPORTABLE_ONLY = "log_reportable_fusions";
    public static final String LOG_ALL_POTENTIALS = "log_potential_fusions";
    public static final String SKIP_UNPHASED_FUSIONS = "skip_unphased_fusions";

    private static final Logger LOGGER = LogManager.getLogger(FusionDisruptionAnalyser.class);

    public FusionDisruptionAnalyser()
    {
        mFusionFinder = null;
        mFusionWriter = null;
        mDisruptionFinder = null;
        mGeneTransCollection = new SvGeneTranscriptCollection();
        mOutputDir = "";
        mFusions = Lists.newArrayList();
        mDisruptions = Lists.newArrayList();
        mSkipFusionCheck = false;
        mLogReportableOnly = false;
        mLogAllPotentials = false;
        mVisWriter = null;
        mRnaFusionMapper = null;

        mPerfCounter = new PerformanceCounter("Fusions");

        mChrBreakendMap = null;
        mKnownFusionGenes = Lists.newArrayList();
        mRestrictedGenes = Lists.newArrayList();
    }

    public static void addCmdLineArgs(Options options)
    {
        options.addOption(SAMPLE_RNA_FILE, true, "Sample RNA data to match");
        options.addOption(SKIP_FUSION_OUTPUT, false, "No fusion search or output");
        options.addOption(PRE_GENE_BREAKEND_DISTANCE, true, "Distance after to a breakend to consider in a gene");
        options.addOption(RESTRICTED_GENE_LIST, true, "Restrict fusion search to specific genes");
        options.addOption(LOG_REPORTABLE_ONLY, false, "Only write out reportable fusions");
        options.addOption(LOG_ALL_POTENTIALS, false, "Log all potential fusions");
        options.addOption(SKIP_UNPHASED_FUSIONS, false, "Skip unphased fusions");
        options.addOption(DRUP_TSG_GENES_FILE, true, "List of DRUP TSG genes");
    }

    public void initialise(final CommandLine cmdLineArgs, final String outputDir, final LinxConfig config, SvGeneTranscriptCollection ensemblDataCache)
    {
        mOutputDir = outputDir;

        mConfig = config;
        mGeneTransCollection = ensemblDataCache;
        mFusionFinder = new FusionFinder(cmdLineArgs, ensemblDataCache);
        mFusionWriter = new FusionWriter(mOutputDir);
        mDisruptionFinder = new DisruptionFinder(cmdLineArgs, ensemblDataCache, mOutputDir);

        populateKnownFusionGenes();

        if(cmdLineArgs != null)
        {
            mSkipFusionCheck = cmdLineArgs.hasOption(SKIP_FUSION_OUTPUT);

            if (!mSkipFusionCheck)
            {
                String fusionFileName = mConfig.hasMultipleSamples() ? "SVA_FUSIONS.csv" : mConfig.getSampleIds().get(0) + ".linx.fusions_detailed.csv";
                mFusionWriter.initialiseOutputFile(fusionFileName);

                if(mConfig.hasMultipleSamples())
                    mDisruptionFinder.initialiseOutputFile("SVA_DISRUPTIONS.csv");
            }

            if (cmdLineArgs.hasOption(PRE_GENE_BREAKEND_DISTANCE))
            {
                int preGeneBreakendDistance = Integer.parseInt(cmdLineArgs.getOptionValue(PRE_GENE_BREAKEND_DISTANCE));
                PRE_GENE_PROMOTOR_DISTANCE = preGeneBreakendDistance;
            }

            if (cmdLineArgs.hasOption(SAMPLE_RNA_FILE))
            {
                mRnaFusionMapper = new RnaFusionMapper(mGeneTransCollection, mFusionFinder, mFusions);
                mRnaFusionMapper.setOutputDir(mOutputDir);
                mRnaFusionMapper.loadSampleRnaData(cmdLineArgs.getOptionValue(SAMPLE_RNA_FILE));
            }

            if(cmdLineArgs.hasOption(RESTRICTED_GENE_LIST))
            {
                String restrictedGenesStr = cmdLineArgs.getOptionValue(RESTRICTED_GENE_LIST);
                mRestrictedGenes = Arrays.stream(restrictedGenesStr.split(";")).collect(Collectors.toList());

                LOGGER.info("restricting fusion genes to: {}", restrictedGenesStr);
            }

            mLogReportableOnly = cmdLineArgs.hasOption(LOG_REPORTABLE_ONLY);
            mSkipUnphasedFusions = cmdLineArgs.hasOption(SKIP_UNPHASED_FUSIONS);
            mLogAllPotentials = cmdLineArgs.hasOption(LOG_ALL_POTENTIALS);
        }
    }

    public boolean hasRnaSampleData() { return mRnaFusionMapper != null; }
    public final Set<String> getRnaSampleIds() { return mRnaFusionMapper.getSampleRnaData().keySet(); }
    public final List<GeneFusion> getFusions() { return mFusions; }
    public void setVisWriter(VisualiserWriter writer) { mVisWriter = writer; }

    // for testing
    public void setHasValidConfigData(boolean toggle) { mFusionFinder.setHasValidConfigData(toggle); }
    public final FusionFinder getFusionFinder() { return mFusionFinder; }

    public static void setSvGeneData(final List<SvVarData> svList, SvGeneTranscriptCollection geneCollection,
            boolean applyPromotorDistance, boolean selectiveLoading, boolean purgeInvalidTranscripts)
    {
        int upstreamDistance = applyPromotorDistance ? PRE_GENE_PROMOTOR_DISTANCE : 0;

        if(selectiveLoading)
        {
            // only load transcript info for the genes covered
            List<String> restrictedGeneIds = Lists.newArrayList();

            for (final SvVarData var : svList)
            {
                // isSpecificSV(var);
                for (int be = SE_START; be <= SE_END; ++be)
                {
                    if (be == SE_END && var.isSglBreakend())
                        continue;

                    boolean isStart = isStart(be);

                    geneCollection.populateGeneIdList(restrictedGeneIds, var.chromosome(isStart), var.position(isStart), upstreamDistance);
                }
            }

            geneCollection.loadEnsemblTranscriptData(restrictedGeneIds);
        }

        for(final SvVarData var : svList)
        {
            // isSpecificSV(var);

            for (int be = SE_START; be <= SE_END; ++be)
            {
                if (be == SE_END && var.isSglBreakend())
                    continue;

                boolean isStart = isStart(be);

                List<GeneAnnotation> genesList = geneCollection.findGeneAnnotationsBySv(
                        var.id(), isStart, var.chromosome(isStart), var.position(isStart), var.orientation(isStart), upstreamDistance);

                if (genesList.isEmpty())
                    continue;

                for (GeneAnnotation gene : genesList)
                {
                    gene.setSvData(var.getSvData());
                }

                var.setGenesList(genesList, isStart);
            }

            // mark any transcripts as not disruptive prior to running any fusion logic
            markNonDisruptiveTranscripts(var.getGenesList(true), var.getGenesList(false));

            // inferred SGLs are always non-disruptive
            if(var.isInferredSgl())
            {
                var.getGenesList(true).stream().forEach(x -> x.transcripts().stream().forEach(y -> y.setIsDisruptive(false)));
            }

            // now that transcripts have been marked as disruptive it is safe to purge any which cannot make viable fusions
            if (purgeInvalidTranscripts)
            {
                for (int be = SE_START; be <= SE_END; ++be)
                {
                    if (be == SE_END && var.isSglBreakend())
                        continue;

                    boolean isStart = isStart(be);

                    List<GeneAnnotation> genesList = var.getGenesList(isStart);

                    for (GeneAnnotation gene : genesList)
                    {
                        gene.setSvData(var.getSvData());

                        int transIndex = 0;
                        while (transIndex < gene.transcripts().size())
                        {
                            Transcript transcript = gene.transcripts().get(transIndex);

                            // only retain transcript which are potential fusion candidates (with exception for canonical)
                            if (!transcript.isDisruptive() && !validFusionTranscript(transcript) && !transcript.isCanonical())
                            {
                                gene.transcripts().remove(transIndex);
                            }
                            else
                            {
                                ++transIndex;
                            }
                        }
                    }
                }
            }
        }
    }

    public void run(final String sampleId, final List<SvVarData> svList, final DatabaseAccess dbAccess,
            final List<SvCluster> clusters, Map<String, List<SvBreakend>> chrBreakendMap)
    {
        mPerfCounter.start();

        mSampleId = sampleId;
        mChrBreakendMap = chrBreakendMap;

        if(!mSkipFusionCheck)
        {
            findFusions(svList, clusters);
            findDisruptions(svList);
        }

        if(mConfig.isSingleSample())
        {
            mFusionWriter.writeSampleData(mSampleId, mFusions);
            mDisruptionFinder.writeSampleData(mSampleId, mDisruptions);
        }
        else
        {
            mDisruptions.forEach(x -> mDisruptionFinder.writeDisruptionData(mSampleId, x));
        }

        List<GeneFusion> uniqueFusions = extractUniqueFusions();

        if(mLogAllPotentials)
        {
            mFusions.forEach(x -> writeFusionData(x));
        }
        else
        {
            uniqueFusions.forEach(x -> writeFusionData(x));
        }

        if(LOGGER.isDebugEnabled())
        {
            for(final GeneFusion fusion : uniqueFusions)
            {
                LOGGER.debug("fusion({}-{}) reportable({}) knownType({}) cluster({} sv={}) SVs({} & {})",
                        fusion.upstreamTrans().parent().GeneName, fusion.downstreamTrans().parent().GeneName, fusion.reportable(),
                        fusion.getKnownType(), fusion.getAnnotations().clusterId(), fusion.getAnnotations().clusterCount(),
                        fusion.upstreamTrans().parent().id(), fusion.downstreamTrans().parent().id());
            }
        }

        if(dbAccess != null && mConfig.UploadToDB)
        {
            uploadData(svList, uniqueFusions, dbAccess);
        }

        if(mRnaFusionMapper != null)
            mRnaFusionMapper.assessRnaFusions(sampleId, chrBreakendMap);

        mChrBreakendMap = null;

        mPerfCounter.stop();
    }

    private void findFusions(final List<SvVarData> svList, final List<SvCluster> clusters)
    {
        if(mSampleId.isEmpty() || mFusionFinder == null)
            return;

        mFusions.clear();

        boolean checkSoloSVs = true;
        boolean checkClusters = true;

        if(checkSoloSVs)
        {
            finalSingleSVFusions(svList);
        }

        if(checkClusters)
        {
            findChainedFusions(clusters);
        }
    }

    private void finalSingleSVFusions(final List<SvVarData> svList)
    {
        // always report SVs by themselves
        for (final SvVarData var : svList)
        {
            if (var.isSglBreakend())
                continue;

            // skip SVs which have been chained or in larger
            if(var.getCluster().getSvCount() > 1 && var.getCluster().findChain(var) != null)
                continue;

            // isSpecificSV(var);
            // mFusionFinder.setLogInvalidReasons(isSpecificSV(var));

            List<GeneAnnotation> genesListStart = Lists.newArrayList(var.getGenesList(true));
            List<GeneAnnotation> genesListEnd = Lists.newArrayList(var.getGenesList(false));

            applyGeneRestrictions(genesListStart);
            applyGeneRestrictions(genesListEnd);

            if(genesListStart.isEmpty() || genesListEnd.isEmpty())
                continue;

            List<GeneFusion> fusions = mFusionFinder.findFusions(
                    genesListStart, genesListEnd, mSkipUnphasedFusions, true, null, true);

            if (fusions.isEmpty())
                continue;

            if(mLogReportableOnly)
            {
                fusions = fusions.stream().filter(GeneFusion::reportable).collect(Collectors.toList());
            }

            final SvCluster cluster = var.getCluster();

            // check transcript disruptions
            for (final GeneFusion fusion : fusions)
            {
                FusionTermination[] terminationInfo = {null, null};

                for(int i = 0; i <=1; ++i)
                {
                    boolean isUpstream = (i == 0);

                    // look at each gene in turn
                    Transcript transcript = isUpstream ? fusion.upstreamTrans() : fusion.downstreamTrans();
                    GeneAnnotation gene = isUpstream ? fusion.upstreamTrans().parent() : fusion.downstreamTrans().parent();

                    SvBreakend breakend = var.getBreakend(gene.isStart());

                    terminationInfo[i] = checkTranscriptDisruptionInfo(breakend, transcript);
                }

                FusionAnnotations annotations = ImmutableFusionAnnotations.builder()
                        .clusterId(cluster.id())
                        .clusterCount(cluster.getSvCount())
                        .resolvedType(cluster.getResolvedType().toString())
                        .chainInfo(null)
                        .disruptionUp(terminationInfo[0])
                        .disruptionDown(terminationInfo[1])
                        .build();

                fusion.setAnnotations(annotations);
                mFusions.add(fusion);
            }
        }

        mFusionFinder.setLogInvalidReasons(false);
    }

    private void findChainedFusions(final List<SvCluster> clusters)
    {
        // for now only consider simple SVs and resolved small clusters
        for (final SvCluster cluster : clusters)
        {
            if (cluster.getSvCount() == 1) // simple clusters already checked
                continue;

            if (cluster.getChains().isEmpty())
                continue;

            // isSpecificCluster(cluster);

            List<GeneFusion> chainFusions = Lists.newArrayList();

            for (final SvChain chain : cluster.getChains())
            {
                findChainedFusions(cluster, chain, chainFusions);
            }

            if(chainFusions.isEmpty())
                continue;

            // now all fusions have been gathered from this chain, set the reportable one (if any)
            LOGGER.debug("cluster({}) found {} chained fusions", cluster.id(), chainFusions.size());

            // consider fusions from amongst unique gene-pairings
            List<String> genePairings = Lists.newArrayList();

            for(int i = 0; i < chainFusions.size(); ++i)
            {
                GeneFusion fusion = chainFusions.get(i);
                String genePair = fusion.upstreamTrans().parent().GeneName + "_" + fusion.downstreamTrans().parent().GeneName;

                if(genePairings.contains(genePair))
                    continue;

                genePairings.add(genePair);

                // gather up all matching fusions
                List<GeneFusion> genePairFusions = Lists.newArrayList();
                genePairFusions.add(fusion);

                for(int j = i+1; j < chainFusions.size(); ++j)
                {
                    GeneFusion nextFusion = chainFusions.get(j);
                    String nextGenePair = nextFusion.name();

                    if(nextGenePair.equals(genePair))
                    {
                        genePairFusions.add(nextFusion);
                    }
                }

                // only chained fusions with unterminated ends and valid traversal are considered as reportable
                mFusionFinder.setReportableGeneFusions(genePairFusions);
            }

            mFusions.addAll(chainFusions.stream()
                    .filter(x -> !mLogReportableOnly || x.reportable())
                    .filter(x -> x.getAnnotations() != null)
                    .collect(Collectors.toList()));
        }
    }

    private static int FUSION_MAX_CHAIN_LENGTH = 100000;

    private void findChainedFusions(final SvCluster cluster, final SvChain chain, List<GeneFusion> chainFusions)
    {
        // look for fusions formed by breakends connected in a chain

        // given a chain sAe - sBe - sCe - sDe, where As and De are the open ends of the chain, the following fusions need to be tested:
        // a) each SV in isolation, ie as a single SV
        // b) the left-most / lower breakend of each SV with with right-most / upper breakend of SVs higher up in the chain

        // whenever a linked pair is traversed by a fusion, it cannot touch or traverse genic regions without disrupting the fusion
        final List<SvLinkedPair> linkedPairs = chain.getLinkedPairs();

        for (int lpIndex1 = 0; lpIndex1 <= linkedPairs.size(); ++lpIndex1)
        {
            SvVarData lowerSV = null;
            SvBreakend lowerBreakend = null;

            // the lower link takes the other breakend of the current linked pair's 'first' SV
            // and in order for it to also test the last SV in isolation, also takes the last pair's second (upper) breakend
            if(lpIndex1 < linkedPairs.size())
            {
                SvLinkedPair pair = linkedPairs.get(lpIndex1);
                lowerSV = pair.first();
                lowerBreakend = pair.first().getBreakend(!pair.firstLinkOnStart());
            }
            else
            {
                SvLinkedPair prevPair = linkedPairs.get(lpIndex1 - 1);
                lowerSV = prevPair.second();
                lowerBreakend = prevPair.secondBreakend();
            }

            //isSpecificSV(lowerSV);

            if (lowerSV.isSglBreakend())
                continue;

            List<GeneAnnotation> genesListLower = Lists.newArrayList(lowerSV.getGenesList(lowerBreakend.usesStart()));
            applyGeneRestrictions(genesListLower);

            if (genesListLower.isEmpty())
                continue;

            List<SvLinkedPair> traversedPairs = Lists.newArrayList();

            for (int lpIndex2 = lpIndex1; lpIndex2 <= linkedPairs.size(); ++lpIndex2)
            {
                SvVarData upperSV = null;
                SvBreakend upperBreakend = null;

                // the upper link takes the breakend of the current linked pair's 'second' SV
                // and beyond all the links, it must also test fusions with the chain's upper open breakend
                if(lpIndex2 < linkedPairs.size())
                {
                    SvLinkedPair pair = linkedPairs.get(lpIndex2);
                    upperSV = pair.first();
                    upperBreakend = pair.firstBreakend();
                }
                else
                {
                    // at the last link, take the open breakend of the chain
                    upperBreakend = chain.getOpenBreakend(false);

                    if(upperBreakend == null) // can be null for SGLs at end of chain
                        break;

                    upperSV = upperBreakend.getSV();
                }

                // isSpecificSV(upperSV);

                List<GeneAnnotation> genesListUpper = Lists.newArrayList(upperSV.getGenesList(upperBreakend.usesStart()));
                applyGeneRestrictions(genesListUpper);

                // if a new linked pair has been traversed to reach this upper breakend, record its length
                // and test whether it traverses any genic region, and if so invalidate the fusion
                if(lpIndex2 > lpIndex1)
                {
                    SvLinkedPair lastPair = linkedPairs.get(lpIndex2 - 1);
                    traversedPairs.add(lastPair);
                }

                if(genesListUpper.isEmpty())
                {
                    // skip past this link and breakend to the next one, keeping the possibility of a fusion with the lower breakend open
                    continue;
                }

                /*
                if(lpIndex2 > lpIndex1)
                {
                    LOGGER.debug("cluster({}) chain({}) testing chained fusion: be1({} {}) & be2({} {}) link indices({} -> {})",
                            cluster.id(), chain.id(), lowerBreakend.toString(), genesListLower.get(0).GeneName,
                            upperBreakend.toString(), genesListUpper.get(0).GeneName, lpIndex1, lpIndex2);
                }
                */

                // test the fusion between these 2 breakends

                // boolean logFusionReasons = isSpecificSV(lowerBreakend.getSV()) & isSpecificSV(upperBreakend.getSV());
                // mFusionFinder.setLogInvalidReasons(logFusionReasons);

                List<GeneFusion> fusions = mFusionFinder.findFusions(genesListLower, genesListUpper,
                        mSkipUnphasedFusions, true, null, false);

                if(fusions.isEmpty())
                    continue;

                if (lpIndex2 > lpIndex1)
                {
                    // a chain cannot be an exon-exon fusion, so cull any of these
                    fusions = fusions.stream().filter(x -> !x.isExonic()).collect(Collectors.toList());
                }

                if(mLogReportableOnly)
                {
                    fusions = fusions.stream().filter(x -> couldBeReportable(x)).collect(Collectors.toList());

                    if(fusions.isEmpty())
                        continue;
                }

                int validTraversalFusionCount = 0; // between these 2 SVs

                for (GeneFusion fusion : fusions)
                {
                    // if the fusion from the upstream gene is on the positive strand, then it will have a fusion direction of +1
                    // whenever it goes through a subsequent linked pair by joining to the first (lower) breakend in the pair
                    int upGeneStrand = fusion.upstreamTrans().parent().Strand;
                    boolean isPrecodingUpstream = fusion.upstreamTrans().preCoding();
                    boolean fusionLowerToUpper = fusion.upstreamTrans().parent().position() == lowerBreakend.position();

                    // check any traversed genes
                    long totalLinkLength = 0;
                    boolean validTraversal = true;
                    boolean allTraversalAssembled = true;

                    for(SvLinkedPair pair : traversedPairs)
                    {
                        totalLinkLength += pair.length();

                        if(pair.isInferred())
                            allTraversalAssembled = false;

                        // if going lower to upper, if the orientation of the first breakend in the pair is opposite to the strand of
                        // the upstream gene, then the fusion direction for that pair is the same as a the upstream gene
                        // otherwise it needs to be switched
                        int fusionDirection = 0;

                        if(fusionLowerToUpper)
                        {
                            fusionDirection = pair.firstBreakend().orientation() != upGeneStrand ? upGeneStrand : -upGeneStrand;
                        }
                        else
                        {
                            fusionDirection = pair.secondBreakend().orientation() != upGeneStrand ? upGeneStrand : -upGeneStrand;
                        }

                        // any invalid traversal causes this fusion to be entirely skipped from further analysis
                        if(pairTraversesGene(pair, fusionDirection, isPrecodingUpstream))
                        {
                            validTraversal = false;
                            break;
                        }
                    }

                    if(!validTraversal)
                        continue;

                    ++validTraversalFusionCount;

                    FusionTermination[] terminationInfo = {null, null};

                    for (int se = SE_START; se <= SE_END; ++se)
                    {
                        boolean isUpstream = (se == 0);

                        // look at each gene in turn
                        Transcript transcript = isUpstream ? fusion.upstreamTrans() : fusion.downstreamTrans();
                        GeneAnnotation gene = isUpstream ? fusion.upstreamTrans().parent() : fusion.downstreamTrans().parent();

                        SvBreakend breakend = lowerBreakend.position() == gene.position() ? lowerBreakend : upperBreakend;

                        boolean isChainEnd = (breakend == lowerBreakend && lpIndex1 == 0)
                                || (breakend == upperBreakend && lpIndex2 == linkedPairs.size());

                        if (isChainEnd)
                        {
                            terminationInfo[se] = checkTranscriptDisruptionInfo(breakend, transcript);
                        }
                        else
                        {
                            // looks from this link outwards past the end of the transcript for any invalidation of the transcript
                            int linkIndex = breakend == lowerBreakend ? lpIndex1 - 1 : lpIndex2;
                            terminationInfo[se] = checkTranscriptDisruptionInfo(breakend, transcript, chain, linkIndex);
                        }
                    }

                    int linksCount = lpIndex2 - lpIndex1;

                    FusionChainInfo chainInfo = ImmutableFusionChainInfo.builder()
                            .chainId(chain.id())
                            .chainLinks(linksCount)
                            .chainLength(totalLinkLength)
                            .traversalAssembled(allTraversalAssembled)
                            .validTraversal(validTraversal)
                            .build();

                    FusionAnnotations annotations = ImmutableFusionAnnotations.builder()
                            .clusterId(cluster.id())
                            .clusterCount(cluster.getSvCount())
                            .resolvedType(cluster.getResolvedType().toString())
                            .chainInfo(chainInfo)
                            .disruptionUp(terminationInfo[SE_START])
                            .disruptionDown(terminationInfo[SE_END])
                            .build();

                    fusion.setAnnotations(annotations);

                    // accept invalidated chains and transcripts for known fusions
                    boolean isKnown = fusion.getKnownType() == REPORTABLE_TYPE_KNOWN;
                    boolean chainLengthOk =  totalLinkLength <= FUSION_MAX_CHAIN_LENGTH;
                    boolean notTerminated = !fusion.isTerminated();

                    if(validTraversal && ((chainLengthOk && notTerminated) || isKnown))
                    {
                        if(!hasIdenticalFusion(fusion, chainFusions))
                        {
                            chainFusions.add(fusion);
                        }
                    }
                }

                if(validTraversalFusionCount == 0 && lpIndex2 > lpIndex1)
                {
                    // if there are no valid traversals between 2 indices, then any chain sections starting at the lower index
                    // will likewise be invalidated since can skip past this
                    LOGGER.trace("cluster({}) chain({}) no valid traversals between({} -> {})",
                            cluster.id(), chain.id(), lpIndex1, lpIndex2);

                    break;
                }
            }
        }

        mFusionFinder.setLogInvalidReasons(false);
    }

    private boolean hasIdenticalFusion(final GeneFusion newFusion, final List<GeneFusion> fusions)
    {
        for(GeneFusion fusion : fusions)
        {
            if(newFusion.upstreamTrans().parent().id() != fusion.upstreamTrans().parent().id())
                continue;

            if(newFusion.downstreamTrans().parent().id() != fusion.downstreamTrans().parent().id())
                continue;

            if(!newFusion.upstreamTrans().StableId.equals(fusion.upstreamTrans().StableId))
                continue;

            if(!newFusion.downstreamTrans().StableId.equals(fusion.downstreamTrans().StableId))
                continue;

            return true;
        }

        return false;
    }

    private void applyGeneRestrictions(List<GeneAnnotation> genesList)
    {
        if(mRestrictedGenes.isEmpty())
            return;

        int index = 0;
        while(index < genesList.size())
        {
            GeneAnnotation gene = genesList.get(index);

            if(mRestrictedGenes.contains(gene.GeneName))
                ++index;
            else
                genesList.remove(index);
        }
    }

    private FusionTermination checkTranscriptDisruptionInfo(final SvBreakend breakend, final Transcript transcript)
    {
        // check all breakends which fall within the bounds of this transcript, including any which are exonic
        List<SvBreakend> breakendList = mChrBreakendMap.get(breakend.chromosome());

        int totalBreakends = 0;
        int facingBreakends = 0;
        int disruptedExons = 0;
        long minDistance = -1;

        final SvCluster cluster = breakend.getCluster();

        int index = breakend.getChrPosIndex();

        while(true)
        {
            index += breakend.orientation() == -1 ? +1 : -1;

            if (index < 0 || index >= breakendList.size())
                break;

            SvBreakend nextBreakend = breakendList.get(index);

            // exit once the next breakend extends beyond the gene bounds
            if((breakend.orientation() == 1 && nextBreakend.position() < transcript.TranscriptStart)
            || (breakend.orientation() == -1 && nextBreakend.position() > transcript.TranscriptEnd))
            {
                break;
            }

            ++totalBreakends;

            if (nextBreakend.orientation() != breakend.orientation())
            {
                // skip breakends which cannot be chained by min TI length
                int minTiLength = getMinTemplatedInsertionLength(breakend, nextBreakend);
                long breakendDistance = abs(breakend.position() - nextBreakend.position());

                if(breakendDistance < minTiLength)
                    continue;

                if (minDistance == -1)
                    minDistance = breakendDistance;

                ++facingBreakends;
            }
        }

        boolean allLinksAssembled = false;

        if(facingBreakends > 0)
        {
            // is any facing breakend assembled?
            final SvLinkedPair tiLink = breakend.getSV().getLinkedPair(breakend.usesStart());
            allLinksAssembled = tiLink != null && tiLink.isAssembled();
        }

        return ImmutableFusionTermination.builder()
                .allLinksAssembled(allLinksAssembled)
                .facingBreakends(facingBreakends)
                .disruptedExons(disruptedExons)
                .totalBreakends(totalBreakends)
                .minDistance(minDistance)
                .transcriptTerminated(false)
                .build();
    }

    private FusionTermination checkTranscriptDisruptionInfo(
            final SvBreakend breakend, final Transcript transcript, final SvChain chain, int linkIndex)
    {
        // starting with this breakend and working onwards from it in the chain, check for any disruptions to the transcript
        // this includes subsequent links within the same chain and transcript
        SvLinkedPair startPair = chain.getLinkedPairs().get(linkIndex);
        boolean traverseUp = startPair.firstBreakend() == breakend; // whether to search up or down the chain

        int totalBreakends = 0;
        int facingBreakends = 0;
        int disruptedExons = 0;
        boolean transcriptTerminated = false;
        long minDistance = startPair.length();
        boolean allLinksAssembled = startPair.isAssembled();

        boolean isUpstream = transcript.isUpstream();

        while(linkIndex >= 0 && linkIndex <= chain.getLinkedPairs().size() - 1)
        {
            SvLinkedPair pair = chain.getLinkedPairs().get(linkIndex);

            if(pair.isInferred())
                allLinksAssembled = false;

            // identify next exon after this TI
            // the breakend's transcript info cannot be used because it faces the opposite way from the fusing breakend
            SvBreakend nextBreakend = traverseUp ? pair.secondBreakend() : pair.firstBreakend();

            // exit if the next breakend is now past the end of the transcript or if the breakend is down-stream of coding
            if(nextBreakend.orientation() == 1)
            {
                if(nextBreakend.position() > transcript.TranscriptEnd)
                    break;
                else if(!isUpstream && transcript.CodingEnd != null && nextBreakend.position() > transcript.CodingEnd)
                    break;
            }
            else
            {
                if(nextBreakend.position() < transcript.TranscriptStart)
                    break;
                else if(!isUpstream && transcript.CodingStart != null && nextBreakend.position() < transcript.CodingStart)
                    break;
            }

            transcriptTerminated = true;

            ++totalBreakends;
            ++facingBreakends;

            break;

            // no longer check that subsequent links within the same transcript are valid
        }

        return ImmutableFusionTermination.builder()
                .allLinksAssembled(allLinksAssembled)
                .facingBreakends(facingBreakends)
                .disruptedExons(disruptedExons)
                .totalBreakends(totalBreakends)
                .minDistance(minDistance)
                .transcriptTerminated(transcriptTerminated)
                .build();
    }

    private boolean pairTraversesGene(SvLinkedPair pair, int fusionDirection, boolean isPrecodingUpstream)
    {
        // for this pair to not affect the fusion, the section it traverses cannot cross any gene's splice acceptor
        // with the same strand direction unless that is part of a fully traversed non-coding 5' exon

        long lowerPos = pair.getBreakend(true).position();
        long upperPos = pair.getBreakend(false).position();

        List<EnsemblGeneData> geneDataList = mGeneTransCollection.getChrGeneDataMap().get(pair.chromosome());

        if(geneDataList == null)
            return false;

        for(EnsemblGeneData geneData : geneDataList)
        {
            if(lowerPos > geneData.GeneEnd)
                continue;

            if(upperPos < geneData.GeneStart)
                break;

            if(geneData.Strand == fusionDirection)
            {
                // check whether a splice acceptor is encountered within this window
                List<TranscriptData> transDataList = mGeneTransCollection.getTranscripts(geneData.GeneId);

                if(transDataList == null)
                    continue;

                for(final TranscriptData transData : transDataList)
                {
                    for (final ExonData exonData : transData.exons())
                    {
                        if (exonData.ExonRank == 1)
                            continue;

                        if ((geneData.Strand == 1 && lowerPos <= exonData.ExonStart && upperPos >= exonData.ExonStart)
                                || (geneData.Strand == -1 && lowerPos <= exonData.ExonEnd && upperPos >= exonData.ExonEnd))
                        {
                            // allow an exon to be fully traversed if the upstream transcript is pre-coding
                            if (isPrecodingUpstream && lowerPos <= exonData.ExonStart && upperPos >= exonData.ExonEnd)
                            {
                                if (geneData.Strand == 1 && (transData.CodingStart == null || upperPos < transData.CodingStart))
                                    continue;
                                else if (geneData.Strand == -1 && (transData.CodingEnd == null || lowerPos > transData.CodingEnd))
                                    continue;
                            }

                            LOGGER.trace("pair({}) fusionDirection({}) traverses splice acceptor({} {}) exon(rank{} pos={})",
                                    pair.toString(), fusionDirection, geneData.GeneName, transData.TransName,
                                    exonData.ExonRank, exonData.ExonStart, exonData.ExonEnd);

                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private void populateKnownFusionGenes()
    {
        if(mFusionFinder.getKnownFusionDatal() == null)
            return;

        for(final String[] genePair : mFusionFinder.getKnownFusionDatal().knownPairs())
        {
            if(!mKnownFusionGenes.contains(genePair[FIVE_GENE]))
                mKnownFusionGenes.add(genePair[FIVE_GENE]);

            if(!mKnownFusionGenes.contains(genePair[THREE_GENE]))
                mKnownFusionGenes.add(genePair[THREE_GENE]);
        }
    }

    private List<GeneFusion> extractUniqueFusions()
    {
        // from the list of all potential fusions, collect up all reportable ones, and then the highest priority fusion
        // from amongst the those with the same gene-pairing and/or SV Id
        List<GeneFusion> uniqueFusions = mFusions.stream().filter(GeneFusion::reportable).collect(Collectors.toList());

        List<String> genePairs = Lists.newArrayList();
        uniqueFusions.stream().forEach(x -> genePairs.add(x.name()));

        List<Integer> usedSvIds = Lists.newArrayList();
        uniqueFusions.stream().forEach(x -> usedSvIds.add(x.upstreamTrans().parent().id()));

        uniqueFusions.stream()
                .filter(x -> !usedSvIds.contains(x.downstreamTrans().parent().id()))
                .forEach(x -> usedSvIds.add(x.downstreamTrans().parent().id()));

        for(GeneFusion fusion : mFusions)
        {
            if(fusion.reportable() || genePairs.contains(fusion.name()))
                continue;

            if(usedSvIds.contains(fusion.upstreamTrans().parent().id()) || usedSvIds.contains(fusion.downstreamTrans().parent().id()))
                continue;

            // only add viable fusions for upload
            if(!fusion.isViable())
                continue;

            // gather up all other candidate fusions for this pairing and take the highest priority
            List<GeneFusion> similarFusions = mFusions.stream()
                    .filter(x -> !x.reportable())
                    .filter(x -> x.isViable())
                    .filter(x -> x != fusion)
                    .filter(x -> x.name().equals(fusion.name()))
                    .collect(Collectors.toList());

            genePairs.add(fusion.name());

            usedSvIds.add(fusion.upstreamTrans().parent().id());

            if(!usedSvIds.contains(fusion.downstreamTrans().parent().id()))
                usedSvIds.add(fusion.downstreamTrans().parent().id());

            GeneFusion topFusion = determineReportableFusion(similarFusions, false);

            if(topFusion != null)
            {
                // add protein information which won't have been set for non-known fusions
                mFusionFinder.setFusionProteinFeatures(topFusion);

                uniqueFusions.add(topFusion);
            }
        }

        return uniqueFusions;
    }

    private void uploadData(final List<SvVarData> svList, List<GeneFusion> fusionsToUpload, final DatabaseAccess dbAccess)
    {
        if (dbAccess == null)
            return;

        // upload every reportable fusion and if a gene-pair has no reportable fusion then upload the top-priority fusion
        // upload every breakend in a fusion to be uploaded, and any other disrupted or canonical transcripts
        List<Transcript> transcriptsToUpload = Lists.newArrayList();

        for (SvVarData var : svList)
        {
            for (int be = SE_START; be <= SE_END; ++be)
            {
                for (GeneAnnotation geneAnnotation : var.getGenesList(isStart(be)))
                {
                    transcriptsToUpload.addAll(geneAnnotation.transcripts().stream()
                            .filter(x -> x.isCanonical() || x.isDisruptive())
                            .collect(Collectors.toList()));
                }
            }
        }

        for(GeneFusion fusion : fusionsToUpload)
        {
            if(!transcriptsToUpload.contains(fusion.upstreamTrans()))
                transcriptsToUpload.add(fusion.upstreamTrans());

            if(!transcriptsToUpload.contains(fusion.downstreamTrans()))
                transcriptsToUpload.add(fusion.downstreamTrans());
        }

        // transcripts not used in fusions won't have the exact exonic base set
        transcriptsToUpload.stream().filter(Transcript::isExonic).forEach(x -> x.setExonicCodingBase());

        LOGGER.debug("persisting {} breakends and {} fusions to database", transcriptsToUpload.size(), fusionsToUpload.size());

        final StructuralVariantFusionDAO annotationDAO = new StructuralVariantFusionDAO(dbAccess.context());
        annotationDAO.writeBreakendsAndFusions(mSampleId, transcriptsToUpload, fusionsToUpload);
    }



    private void writeFusionData(final GeneFusion fusion)
    {
        // write fusions in detail
        if (fusion.reportable() || !mLogReportableOnly)
        {
            mFusionWriter.writeFusionData(fusion, mSampleId);
        }

        if(fusion.reportable() && mVisWriter != null)
        {
            int clusterId = fusion.getAnnotations() != null ? fusion.getAnnotations().clusterId() : -1;

            mVisWriter.addGeneExonData(clusterId,
                    fusion.upstreamTrans().parent().StableId, fusion.upstreamTrans().parent().GeneName,
                    fusion.upstreamTrans().StableId, fusion.upstreamTrans().TransId,
                    fusion.upstreamTrans().parent().chromosome(), "FUSION");

            mVisWriter.addGeneExonData(clusterId,
                    fusion.downstreamTrans().parent().StableId, fusion.downstreamTrans().parent().GeneName,
                    fusion.downstreamTrans().StableId, fusion.downstreamTrans().TransId,
                    fusion.downstreamTrans().parent().chromosome(), "FUSION");
        }
    }

    private void findDisruptions(final List<SvVarData> svList)
    {
        mDisruptions.clear();

        for (final SvVarData var : svList)
        {
            for (int be = SE_START; be <= SE_END; ++be)
            {
                if (be == SE_END && var.isSglBreakend())
                    continue;

                final List<GeneAnnotation> tsgGenesList = var.getGenesList(isStart(be)).stream()
                        .filter(x -> mDisruptionFinder.matchesDisruptionGene(x)).collect(Collectors.toList());

                for(GeneAnnotation gene : tsgGenesList)
                {
                    for(Transcript transcript : gene.transcripts())
                    {
                        if(transcript.isDisruptive() && transcript.isCanonical())
                        {
                            LOGGER.debug("TSG gene({}) transcript({}) is disrupted", gene.GeneName, transcript.StableId);
                            transcript.setReportableDisruption(true);
                            mDisruptions.add(transcript);
                        }
                    }
                }
            }
        }
    }

    public void close()
    {
        if(mConfig.hasMultipleSamples() || LOGGER.isDebugEnabled())
        {
            mPerfCounter.logStats();
        }

        if(mFusionFinder != null)
            mFusionWriter.close();

        if(mRnaFusionMapper != null)
            mRnaFusionMapper.close();

        if(mDisruptionFinder != null)
            mDisruptionFinder.close();
    }

    // currently unused logic for finding unclustered or chained fusions:

    /*

    private boolean hasFusionGenePair(final GeneAnnotation geneUp, final GeneAnnotation geneDown)
    {
        for(final GeneFusion fusion : mFusions)
        {
            final GeneAnnotation upGene = fusion.upstreamTrans().parent();
            final GeneAnnotation downGene = fusion.downstreamTrans().parent();

            if(upGene.GeneName.equals(geneUp.GeneName) && downGene.GeneName.equals(geneDown.GeneName))
                return true;
        }

        return false;
    }

    private boolean areKnownFusionGenes(final String geneUp, final String geneDown)
    {
        if(mFusionFinder.getKnownFusionsModel() == null)
            return false;

        for(Pair<String,String> genePair : mFusionFinder.getKnownFusionsModel().fusions().keySet())
        {
            if(genePair.getFirst().equals(geneUp) && genePair.getSecond().equals(geneDown))
                return true;
        }

        return false;
    }

    public static final String LOG_KNOWN_FUSION_DATA = "log_known_fusion_data";
    public static final String INVALID_REASON_UNCLUSTERED = "Unclustered";
    public static final String INVALID_REASON_UNCHAINED = "Unchained";

    private void findUnclusteredKnownFusions(final List<SvVarData> svList)
    {
        // look for unclustered or unchained SVs with breakends in known fusion pairs
        Map<SvBreakend, GeneAnnotation> knownFusionBreakends = Maps.newHashMap();

        for(final SvVarData var :svList)
        {
            for (int be = SE_START; be <= SE_END; ++be)
            {
                boolean isStart = isStart(be);

                List<GeneAnnotation> genesList = Lists.newArrayList(var.getGenesList(isStart));

                for(GeneAnnotation gene : genesList)
                {
                    if(mKnownFusionGenes.contains(gene.GeneName))
                    {
                        knownFusionBreakends.put(var.getBreakend(isStart), gene);
                        break;
                    }
                }
            }
        }

        for (Map.Entry<SvBreakend, GeneAnnotation> entry1 : knownFusionBreakends.entrySet())
        {
            SvBreakend be1 = entry1.getKey();
            GeneAnnotation gene1 = entry1.getValue();
            List<GeneAnnotation> genes1 = Lists.newArrayList(gene1);

            for (Map.Entry<SvBreakend, GeneAnnotation> entry2 : knownFusionBreakends.entrySet())
            {
                SvBreakend be2 = entry2.getKey();
                GeneAnnotation gene2 = entry2.getValue();
                List<GeneAnnotation> genes2 = Lists.newArrayList(gene2);

                if(be1 == be2)
                    continue;

                if(!areKnownFusionGenes(gene1.GeneName, gene2.GeneName))
                    continue;

                if (hasFusionGenePair(gene1, gene2))
                    continue;

                if(be1.getSV().getCluster() == be2.getSV().getCluster())
                {
                    // skip if already in the same chain
                    SvCluster cluster = be1.getSV().getCluster();

                    if(cluster.findSameChainForSVs(be1.getSV(), be2.getSV()) != null)
                        continue;
                }

                // isSpecificSV(be1.getSV());

                List<GeneFusion> fusions = mFusionFinder.findFusions(genes1, genes2,
                        true, true, null, true);

                if (fusions.isEmpty())
                    continue;

                if(mLogReportableOnly)
                {
                    fusions = fusions.stream().filter(GeneFusion::reportable).collect(Collectors.toList());
                }

                // check transcript disruptions
                for (final GeneFusion fusion : fusions)
                {
                    FusionTermination[] terminationInfo = {null, null};

                    for(int i = 0; i <=1; ++i)
                    {
                        boolean isUpstream = (i == 0);

                        // look at each gene in turn
                        Transcript transcript = isUpstream ? fusion.upstreamTrans() : fusion.downstreamTrans();
                        GeneAnnotation gene = isUpstream ? fusion.upstreamTrans().parent() : fusion.downstreamTrans().parent();

                        SvBreakend breakend = gene.GeneName.equals(gene1.GeneName) ? be1 : be2;

                        terminationInfo[i] = checkTranscriptDisruptionInfo(breakend, transcript);
                    }

                    final SvCluster cluster1 = be1.getSV().getCluster();
                    final SvCluster cluster2 = be2.getSV().getCluster();

                    String combinedClusterInfo = String.format("Unclustered;%d;%s;%d;%s",
                            cluster1.getSvCount(), cluster1.getResolvedType(), cluster2.getSvCount(), cluster2.getResolvedType());

                    FusionAnnotations annotations = ImmutableFusionAnnotations.builder()
                            .clusterId(cluster1.id())
                            .clusterCount(cluster1.getSvCount())
                            .resolvedType(combinedClusterInfo)
                            .chainInfo(null)
                            .disruptionUp(terminationInfo[0])
                            .disruptionDown(terminationInfo[1])
                            .build();

                    fusion.setAnnotations(annotations);
                    writeFusionData(fusion, cluster1);
                }

                mFusions.addAll(fusions);
            }
        }

        if(mReportKnownFusionData)
        {
            logKnownFusionData(knownFusionBreakends);
        }
    }

    private void logKnownFusionData(Map<SvBreakend, GeneAnnotation> knownFusionBreakends)
    {
        for (Map.Entry<SvBreakend, GeneAnnotation> entry1 : knownFusionBreakends.entrySet())
        {
            SvBreakend be1 = entry1.getKey();
            GeneAnnotation gene1 = entry1.getValue();
            List<GeneAnnotation> genes1 = Lists.newArrayList(gene1);

            // skip SVs where all transcripts are non-disruptive
            if(!gene1.hasAnyDisruptiveTranscript())
                continue;

            for (Map.Entry<SvBreakend, GeneAnnotation> entry2 : knownFusionBreakends.entrySet())
            {
                SvBreakend be2 = entry2.getKey();
                GeneAnnotation gene2 = entry2.getValue();
                List<GeneAnnotation> genes2 = Lists.newArrayList(gene2);

                if (be1 == be2)
                    continue;

                if(!gene2.hasAnyDisruptiveTranscript())
                    continue;

                if (!areKnownFusionGenes(gene1.GeneName, gene2.GeneName))
                    continue;

                if (hasFusionGenePair(gene1, gene2)) // already captured even if not valid
                    continue;

                // possible reasons for not forming a valid fusions are:
                // 1. Unclustered or unchained SVs
                // 2. Incorrect orientations
                // 3. Invalid transcript locations (eg post-coding)
                // 4. Unphased

                List<String> invalidReasons = Lists.newArrayList();

                if (be1.getSV().getCluster() != be2.getSV().getCluster())
                {
                    invalidReasons.add(INVALID_REASON_UNCLUSTERED);
                }
                else
                {
                    SvCluster cluster = be1.getSV().getCluster();

                    if(cluster.findSameChainForSVs(be1.getSV(), be2.getSV()) == null)
                    {
                        invalidReasons.add(INVALID_REASON_UNCHAINED);
                    }
                }

                List<GeneFusion> fusions = mFusionFinder.findFusions(genes1, genes2, true, true, invalidReasons, false);

                String invalidReasonStr = "";

                for(String reason : invalidReasons)
                {
                    invalidReasonStr = appendStr(invalidReasonStr, reason, ';');
                }

                // SampleId,GeneUp,GeneDown,SvId1,SvId2,InvalidReasons
                String fusionInfo = String.format("%s,%s,%s,%s,%s,%s",
                        mSampleId, gene1.GeneName, gene2.GeneName, be1.getSV().id(), be2.getSV().id(), invalidReasonStr);

                LOGGER.info("KNOWN_FUSION_DATA: {}", fusionInfo);
            }
        }
    }
    */

}
