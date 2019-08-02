package com.hartwig.hmftools.linx.analysis;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DEL;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.DUP;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INS;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.INV;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantType.SGL;
import static com.hartwig.hmftools.linx.analysis.ClusterAnalyser.NEW_CLUSTERING;
import static com.hartwig.hmftools.linx.analysis.ClusteringState.CR_HOM_LOSS;
import static com.hartwig.hmftools.linx.analysis.ClusteringState.CR_LOH;
import static com.hartwig.hmftools.linx.analysis.ClusteringState.CR_LOH_CHAIN;
import static com.hartwig.hmftools.linx.analysis.ClusteringState.CR_LONG_DEL_DUP_OR_INV;
import static com.hartwig.hmftools.linx.analysis.ClusteringState.CR_MAJOR_AP_PLOIDY;
import static com.hartwig.hmftools.linx.analysis.ClusteringState.CR_PROXIMITY;
import static com.hartwig.hmftools.linx.analysis.ClusteringState.CLUSTER_REASON_SOLO_SINGLE;
import static com.hartwig.hmftools.linx.analysis.SvClassification.isSimpleSingleSV;
import static com.hartwig.hmftools.linx.analysis.SvClassification.markSinglePairResolvedType;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.copyNumbersEqual;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.formatPloidy;
import static com.hartwig.hmftools.linx.types.ResolvedType.LINE;
import static com.hartwig.hmftools.linx.types.ResolvedType.NONE;
import static com.hartwig.hmftools.linx.types.ResolvedType.PAIR_OTHER;
import static com.hartwig.hmftools.linx.cn.LohEvent.CN_DATA_NO_SV;
import static com.hartwig.hmftools.linx.types.SvCluster.isSpecificCluster;
import static com.hartwig.hmftools.linx.types.SvVarData.RELATION_TYPE_NEIGHBOUR;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_END;
import static com.hartwig.hmftools.linx.types.SvVarData.SE_START;
import static com.hartwig.hmftools.linx.types.SvVarData.isStart;
import static com.hartwig.hmftools.linx.types.SvaConstants.LOW_CN_CHANGE_SUPPORT;
import static com.hartwig.hmftools.linx.types.SvaConstants.MAX_MERGE_DISTANCE;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantType;
import com.hartwig.hmftools.linx.cn.HomLossEvent;
import com.hartwig.hmftools.linx.types.ResolvedType;
import com.hartwig.hmftools.linx.types.SvBreakend;
import com.hartwig.hmftools.linx.types.SvCluster;
import com.hartwig.hmftools.linx.cn.LohEvent;
import com.hartwig.hmftools.linx.types.SvVarData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimpleClustering
{
    private ClusteringState mState;

    private static final Logger LOGGER = LogManager.getLogger(SimpleClustering.class);

    public SimpleClustering(ClusteringState state)
    {
        mState = state;
    }

    private int getNextClusterId() { return mState.getNextClusterId(); }

    public void clusterByProximity(List<SvCluster> clusters, int proximityDistance)
    {
        // walk through each chromosome and breakend list
        for (final Map.Entry<String, List<SvBreakend>> entry : mState.getChrBreakendMap().entrySet())
        {
            final List<SvBreakend> breakendList = entry.getValue();

            int currentIndex = 0;
            while (currentIndex < breakendList.size())
            {
                final SvBreakend breakend = breakendList.get(currentIndex);
                SvVarData var = breakend.getSV();

                SvBreakend nextBreakend = null;
                int nextIndex = currentIndex + 1;

                for (; nextIndex < breakendList.size(); ++nextIndex)
                {
                    final SvBreakend nextBe = breakendList.get(nextIndex);
                    nextBreakend = nextBe;
                    break;
                }

                if (nextBreakend == null)
                {
                    // no more breakends on this chromosome
                    if (var.getCluster() == null)
                    {
                        SvCluster cluster = new SvCluster(getNextClusterId());
                        cluster.addVariant(var);
                        clusters.add(cluster);
                    }

                    break;
                }

                SvCluster cluster = var.getCluster();
                SvVarData nextVar = nextBreakend.getSV();
                SvCluster nextCluster = nextVar.getCluster();

                if (cluster != null && cluster == nextCluster)
                {
                    // already clustered
                }
                else if (abs(nextBreakend.position() - breakend.position()) > proximityDistance)
                {
                    // too far between the breakends
                    if (cluster == null)
                    {
                        cluster = new SvCluster(getNextClusterId());
                        cluster.addVariant(var);
                        clusters.add(cluster);
                    }
                    else
                    {
                        // nothing more to do for this variant - already clustered
                    }
                }
                else
                {
                    // 2 breakends are close enough to cluster
                    if (var == nextVar)
                    {
                        if (cluster == null)
                        {
                            cluster = new SvCluster(getNextClusterId());
                            cluster.addVariant(var);
                            clusters.add(cluster);
                        }
                    }
                    else
                    {
                        // one or both SVs could already be a part of clusters, or neither may be
                        if (cluster == null && nextCluster == null)
                        {
                            cluster = new SvCluster(getNextClusterId());
                            cluster.addVariant(var);
                            cluster.addVariant(nextVar);
                            cluster.addClusterReason(CR_PROXIMITY);
                            clusters.add(cluster);
                        }
                        else if (cluster != null && nextCluster != null)
                        {
                            // keep one and remove the other
                            cluster.mergeOtherCluster(nextCluster, false);
                            cluster.addClusterReason(CR_PROXIMITY);
                            clusters.remove(nextCluster);
                        }
                        else
                        {
                            if (cluster == null)
                            {
                                nextCluster.addVariant(var);
                                nextCluster.addClusterReason(CR_PROXIMITY);
                            }
                            else
                            {
                                cluster.addVariant(nextVar);
                                cluster.addClusterReason(CR_PROXIMITY);
                            }
                        }

                        if (var.getClusterReason().isEmpty())
                            var.addClusterReason(CR_PROXIMITY, nextVar.id());

                        if (nextVar.getClusterReason().isEmpty())
                            nextVar.addClusterReason(CR_PROXIMITY, var.id());

                        // checkClusteringClonalDiscrepancy(var, nextVar, CR_PROXIMITY);
                    }
                }

                // move the index to the SV which was just proximity cluster so the next comparison is with the closest candidate
                currentIndex = nextIndex;
            }
        }
    }

    public static void addClusterReasons(final SvVarData var1, final SvVarData var2, final String clusterReason)
    {
        var1.addClusterReason(clusterReason, var2.id());
        var2.addClusterReason(clusterReason, var1.id());
        // checkClusteringClonalDiscrepancy(var1, var2, clusterReason);
    }

    public void mergeClusters(final String sampleId, List<SvCluster> clusters)
    {
        // first apply replication rules since this can affect consistency
        for(SvCluster cluster : clusters)
        {
            if(cluster.isResolved())
                continue;

            markClusterLongDelDups(cluster);
            markClusterInversions(cluster);
        }

        associateBreakendCnEvents(sampleId, clusters);

        int initClusterCount = clusters.size();

        int iterations = 0;

        mergeOnLOHEvents(clusters);

        if(NEW_CLUSTERING)
        {
            mergeOnMajorAllelePloidyBounds(clusters);

            mergeLOHResolvingClusters(clusters);
        }

        // the merge must be run a few times since as clusters grow, more single SVs and other clusters
        // will then fall within the bounds of the new larger clusters
        boolean foundMerges = true;

        while(foundMerges && iterations < 10)
        {
            if(mergeOnOverlappingInvDupDels(clusters))
                foundMerges = true;

            if(!NEW_CLUSTERING)
            {
                if (mergeOnUnresolvedSingles(clusters))
                    foundMerges = true;
            }

            ++iterations;
        }


        if(clusters.size() < initClusterCount)
        {
            LOGGER.debug("reduced cluster count({} -> {}) iterations({})", initClusterCount, clusters.size(), iterations);
        }
    }

    public static boolean hasLowCNChangeSupport(final SvVarData var)
    {
        if(var.type() == INS)
            return false;

        if(var.isSglBreakend())
            return var.copyNumberChange(true) < LOW_CN_CHANGE_SUPPORT;
        else
            return var.copyNumberChange(true) < LOW_CN_CHANGE_SUPPORT && var.copyNumberChange(false) < LOW_CN_CHANGE_SUPPORT;
    }

    private void associateBreakendCnEvents(final String sampleId, List<SvCluster> clusters)
    {
        // search for breakends that match LOH and Hom-loss events
        // note that LOH-breakend links are established here and then must be tidied up once the sample is complete

        String currentChromosome = "";
        List<SvBreakend> breakendList = null;

        int missedEvents = 0;

        if(mState.getLohEventList() != null && !mState.getLohEventList().isEmpty())
        {
            for (final LohEvent lohEvent : mState.getLohEventList())
            {
                if (!lohEvent.isSvEvent())
                    continue;

                // use the breakend table to find matching SVs
                if (breakendList == null || !currentChromosome.equals(lohEvent.Chromosome))
                {
                    breakendList = mState.getChrBreakendMap().get(lohEvent.Chromosome);
                    currentChromosome = lohEvent.Chromosome;
                }

                if (breakendList == null)
                    continue;

                for (final SvBreakend breakend : breakendList)
                {
                    if (breakend.orientation() == 1 && breakend.getSV().id() == lohEvent.StartSV)
                    {
                        lohEvent.setBreakend(breakend, true);
                        breakend.getCluster().addLohEvent(lohEvent);
                    }

                    if (breakend.orientation() == -1 && breakend.getSV().id() == lohEvent.EndSV)
                    {
                        lohEvent.setBreakend(breakend, false);
                        breakend.getCluster().addLohEvent(lohEvent);
                    }

                    if (lohEvent.matchedBothSVs())
                        break;
                }

                if (lohEvent.StartSV != CN_DATA_NO_SV && lohEvent.getBreakend(true) == null)
                    ++missedEvents;

                if (lohEvent.EndSV != CN_DATA_NO_SV && lohEvent.getBreakend(false) == null)
                    ++missedEvents;
            }
        }

        if(mState.getHomLossList() != null && !mState.getHomLossList().isEmpty())
        {
            for (HomLossEvent homLossEvent : mState.getHomLossList())
            {
                if (homLossEvent.StartSV == CN_DATA_NO_SV && homLossEvent.EndSV == CN_DATA_NO_SV)
                    continue;

                breakendList = mState.getChrBreakendMap().get(homLossEvent.Chromosome);

                if (breakendList == null)
                    continue;

                for (final SvBreakend breakend : breakendList)
                {
                    if (breakend.orientation() == 1 && breakend.getSV().id() == homLossEvent.StartSV)
                    {
                        homLossEvent.setBreakend(breakend, true);
                    }

                    if (breakend.orientation() == -1 && breakend.getSV().id() == homLossEvent.EndSV)
                    {
                        homLossEvent.setBreakend(breakend, false);
                    }

                    if (homLossEvent.matchedBothSVs())
                        break;
                }

                if (homLossEvent.StartSV != CN_DATA_NO_SV && homLossEvent.getBreakend(true) == null)
                    ++missedEvents;

                if (homLossEvent.EndSV != CN_DATA_NO_SV && homLossEvent.getBreakend(false) == null)
                    ++missedEvents;
            }
        }

        if(missedEvents > 0)
        {
            LOGGER.warn("sample({}) missed {} links to LOH and hom-loss events", sampleId, missedEvents);
        }
    }

    private void mergeOnLOHEvents(List<SvCluster> clusters)
    {
        if (mState.getLohEventList().isEmpty() && mState.getHomLossList().isEmpty())
            return;

        // first link up breakends joined by an LOH with no multi-SV hom-loss events within
        for (final LohEvent lohEvent : mState.getLohEventList())
        {
            if (!lohEvent.matchedBothSVs() || lohEvent.hasIncompleteHomLossEvents())
                continue; // cannot be used for clustering

            SvBreakend lohBeStart = lohEvent.getBreakend(true);
            SvCluster lohClusterStart = lohBeStart.getCluster();
            SvVarData lohSvStart = lohBeStart.getSV();
            SvBreakend lohBeEnd = lohEvent.getBreakend(false);
            SvCluster lohClusterEnd = lohBeEnd.getCluster();
            SvVarData lohSvEnd = lohBeEnd.getSV();

            if (lohClusterStart != lohClusterEnd)
            {
                if (!lohClusterStart.hasLinkingLineElements() && !lohClusterEnd.hasLinkingLineElements())
                {
                    LOGGER.debug("cluster({} svs={}) merges in other cluster({} svs={}) on LOH event: SVs({} and {}) length({})",
                            lohClusterStart.id(), lohClusterStart.getSvCount(), lohClusterEnd.id(), lohClusterEnd.getSvCount(),
                            lohEvent.StartSV, lohEvent.EndSV, lohEvent.Length);

                    addClusterReasons(lohSvStart, lohSvEnd, CR_LOH);
                    lohClusterStart.addClusterReason(CR_LOH);

                    lohClusterStart.mergeOtherCluster(lohClusterEnd);
                    clusters.remove(lohClusterEnd);
                }
            }
        }

        // search for LOH events which are clustered but which contain hom-loss events which aren't clustered
        // (other than simple DELs) which already will have been handled
        for (final LohEvent lohEvent : mState.getLohEventList())
        {
            if (!lohEvent.hasIncompleteHomLossEvents())
                continue;

            // already clustered - search for hom-loss events contained within this LOH that isn't clustered
            if(lohEvent.clustered() || lohEvent.wholeArmLoss())
            {
                List<HomLossEvent> unclusteredHomLossEvents = lohEvent.getHomLossEvents().stream()
                        .filter(HomLossEvent::matchedBothSVs)
                        .filter(x -> !x.sameSV())
                        .filter(x -> x.PosStart > lohEvent.PosStart)
                        .filter(x -> x.PosEnd < lohEvent.PosEnd)
                        .filter(x -> !x.clustered())
                        .collect(Collectors.toList());

                for (final HomLossEvent homLoss : unclusteredHomLossEvents)
                {
                    SvBreakend homLossBeStart = homLoss.getBreakend(true);
                    SvBreakend homLossBeEnd = homLoss.getBreakend(false);

                    SvCluster cluster = homLossBeStart.getCluster();
                    SvCluster otherCluster = homLossBeEnd.getCluster();

                    if(cluster == otherCluster) // protect against clusters already merged or removed
                        continue;

                    LOGGER.debug("cluster({} svs={}) merges in other cluster({} svs={}) on hom-loss({}: {} -> {}) inside LOH event({} -> {})",
                            cluster.id(), cluster.getSvCount(), otherCluster.id(), otherCluster.getSvCount(),
                            homLoss.Chromosome, homLoss.PosStart, homLoss.PosEnd, lohEvent.PosStart, lohEvent.PosEnd);

                    addClusterReasons(homLossBeStart.getSV(), homLossBeEnd.getSV(), CR_HOM_LOSS);
                    cluster.addClusterReason(CR_HOM_LOSS);

                    cluster.mergeOtherCluster(otherCluster);
                    clusters.remove(otherCluster);
               }

                continue;
            }

            if (!lohEvent.matchedBothSVs() || lohEvent.clustered())
                continue;

            if (lohEvent.getBreakend(true).getCluster().hasLinkingLineElements()
            || lohEvent.getBreakend(false).getCluster().hasLinkingLineElements())
            {
                continue;
            }

            // now look for an LOH with unclustered breakends but which contains only clustered hom-loss events
            boolean hasIncompleteHomLossEvent = false;

            for(final HomLossEvent homLossEvent : lohEvent.getHomLossEvents())
            {
                if(!(homLossEvent.PosStart > lohEvent.PosStart && homLossEvent.PosEnd < lohEvent.PosEnd))
                {
                    // handle overlapping separately
                    hasIncompleteHomLossEvent = true;
                    break;
                }

                if(!homLossEvent.matchedBothSVs())
                {
                    hasIncompleteHomLossEvent = true;
                    break;
                }

                if(!homLossEvent.clustered())
                {
                    hasIncompleteHomLossEvent = true;
                    break;
                }
            }

            if(!hasIncompleteHomLossEvent)
            {
                // all hom-loss events involving more than 1 SV were clustered, so clustered the LOH SVs
                SvBreakend lohBeStart = lohEvent.getBreakend(true);
                SvBreakend lohBeEnd = lohEvent.getBreakend(false);

                SvCluster cluster = lohBeStart.getCluster();
                SvCluster otherCluster = lohBeEnd.getCluster();

                lohEvent.setIsValid(true);

                LOGGER.debug("cluster({} svs={}) merges in other cluster({} svs={}) on no unclustered hom-loss events",
                        cluster.id(), cluster.getSvCount(), otherCluster.id(), otherCluster.getSvCount());

                addClusterReasons(lohBeStart.getSV(), lohBeEnd.getSV(), CR_HOM_LOSS);
                cluster.addClusterReason(CR_HOM_LOSS);

                cluster.mergeOtherCluster(otherCluster);
                clusters.remove(otherCluster);
            }

            // finally look for overlapping LOH and hom-loss events where all but 2 of the breakends are clustered
            // resulting in a clustering of a breakend from the LOH with one of the hom-loss breakends

            List<SvBreakend> unclusteredBreakends = Lists.newArrayList();
            boolean allBreakendsValid = true;

            for(int se = SE_START; se <= SE_END; ++se)
            {
                boolean isStart = isStart(se);
                unclusteredBreakends.add(lohEvent.getBreakend(isStart));

                for(final HomLossEvent homLossEvent : lohEvent.getHomLossEvents())
                {
                    if(!homLossEvent.matchedBothSVs())
                    {
                        allBreakendsValid = false;
                        break;
                    }

                    unclusteredBreakends.add(homLossEvent.getBreakend(isStart));
                }

                if(!allBreakendsValid)
                    break;
            }

            if(allBreakendsValid)
            {
                int i = 0;
                while(i < unclusteredBreakends.size())
                {
                    final SvBreakend be1 = unclusteredBreakends.get(i);

                    boolean found = false;
                    for(int j = i+1; j < unclusteredBreakends.size(); ++j)
                    {
                        final SvBreakend be2 = unclusteredBreakends.get(j);

                        if(be1.getCluster() == be2.getCluster())
                        {
                            unclusteredBreakends.remove(j);
                            unclusteredBreakends.remove(i);
                            found = true;
                            break;
                        }
                    }

                    if(!found)
                        ++i;
                }
            }

            if(unclusteredBreakends.size() == 2)
            {
                SvBreakend breakend1 = unclusteredBreakends.get(0);
                SvBreakend breakend2 = unclusteredBreakends.get(1);
                SvCluster cluster = breakend1.getCluster();
                SvCluster otherCluster = breakend2.getCluster();

                lohEvent.setIsValid(true);

                LOGGER.debug("cluster({} svs={}) merges in other cluster({} svs={}) on unclustered LOH and hom-loss breakends",
                        cluster.id(), cluster.getSvCount(), otherCluster.id(), otherCluster.getSvCount());

                addClusterReasons(breakend1.getSV(), breakend2.getSV(), CR_HOM_LOSS);
                cluster.addClusterReason(CR_HOM_LOSS);

                cluster.mergeOtherCluster(otherCluster);
                clusters.remove(otherCluster);
            }
        }
    }

    private void markClusterInversions(final SvCluster cluster)
    {
        if(cluster.getTypeCount(INV) == 0)
            return;

        // skip cluster-2s which resolved to a simple type and not long
        if(cluster.isResolved() && cluster.getResolvedType().isSimple())
            return;

        for (final SvVarData var : cluster.getSVs())
        {
            if(var.type() == INV && !var.isCrossArm())
            {
                cluster.registerInversion(var);
            }
        }
    }

    private void markClusterLongDelDups(final SvCluster cluster)
    {
        // find and record any long DEL or DUP for merging, including long synthetic ones
        if(cluster.isSyntheticType() && cluster.getResolvedType().isSimple() && !cluster.isResolved())
        {
            if ((cluster.getResolvedType() == ResolvedType.DEL && cluster.getSyntheticLength() >= mState.getDelCutoffLength())
            || (cluster.getResolvedType() == ResolvedType.DUP && cluster.getSyntheticLength() >= mState.getDupCutoffLength()))
            {
                for (final SvVarData var : cluster.getSVs())
                {
                    cluster.registerLongDelDup(var);
                }
            }
        }

        if(cluster.getTypeCount(DEL) > 0 || cluster.getTypeCount(DUP) > 0)
        {
            for(final SvVarData var : cluster.getSVs())
            {
                if(var.isCrossArm())
                    continue;

                if(exceedsDupDelCutoffLength(var.type(), var.length()))
                {
                    cluster.registerLongDelDup(var);
                }
            }
        }
    }

    private boolean mergeOnOverlappingInvDupDels(List<SvCluster> clusters)
    {
        // merge any clusters with overlapping inversions, long dels or long dups on the same arm
        int initClusterCount = clusters.size();

        final List<StructuralVariantType> requiredTypes = Lists.newArrayList();
        requiredTypes.add(INV);

        int index1 = 0;
        while(index1 < clusters.size())
        {
            SvCluster cluster1 = clusters.get(index1);

            if(cluster1.getInversions().isEmpty() && cluster1.getLongDelDups().isEmpty() || cluster1.hasLinkingLineElements())
            {
                ++index1;
                continue;
            }

            List<SvVarData> cluster1Svs = Lists.newArrayList();
            cluster1Svs.addAll(cluster1.getLongDelDups());
            cluster1Svs.addAll(cluster1.getInversions());

            int index2 = index1 + 1;
            while(index2 < clusters.size())
            {
                SvCluster cluster2 = clusters.get(index2);

                if(cluster2.getInversions().isEmpty() && cluster2.getLongDelDups().isEmpty() || cluster2.hasLinkingLineElements())
                {
                    ++index2;
                    continue;
                }

                // check for conflicting LOH / hom-loss events
                if(!canMergeClusters(cluster1, cluster2))
                {
                    LOGGER.debug("cluster({}) and cluster({}) have conflicting LOH & hom-loss events", cluster1.id(), cluster2.id());
                    ++index2;
                    continue;
                }

                List<SvVarData> cluster2Svs = Lists.newArrayList();
                cluster2Svs.addAll(cluster2.getLongDelDups());
                cluster2Svs.addAll(cluster2.getInversions());

                boolean canMergeClusters = false;

                for (final SvVarData var1 : cluster1Svs)
                {
                    for (final SvVarData var2 : cluster2Svs)
                    {
                        if(!var1.chromosome(true).equals(var2.chromosome(true)))
                            continue;

                        if(var1.position(false) < var2.position(true) || var1.position(true) > var2.position(false))
                            continue;

                        LOGGER.debug("cluster({}) SV({} {}) and cluster({}) SV({} {}) have inversion or longDelDup overlap",
                                cluster1.id(), var1.posId(), var1.type(), cluster2.id(), var2.posId(), var2.type());

                        addClusterReasons(var1, var2, CR_LONG_DEL_DUP_OR_INV);

                        canMergeClusters = true;
                        break;
                    }

                    if(canMergeClusters)
                        break;
                }

                if(canMergeClusters)
                {
                    cluster1.mergeOtherCluster(cluster2);
                    cluster1.addClusterReason(CR_LONG_DEL_DUP_OR_INV);
                    clusters.remove(index2);
                }
                else
                {
                    ++index2;
                }
            }

            ++index1;
        }

        return clusters.size() < initClusterCount;
    }

    protected static boolean canMergeClusters(final SvCluster cluster, final SvCluster otherCluster)
    {
        // if the 2 clusters have overlapping LOH and hom-loss events, they cannot be merged
        if(cluster.getLohEvents().stream().anyMatch(x -> x.getHomLossEvents().stream().anyMatch(y -> y.getCluster() == otherCluster)))
        {
            return false;
        }

        if(otherCluster.getLohEvents().stream().anyMatch(x -> x.getHomLossEvents().stream().anyMatch(y -> y.getCluster() == cluster)))
        {
            return false;
        }

        return true;
    }

    protected boolean exceedsDupDelCutoffLength(StructuralVariantType type, long length)
    {
        if(type == DEL)
            return length > mState.getDelCutoffLength();
        else if(type == DUP)
            return length > mState.getDelCutoffLength();
        else
            return false;
    }

    protected static boolean skipClusterType(final SvCluster cluster)
    {
        if(cluster.getResolvedType() == LINE)
            return true;

        if(isSimpleSingleSV(cluster) && cluster.getSV(0).getNearestSvRelation() == RELATION_TYPE_NEIGHBOUR)
            return true;

        if(cluster.isResolved())
            return true;

        return false;
    }

    private boolean mergeOnMajorAllelePloidyBounds(List<SvCluster> clusters)
    {
        /* The major allele ploidy of a segment is the maximum ploidy any derivative chromosome which includes that segment can have.
        Hence a breakend cannot chain completely across a region with major allele ploidy < ploidy of the breakend, or
        partially across the region with a chain of ploidy more than the major allele.

    	Therefore cluster any breakend with the next 1 or more facing breakends (excluding LINE & assembled & simple non overlapping DEL/DUP)
    	if the major allele ploidy in the segment immediately after the facing breakend is lower than the breakend ploidy - sum(facing breakend ploidies).
    	We limit this clustering to a proximity of 5 million bases and bounded by the centromere, since although more distal events are on
    	the same chromosome may be definitely on the same derivative chromosome, this does necessarily imply they occurred concurrently.
        */

        // method:
        // walk through each cluster's chromosome breakend lists in turn in both directions
        // from each breakend walk forward and subtract any facing breakend's ploidy in the same cluster
        // if an opposing unclustered breakend is encountered an the major AP in the segment after the unclustered breakend is less than
        // the clustered net breakend ploidy, then merge in the unclustered breakend, subtract its ploidy and continue

        List<SvCluster> mergedClusters = Lists.newArrayList();

        int clusterIndex = 0;
        while(clusterIndex < clusters.size())
        {
            SvCluster cluster = clusters.get(clusterIndex);

            if(mergedClusters.contains(cluster) || skipClusterType(cluster))
            {
                ++clusterIndex;
                continue;
            }

            // isSpecificCluster(cluster);

            boolean mergedOtherClusters = false;

            for (final Map.Entry<String, List<SvBreakend>> entry : cluster.getChrBreakendMap().entrySet())
            {
                List<SvBreakend> breakendList = entry.getValue();

                List<SvBreakend> fullBreakendList = mState.getChrBreakendMap().get(entry.getKey());

                // walk through this list from each direction
                for(int i = 0; i <= 1; ++i)
                {
                    boolean traverseUp = (i == 0);
                    int index = traverseUp ? -1 : breakendList.size(); // will be incremented on first pass

                    while(true)
                    {
                        index += traverseUp ? 1 : -1;

                        if(index < 0 || index >= breakendList.size())
                            break;

                        SvBreakend breakend = breakendList.get(index);

                        if((breakend.orientation() == -1) != traverseUp)
                            continue;

                        double breakendPloidy = breakend.ploidy();

                        // now walk from this location onwards using the full arm breakend list
                        int chrIndex = breakend.getChrPosIndex();

                        List<SvBreakend> opposingBreakends = Lists.newArrayList();

                        while(true)
                        {
                            chrIndex += traverseUp ? 1 : -1;

                            if(chrIndex < 0 || chrIndex >= fullBreakendList.size())
                                break;

                            SvBreakend nextBreakend = fullBreakendList.get(chrIndex);

                            if(abs(nextBreakend.position() - breakend.position()) > MAX_MERGE_DISTANCE)
                                break;

                            if(nextBreakend.arm() != breakend.arm())
                                break;

                            if(nextBreakend.orientation() == breakend.orientation())
                                continue;

                            if(skipClusterType(nextBreakend.getCluster()))
                                continue;

                            if(!canMergeClusters(cluster, nextBreakend.getCluster()))
                                continue;

                            if(nextBreakend.isAssembledLink())
                                continue;

                            if(nextBreakend.getCluster() == cluster)
                            {
                                breakendPloidy -= nextBreakend.ploidy();

                                if(breakendPloidy <= 0)
                                    break;

                                continue;
                            }

                            opposingBreakends.add(nextBreakend);

                            // should this next breakend be merged in?
                            double followingMajorAP = nextBreakend.majorAllelePloidy(!traverseUp);

                            if(!copyNumbersEqual(breakendPloidy, followingMajorAP) && breakendPloidy > followingMajorAP)
                            {
                                // take the highest of the opposing breakends which were encountered, and if the highest match, then the first
                                double maxOpposingPloidy = opposingBreakends.stream().mapToDouble(x -> x.ploidy()).max().getAsDouble();

                                SvBreakend opposingBreakend = opposingBreakends.stream()
                                        .filter(x -> copyNumbersEqual(x.ploidy(), maxOpposingPloidy)).findFirst().get();

                                SvCluster otherCluster = opposingBreakend.getCluster();

                                LOGGER.debug("cluster({}) breakend({} netPloidy={}) merges cluster({}) breakend({} ploidy={}) prior to MAP drop({})",
                                        cluster.id(), breakend, formatPloidy(breakendPloidy), otherCluster.id(), opposingBreakend.toString(),
                                        formatPloidy(opposingBreakend.ploidy()), formatPloidy(followingMajorAP));

                                addClusterReasons(breakend.getSV(), opposingBreakend.getSV(), CR_MAJOR_AP_PLOIDY);

                                otherCluster.addClusterReason(CR_MAJOR_AP_PLOIDY);
                                cluster.addClusterReason(CR_MAJOR_AP_PLOIDY);

                                cluster.mergeOtherCluster(otherCluster);

                                mergedClusters.add(otherCluster);

                                mergedOtherClusters = true;
                            }

                            break;
                        }

                        if(mergedOtherClusters)
                            break;
                    }

                    if(mergedOtherClusters)
                        break;
                }

                if(mergedOtherClusters)
                    break;
            }

            if(mergedOtherClusters)
            {
                // repeat this cluster
            }
            else
            {
                ++clusterIndex;
            }
        }

        if(mergedClusters.isEmpty())
            return false;

        mergedClusters.forEach(x -> clusters.remove(x));

        return true;
    }

    private boolean mergeOnUnresolvedSingles(List<SvCluster> clusters)
    {
        // merge clusters with 1 unresolved single with the following rules:
        // 2 x cluster-1s with SGLs that are each other's nearest neighbours
        //
        // use the chr-breakend map to walk through and find the closest links
        // only apply a rule between the 2 closest breakends at the exclusions of the cluster on their other end
        // unless the other breakend is a short, simple SV

        boolean foundMerges = false;

        for (final Map.Entry<String, List<SvBreakend>> entry : mState.getChrBreakendMap().entrySet())
        {
            final List<SvBreakend> breakendList = entry.getValue();
            int breakendCount = breakendList.size();

            for(int i = 0; i < breakendList.size(); ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                SvVarData var = breakend.getSV();
                final SvCluster cluster = var.getCluster();

                // take the point of view of the cluster with the solo single
                if(cluster.getSvCount() != 1 || cluster.getSV(0).type() != SGL || cluster.isResolved())
                    continue;

                // now look for a proximate cluster with either another solo single or needing one to be resolved
                // check previous and next breakend's cluster
                SvBreakend prevBreakend = (i > 0) ? breakendList.get(i - 1) : null;
                SvBreakend nextBreakend = (i < breakendCount - 1) ? breakendList.get(i + 1) : null;

                if(prevBreakend != null && prevBreakend.getSV().isSimpleType()
                && !exceedsDupDelCutoffLength(prevBreakend.getSV().type(), prevBreakend.getSV().length()))
                {
                    prevBreakend = null;
                }

                if(nextBreakend != null && nextBreakend.getSV().isSimpleType()
                && !exceedsDupDelCutoffLength(nextBreakend.getSV().type(), nextBreakend.getSV().length()))
                {
                    nextBreakend = null;
                }

                // additionally check that breakend after the next one isn't a closer SGL to the next breakend,
                // which would invalidate this one being the nearest neighbour
                long prevProximity = prevBreakend != null ? abs(breakend.position() - prevBreakend.position()) : -1;
                long nextProximity = nextBreakend != null ? abs(breakend.position() - nextBreakend.position()) : -1;

                if(nextBreakend != null && i < breakendCount - 2)
                {
                    final SvBreakend followingBreakend = breakendList.get(i + 2);
                    final SvCluster followingCluster = followingBreakend.getCluster();

                    if(followingCluster.getSvCount() == 1 && followingBreakend.getSV().type() == SGL && !followingCluster.isResolved())
                    {
                        long followingProximity = abs(nextBreakend.position() - followingBreakend.position());

                        if (followingProximity < nextProximity)
                            nextBreakend = null;
                    }
                }

                if(nextBreakend == null && prevBreakend == null)
                    continue;

                SvBreakend otherBreakend = null;

                if(nextBreakend != null && prevBreakend != null)
                {
                    otherBreakend = nextProximity < prevProximity ? nextBreakend : prevBreakend;
                }
                else if(nextBreakend != null)
                {
                    otherBreakend = nextBreakend;
                }
                else
                {
                    otherBreakend = prevBreakend;
                }

                SvVarData otherVar = otherBreakend.getSV();
                final SvCluster otherCluster = otherVar.getCluster();

                ResolvedType resolvedType = canResolveWithSoloSingle(otherCluster, cluster);

                if(resolvedType != NONE)
                {
                    otherCluster.mergeOtherCluster(cluster);
                    otherCluster.addClusterReason(CLUSTER_REASON_SOLO_SINGLE);
                    otherCluster.setResolved(true, resolvedType);
                    otherCluster.setSyntheticData(abs(otherVar.position(true) - var.position(true)), 0);

                    clusters.remove(cluster);
                    foundMerges = true;
                    break;
                }
            }
        }

        return foundMerges;
    }

    private final ResolvedType canResolveWithSoloSingle(SvCluster otherCluster, SvCluster soloSingleCluster)
    {
        // 3 cases:
        // - 2 x SGLs could form a simple DEL or DUP
        // - 2 x SGLs + another SV could form a simple cluster-2 resolved type
        // - a SGL + another SV could form a simple cluster-2 resolved type

        final SvVarData soloSingle = soloSingleCluster.getSV(0);

        if(otherCluster.getSvCount() == 1)
        {
            final SvVarData otherVar = otherCluster.getSV(0);

            if(otherVar.type() == SGL)
            {
                // either both must be NONEs or one be a SGL but without centromeric or telomeric support
                if(otherCluster.isResolved())
                    return NONE;

                ResolvedType resolvedType = markSinglePairResolvedType(otherVar, soloSingle);

                if(resolvedType == NONE)
                    return NONE;

                LOGGER.debug("cluster({}) SV({}) and cluster({}) SV({}) syntheticType({})",
                        soloSingleCluster.id(), soloSingle.posId(), otherCluster.id(), otherVar.posId(), resolvedType);

                addClusterReasons(soloSingle, otherVar, CLUSTER_REASON_SOLO_SINGLE);

                return resolvedType;
            }
            else
            {
                boolean inconsistentOnStart;
                if(otherVar.hasInconsistentCopyNumberChange(true) && otherVar.chromosome(false).equals(soloSingle.chromosome(true)))
                {
                    inconsistentOnStart = true;
                }
                else if(otherVar.hasInconsistentCopyNumberChange(false) && otherVar.chromosome(true).equals(soloSingle.chromosome(true)))
                {
                    inconsistentOnStart = false;
                }
                else
                {
                    return NONE;
                }

                double cnInconsistency = otherVar.ploidy() - otherVar.copyNumberChange(inconsistentOnStart);

                if(round(cnInconsistency) != round(soloSingle.copyNumberChange(true)))
                    return NONE;

                LOGGER.debug(String.format("cluster(%s) SV(%s) and cluster(%s) SV(%s) potentially resolve CN inconsistency(%.2f vs %.2f)",
                        soloSingleCluster.id(), soloSingle.posId(), otherCluster.id(), otherVar.posId(),
                        cnInconsistency, soloSingle.copyNumberChange(true)));

                addClusterReasons(soloSingle, otherVar, CLUSTER_REASON_SOLO_SINGLE);

                return PAIR_OTHER;
            }
        }
        else
        {
            return NONE;
        }
    }

    private boolean mergeLOHResolvingClusters(List<SvCluster> clusters)
    {
        // merge clusters if one resolves another's LOH event with a DUP on one side
        List<SvCluster> clustersWithLohEvents = clusters.stream()
                .filter(x -> !x.getLohEvents().isEmpty())
                .filter(x -> !x.hasLinkingLineElements())
                .collect(Collectors.toList());

        List<SvCluster> mergedClusters = Lists.newArrayList();

        for(SvCluster lohCluster : clustersWithLohEvents)
        {
            if(mergedClusters.contains(lohCluster)) // if this has LOH events they will have been added to the parent cluster
                continue;

            List<LohEvent> lohEvents = lohCluster.getLohEvents();

            int lohIndex = 0; // used since merging another cluster can add more LOH events
            while(lohIndex < lohEvents.size())
            {
                LohEvent lohEvent = lohEvents.get(lohIndex);

                if(!lohEvent.isValid())
                {
                    ++lohIndex;
                    continue;
                }

                for(int be = SE_START; be <= SE_END; ++be)
                {
                    SvBreakend lohBreakend = lohEvent.getBreakend(isStart(be));

                    if(lohBreakend == null || lohBreakend.getSV().type() != DUP)
                        continue;

                    // it's possible that the breakends for this LOH are not clustered, eg if one is LINE
                    if(lohBreakend.getCluster() != lohCluster)
                        continue;

                    // walk towards the LOH from the other end of this DUP to see if it can find a resolving event within the cluster
                    List<SvBreakend> fullBreakendList = mState.getChrBreakendMap().get(lohBreakend.chromosome());

                    SvBreakend otherBreakend = lohBreakend.getOtherBreakend();
                    int index = otherBreakend.getChrPosIndex();
                    boolean traverseUp = otherBreakend.orientation() == -1;
                    SvCluster resolvingCluster = null;
                    SvBreakend resolvingBreakend = null;

                    while(true)
                    {
                        index += traverseUp ? 1 : -1;

                        if(index < 0 || index >= fullBreakendList.size())
                            break;

                        SvBreakend nextBreakend = fullBreakendList.get(index);

                        if(nextBreakend == lohBreakend)
                        {
                            // the LOH was reached without finding an offsetting SV
                            if(resolvingCluster == null)
                                break;

                            LOGGER.debug("cluster({}) SV({}) resolved prior to LOH by other cluster({}) breakend({})",
                                    lohCluster.id(), lohBreakend.getSV().posId(), resolvingCluster.id(), resolvingBreakend.toString());

                            addClusterReasons(lohBreakend.getSV(), resolvingBreakend.getSV(), CR_LOH_CHAIN);

                            resolvingCluster.addClusterReason(CR_LOH_CHAIN);
                            lohCluster.addClusterReason(CR_LOH_CHAIN);

                            lohCluster.mergeOtherCluster(resolvingCluster);

                            mergedClusters.add(resolvingCluster);
                            break;
                        }

                        if(nextBreakend.orientation() == otherBreakend.orientation())
                            continue;

                        SvCluster otherCluster = nextBreakend.getCluster();

                        if(otherCluster == lohCluster)
                            break; // own cluster resolves this LOH breakend

                        if(mergedClusters.contains(otherCluster) || otherCluster.isResolved())
                            continue;

                        if(resolvingCluster != null)
                            break; // cannot apply this rule if more than 1 cluster meet the conditions

                        // found an option, but continue on to see if any other clusters also satisfy the same conditions
                        resolvingBreakend = nextBreakend;
                        resolvingCluster = otherCluster;
                    }
                }

                ++lohIndex;
            }
        }

        if(mergedClusters.isEmpty())
            return false;

        mergedClusters.forEach(x -> clusters.remove(x));
        return true;
    }


    public boolean validateClustering(final List<SvCluster> clusters)
    {
        // validation that every SV was put into a cluster
        for (final Map.Entry<String, List<SvBreakend>> entry : mState.getChrBreakendMap().entrySet())
        {
            final List<SvBreakend> breakendList = entry.getValue();

            for (int i = 0; i < breakendList.size(); ++i)
            {
                final SvBreakend breakend = breakendList.get(i);
                SvVarData var = breakend.getSV();
                if(var.getCluster() == null)
                {
                    LOGGER.error("var({}) not clustered", var.posId());
                    return false;
                }
            }
        }

        // check that no 2 clusters contain the same SV
        for(int i = 0; i < clusters.size(); ++i)
        {
            SvCluster cluster1 = clusters.get(i);
            // isSpecificCluster(cluster1);

            // check all SVs in this cluster reference it
            for(SvVarData var : cluster1.getSVs())
            {
                if(var.getCluster() != cluster1)
                {
                    LOGGER.error("var({}) in cluster({}) has incorrect ref", var.posId(), cluster1.id());
                    return false;
                }
            }

            for(int j = i+1; j < clusters.size(); ++j)
            {
                SvCluster cluster2 = clusters.get(j);

                for(SvVarData var : cluster1.getSVs())
                {
                    if(cluster2.getSVs().contains(var))
                    {
                        LOGGER.error("var({}) in 2 clusters({} and {})", var.posId(), cluster1.id(), cluster2.id());
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public static boolean checkClusterDuplicates(List<SvCluster> clusters)
    {
        for(int i = 0; i < clusters.size(); ++i)
        {
            final SvCluster cluster1 = clusters.get(i);
            for(int j = i + 1; j < clusters.size(); ++j)
            {
                final SvCluster cluster2 = clusters.get(j);

                if(cluster1 == cluster2 || cluster1.id() == cluster2.id())
                {
                    LOGGER.error("cluster({}) exists twice in list", cluster1.id());
                    return false;
                }
            }
        }

        return true;
    }

}
