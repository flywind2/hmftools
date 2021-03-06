package com.hartwig.hmftools.common.purple.copynumber;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.purple.region.FittedRegion;
import com.hartwig.hmftools.common.purple.region.GermlineStatus;
import com.hartwig.hmftools.common.purple.region.ModifiableFittedRegion;
import com.hartwig.hmftools.common.utils.Doubles;

import org.jetbrains.annotations.NotNull;

class CombinedRegionImpl implements CombinedRegion {

    private final ModifiableFittedRegion combined;

    private CopyNumberMethod copyNumberMethod = CopyNumberMethod.UNKNOWN;
    private boolean inferredBAF;
    private final List<FittedRegion> regions = Lists.newArrayList();
    private int unweightedCount = 1;
    private final boolean isBafWeighted;

    CombinedRegionImpl(@NotNull final FittedRegion region) {
        this(true, region);
    }

    CombinedRegionImpl(boolean isBafWeighed, @NotNull final FittedRegion region) {
        this.combined = ModifiableFittedRegion.create().from(region);
        this.isBafWeighted = isBafWeighed;

        if (region.status() != GermlineStatus.DIPLOID) {
            clearBAFValues();
        }
        regions.add(region);
    }

    @NotNull
    @Override
    public String chromosome() {
        return combined.chromosome();
    }

    @Override
    public long start() {
        return combined.start();
    }

    @Override
    public long end() {
        return combined.end();
    }

    public boolean isInferredBAF() {
        return inferredBAF;
    }

    @NotNull
    public List<FittedRegion> regions() {
        return regions;
    }

    public double tumorCopyNumber() {
        return combined.tumorCopyNumber();
    }

    public double tumorBAF() {
        return combined.tumorBAF();
    }

    public int bafCount() {
        return region().bafCount();
    }

    @NotNull
    public CopyNumberMethod copyNumberMethod() {
        return copyNumberMethod;
    }

    public boolean isProcessed() {
        return copyNumberMethod != CopyNumberMethod.UNKNOWN;
    }

    public void setCopyNumberMethod(@NotNull CopyNumberMethod copyNumberMethod) {
        this.copyNumberMethod = copyNumberMethod;
    }

    @NotNull
    public FittedRegion region() {
        return combined;
    }

    public void extend(@NotNull final FittedRegion region) {
        combined.setStart(Math.min(combined.start(), region.start()));
        combined.setEnd(Math.max(combined.end(), region.end()));
        combined.setMinStart(Math.min(region.minStart(), region().minStart()));
        combined.setMaxStart(Math.min(region.maxStart(), region().maxStart()));


        if (region.start() <= combined.start()) {
            regions.add(0, region);
            combined.setSupport(region.support());
            combined.setRatioSupport(region.ratioSupport());
        } else {
            regions.add(region);
        }
    }

    public void extendWithUnweightedAverage(@NotNull final FittedRegion region) {
        extend(region);
        applyDepthWindowCountWeights(region, unweightedCount, 1);
        applyBafCountWeights(region, unweightedCount, 1);
        unweightedCount++;
    }

    public void extendWithWeightedAverage(@NotNull final FittedRegion region) {
        combined.setStatus(GermlineStatus.DIPLOID);

        int currentWeight = region().depthWindowCount();
        int newWeight = region.depthWindowCount();

        applyDepthWindowCountWeights(region, currentWeight, newWeight);

        if (isBafWeighted && (combined.bafCount() > 0 || region.bafCount() > 0)) {
            currentWeight = combined.bafCount();
            newWeight = region.bafCount();
        }
        applyBafCountWeights(region, currentWeight, newWeight);

        extend(region);
    }

    private void applyBafCountWeights(final FittedRegion region, long currentWeight, long newWeight) {
        if (!Doubles.isZero(region.observedBAF())) {
            combined.setObservedBAF(weightedAverage(currentWeight, combined.observedBAF(), newWeight, region.observedBAF()));
        }

        if (!Doubles.isZero(region.tumorBAF())) {
            combined.setTumorBAF(weightedAverage(currentWeight, combined.tumorBAF(), newWeight, region.tumorBAF()));
        }

        combined.setBafCount(combined.bafCount() + region.bafCount());
    }

    private void applyDepthWindowCountWeights(final FittedRegion region, long currentWeight, long newWeight) {

        if (!Doubles.isZero(region.tumorCopyNumber())) {
            combined.setTumorCopyNumber(weightedAverage(currentWeight, combined.tumorCopyNumber(), newWeight, region.tumorCopyNumber()));
        }

        if (!Doubles.isZero(region.refNormalisedCopyNumber())) {
            combined.setRefNormalisedCopyNumber(weightedAverage(currentWeight,
                    combined.refNormalisedCopyNumber(),
                    newWeight,
                    region.refNormalisedCopyNumber()));
        }

        if (!Doubles.isZero(region.gcContent())) {
            combined.setGcContent(weightedAverage(currentWeight, combined.gcContent(), newWeight, region.gcContent()));
        }

        combined.setDepthWindowCount(combined.depthWindowCount() + region.depthWindowCount());
    }

    public void setTumorCopyNumber(@NotNull final CopyNumberMethod method, double copyNumber) {
        setCopyNumberMethod(method);
        combined.setTumorCopyNumber(copyNumber);
    }

    public void setInferredTumorBAF(double baf) {
        inferredBAF = true;
        combined.setTumorBAF(baf);
        combined.setBafCount(0);
        combined.setObservedBAF(0);
    }

    private double weightedAverage(long currentWeight, double currentValue, long newWeight, double newValue) {
        if (Doubles.isZero(currentValue)) {
            return newValue;
        }

        long totalWeight = currentWeight + newWeight;
        return (currentWeight * currentValue + newWeight * newValue) / totalWeight;
    }

    private void clearBAFValues() {
        combined.setBafCount(0);
    }

}
