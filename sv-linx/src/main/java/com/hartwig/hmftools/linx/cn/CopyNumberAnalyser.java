package com.hartwig.hmftools.linx.cn;

import static java.lang.Math.abs;
import static java.lang.Math.floor;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import static com.hartwig.hmftools.common.purple.segment.SegmentSupport.CENTROMERE;
import static com.hartwig.hmftools.common.purple.segment.SegmentSupport.TELOMERE;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.linx.LinxConfig.DATA_OUTPUT_DIR;
import static com.hartwig.hmftools.linx.LinxConfig.DB_PASS;
import static com.hartwig.hmftools.linx.LinxConfig.DB_URL;
import static com.hartwig.hmftools.linx.LinxConfig.DB_USER;
import static com.hartwig.hmftools.linx.LinxConfig.LOG_DEBUG;
import static com.hartwig.hmftools.linx.LinxConfig.SAMPLE;
import static com.hartwig.hmftools.linx.LinxConfig.databaseAccess;
import static com.hartwig.hmftools.linx.LinxConfig.formOutputPath;
import static com.hartwig.hmftools.linx.LinxConfig.sampleListFromConfigStr;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.copyNumbersEqual;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.formatPloidy;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.getChromosomalArmLength;
import static com.hartwig.hmftools.linx.analysis.SvUtilities.getChromosomeLength;
import static com.hartwig.hmftools.linx.types.ChromosomeArm.P_ARM;
import static com.hartwig.hmftools.linx.types.ChromosomeArm.Q_ARM;

import java.io.BufferedWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.common.variant.structural.StructuralVariantData;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;

public class CopyNumberAnalyser
{

    private boolean mWriteLohEvents;
    private boolean mWritePloidyCalcs;
    private boolean mWriteChrArmData;
    private boolean mWriteCnSegmentStats;

    private final String mOutputPath;
    private final DatabaseAccess mDbAccess;

    private final CnDataLoader mCnDataLoader;

    private final List<String> mSampleIds;
    private BufferedWriter mLohEventWriter;
    private BufferedWriter mPloidyCalcWriter;
    private BufferedWriter mChrArmWriter;
    private BufferedWriter mCnSegmentWriter;

    private PerformanceCounter mPerfCounter;

    private static final String WRITE_LOH_TO_FILE = "write_loh_data";
    private static final String WRITE_PLOIDY_TO_FILE = "write_ploidy_data";
    private static final String WRITE_CHR_ARM_DATA = "write_chr_arm_data";
    private static final String WRITE_CN_SEGMENT_DATA = "write_cn_segment_data";

    private static final Logger LOGGER = LogManager.getLogger(CopyNumberAnalyser.class);

    public CopyNumberAnalyser(final String outputPath, DatabaseAccess dbAccess)
    {
        mDbAccess = dbAccess;
        mOutputPath = outputPath;
        mSampleIds = Lists.newArrayList();

        mCnDataLoader = new CnDataLoader("", dbAccess);

        mPerfCounter = new PerformanceCounter("CnAnalysis");

        mWriteChrArmData = false;
        mWriteLohEvents = false;
        mWritePloidyCalcs = false;

        mLohEventWriter = null;
        mPloidyCalcWriter = null;
        mChrArmWriter = null;
        mCnSegmentWriter = null;
    }

    public static void addCmdLineArgs(Options options)
    {
        options.addOption(DATA_OUTPUT_DIR, true, "Output directory");
        options.addOption(DB_USER, true, "Database user name");
        options.addOption(DB_PASS, true, "Database password");
        options.addOption(DB_URL, true, "Database URL");
        options.addOption(SAMPLE, true, "Sample(s) or CSV file with sample IDs");
        options.addOption(WRITE_LOH_TO_FILE, false, "Write LOH events to CSV");
        options.addOption(WRITE_PLOIDY_TO_FILE, false, "Write adjusted ploidy to CSV");
        options.addOption(WRITE_CHR_ARM_DATA, false, "Write chromosomal arm data");
        options.addOption(WRITE_CN_SEGMENT_DATA, false, "Write CN segment data to CSV");
        options.addOption(LOG_DEBUG, false, "Log verbose");
    }

    public boolean loadConfig(final CommandLine cmd)
    {
        if(cmd.hasOption(SAMPLE))
        {
            mSampleIds.addAll(sampleListFromConfigStr(cmd.getOptionValue(SAMPLE)));
        }

        mWritePloidyCalcs = cmd.hasOption(WRITE_PLOIDY_TO_FILE);
        mWriteLohEvents = cmd.hasOption(WRITE_LOH_TO_FILE);
        mWriteChrArmData = cmd.hasOption(WRITE_CHR_ARM_DATA);
        mWriteCnSegmentStats = cmd.hasOption(WRITE_CN_SEGMENT_DATA);
        return true;
    }

    public void runAnalysis()
    {
        final List<String> samplesList = mSampleIds.isEmpty() ? mDbAccess.readPurpleSampleList() : mSampleIds;

        int sampleCount = 0;
        for (final String sampleId : samplesList)
        {
            mPerfCounter.start();

            List<StructuralVariantData> svRecords = mDbAccess.readStructuralVariantData(sampleId);

            if (svRecords.isEmpty())
            {
                continue;
            }

            LOGGER.info("analysing sample({}), totalProcessed({})", sampleId, sampleCount);

            mCnDataLoader.loadSampleData(sampleId, svRecords);

            if(mWriteLohEvents)
                writeLohData(sampleId);

            if(mWritePloidyCalcs)
                writePloidyCalcData(sampleId);

            if(mWriteChrArmData)
                writeChrArmData(sampleId);

            if(mWriteCnSegmentStats)
                writeCnSegmentStats(sampleId);

            ++sampleCount;

            mPerfCounter.stop();
        }

        mPerfCounter.logStats();
    }

    private static final long CN_SEGMENT_WINDOW_SIZE = 3000000;

    private void writeCnSegmentStats(final String sampleId)
    {
        try
        {
            if (mCnSegmentWriter == null)
            {
                String outputFileName = mOutputPath + "LNX_CN_CHANGE_SEGMENTS.csv";

                mCnSegmentWriter = createBufferedWriter(outputFileName, false);

                mCnSegmentWriter.write("SampleId,IsMale,Chromosome,CnChange,Frequency");
                // mCnSegmentWriter.write("SampleId,IsMale,Chromosome,Ploidy,ElevSegCount");
                mCnSegmentWriter.newLine();
            }

            final Map<String,List<SvCNData>> chrCnDataMap = mCnDataLoader.getChrCnDataMap();

            double samplePloidy = mCnDataLoader.getPurityContext().bestFit().ploidy();
            boolean isMale = mCnDataLoader.getPurityContext().gender().toString().startsWith("MALE");

            LOGGER.debug("sample({}) ploidy({})", sampleId, formatPloidy(samplePloidy));

            for(HumanChromosome chrEntry : HumanChromosome.values())
            {
                final String chromosome = chrEntry.toString();
                final List<SvCNData> cnDataList = chrCnDataMap.get(chromosome);

                if(!isMale && chromosome.equals("Y"))
                    continue;

                if(cnDataList.isEmpty())
                {
                    // account for no segments with CN change
                    long chromosomeLength = getChromosomeLength(chromosome);
                    int windowCount = (int)(chromosomeLength/CN_SEGMENT_WINDOW_SIZE);

                    mCnSegmentWriter.write(String.format("%s,%s,%s,%d,%d",
                            sampleId, isMale, chromosome, 0, windowCount));
                    mCnSegmentWriter.newLine();

                    continue;
                }

                final Map<Integer,Integer> cnFrequency = Maps.newHashMap();
                cnFrequency.put(0, 0);

                long windowStartPos = 0;
                long windowEndPos = windowStartPos + CN_SEGMENT_WINDOW_SIZE;

                // account for first and last segments
                final SvCNData firstSegment = cnDataList.get(0);
                double prevCopyNumber = cnDataList.get(0).CopyNumber;

                for(int i = 1; i < cnDataList.size(); ++i)
                {
                    final SvCNData cnData = cnDataList.get(i);

                    if(cnData.StartPos < windowEndPos)
                        continue;

                    windowEndPos = (long)floor(cnData.StartPos/(double)CN_SEGMENT_WINDOW_SIZE)*CN_SEGMENT_WINDOW_SIZE;

                    // account for the distance out the telomere for the last segment
                    int skippedWindowCount = (int)((windowEndPos - windowStartPos) / CN_SEGMENT_WINDOW_SIZE);

                    if(!copyNumbersEqual(prevCopyNumber, cnData.CopyNumber))
                    {
                        double cnChange = abs(prevCopyNumber - cnData.CopyNumber);
                        int cnChangeInt = (int)max(round(cnChange), 1);

                        if(cnFrequency.containsKey(cnChangeInt))
                            cnFrequency.put(cnChangeInt, cnFrequency.get(cnChangeInt) + 1);
                        else
                            cnFrequency.put(cnChangeInt, 1);

                        --skippedWindowCount;
                    }

                    // account for segments without change
                    if(skippedWindowCount > 0)
                    {
                        cnFrequency.put(0, cnFrequency.get(0) + skippedWindowCount);
                    }

                    windowStartPos = windowEndPos;
                    windowEndPos = windowStartPos + CN_SEGMENT_WINDOW_SIZE;
                    prevCopyNumber = cnData.CopyNumber;
                }

                // account for the last segment out to the telomere
                final SvCNData lastSegment = cnDataList.get(cnDataList.size() - 1);
                int skippedWindowCount = (int)((lastSegment.EndPos - lastSegment.StartPos) / CN_SEGMENT_WINDOW_SIZE);

                if(skippedWindowCount > 0)
                    cnFrequency.put(0, cnFrequency.get(0) + skippedWindowCount);


                for(Map.Entry<Integer,Integer> cnCntry : cnFrequency.entrySet())
                {
                    mCnSegmentWriter.write(String.format("%s,%s,%s,%d,%d",
                            sampleId, isMale, chromosome, cnCntry.getKey(), cnCntry.getValue()));
                    mCnSegmentWriter.newLine();
                }


                /*
                long lastWindowStart = 0;
                double cnWindowTotal = 0;
                int elevatedCnWindows = 0;

                for(final SvCNData cnData : cnDataList)
                {
                    if(cnData.EndPos - lastWindowStart < CN_SEGMENT_WINDOW_SIZE && !cnData.SegEnd.equals(TELOMERE.toString()))
                    {
                        cnWindowTotal += cnData.CopyNumber * cnData.length();
                    }
                    else
                    {
                        long segmentStartPos = cnData.StartPos;

                        while(true)
                        {
                            // take each segment spanning the required window distance
                            long segmentEndPos = min(cnData.EndPos, lastWindowStart + CN_SEGMENT_WINDOW_SIZE);
                            cnWindowTotal += cnData.CopyNumber * (segmentEndPos - segmentStartPos);

                            if(cnData.EndPos - lastWindowStart < CN_SEGMENT_WINDOW_SIZE)
                                break;

                            double avgCopyNumber = cnWindowTotal / CN_SEGMENT_WINDOW_SIZE;

                            if (avgCopyNumber > samplePloidy && !copyNumbersEqual(avgCopyNumber, samplePloidy))
                                ++elevatedCnWindows;

                            LOGGER.debug("segment({}) windowStart({}) avgCN({}) elevatedWindows({})",
                                    cnData.toString(), lastWindowStart, formatPloidy(avgCopyNumber), elevatedCnWindows);

                            //if(cnData.EndPos <= segmentEndPos)
                            //    break;

                            cnWindowTotal = 0;
                            lastWindowStart = segmentEndPos;
                            segmentStartPos = lastWindowStart;
                        }
                    }
                }

                mCnSegmentWriter.write(String.format("%s,%s,%s,%.2f,%d",
                        sampleId, isMale, chromosome, samplePloidy, elevatedCnWindows));
                */

                mCnSegmentWriter.newLine();
            }
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing to copy number segments outputFile: {}", e.toString());
        }
    }

    private void writeChrArmData(final String sampleId)
    {
        try
        {
            if (mChrArmWriter == null)
            {
                String outputFileName = mOutputPath + "LNX_CN_CHR_ARM_DATA.csv";

                mChrArmWriter = createBufferedWriter(outputFileName, false);

                mChrArmWriter.write("SampleId,IsMale,Chromosome,Ploidy,WholeChrLoh,LohP,LohQ,CentroCnP,CentroCnQ,TeloCnP,TeloCnQ");
                mChrArmWriter.write(",AvgCnP,AvgCnQ,MedianCnP,MedianCnQ,MaxCnP,MaxCnQ,MinCnP,MinCnQ,SegCountP,SegCountQ");
                mChrArmWriter.newLine();
            }

            // record: sampleId, sample ploidy, whole-arm LOH,
            // record for each arm: LOH, centromere and telomere copy number, avg and median copy number

            final Map<String,List<SvCNData>> chrCnDataMap = mCnDataLoader.getChrCnDataMap();

            double samplePloidy = mCnDataLoader.getPurityContext().bestFit().ploidy();
            boolean isMale = mCnDataLoader.getPurityContext().gender().toString().startsWith("MALE");

            final List<LohEvent> lohEvents = mCnDataLoader.getLohData();

            for(Map.Entry<String,List<SvCNData>> entry : chrCnDataMap.entrySet())
            {
                final String chromosome = entry.getKey();

                if(!isMale && chromosome.equals("Y"))
                    continue;

                final List<SvCNData> cnDataList = entry.getValue();

                final CnArmStats[] armStats = calcArmStats(chromosome, cnDataList, lohEvents);

                boolean hasChrLoh = lohEvents.stream().anyMatch(x -> x.Chromosome.equals(chromosome) && x.chromosomeLoss());

                mChrArmWriter.write(String.format("%s,%s,%s,%.2f,%s,%s,%s",
                        sampleId, isMale, chromosome, samplePloidy,
                        hasChrLoh, armStats[P_ARM_INDEX].HasLOH, armStats[Q_ARM_INDEX].HasLOH));

                mChrArmWriter.write(String.format(",%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d",
                        armStats[P_ARM_INDEX].CentromereCopyNumber, armStats[Q_ARM_INDEX].CentromereCopyNumber,
                        armStats[P_ARM_INDEX].TelomereCopyNumber, armStats[Q_ARM_INDEX].TelomereCopyNumber,
                        armStats[P_ARM_INDEX].AverageCopyNumber, armStats[Q_ARM_INDEX].AverageCopyNumber,
                        armStats[P_ARM_INDEX].MedianCopyNumber, armStats[Q_ARM_INDEX].MedianCopyNumber,
                        armStats[P_ARM_INDEX].MaxCopyNumber, armStats[Q_ARM_INDEX].MaxCopyNumber,
                        armStats[P_ARM_INDEX].MinCopyNumber, armStats[Q_ARM_INDEX].MinCopyNumber,
                        armStats[P_ARM_INDEX].SegmentCount, armStats[Q_ARM_INDEX].SegmentCount));

                mChrArmWriter.newLine();

            }
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing to copy number chr-arm outputFile: {}", e.toString());
        }
    }

    private static int P_ARM_INDEX = 0;
    private static int Q_ARM_INDEX = 1;

    private static int SEG_LENGTH_INDEX = 0;
    private static int SEG_CN_INDEX = 1;

    private CnArmStats[] calcArmStats(final String chromosome, final List<SvCNData> cnDataList, final List<LohEvent> lohEvents)
    {
        CnArmStats[] armStats = new CnArmStats[Q_ARM_INDEX+1];
        armStats[P_ARM_INDEX] = new CnArmStats(chromosome, P_ARM);
        armStats[Q_ARM_INDEX] = new CnArmStats(chromosome, Q_ARM);

        int armIndex = P_ARM_INDEX;

        double baseDistanceCopyNumber = 0;
        final List<double[]> cnSegments = Lists.newArrayList(); // order by copy number ascending

        for(int i = 0; i < cnDataList.size(); ++i)
        {
            final SvCNData cnData = cnDataList.get(i);

            double copyNumber = cnData.CopyNumber;

            if(cnData.matchesSegment(TELOMERE, true))
            {
                armStats[armIndex].TelomereCopyNumber = copyNumber;
            }
            else if(cnData.matchesSegment(CENTROMERE, true))
            {
                armIndex = Q_ARM_INDEX;

                cnSegments.clear();
                baseDistanceCopyNumber = 0;

                armStats[armIndex].CentromereCopyNumber = copyNumber;
            }

            ++armStats[armIndex].SegmentCount;

            long segLength = cnData.length();

            armStats[armIndex].MaxCopyNumber = max(armStats[armIndex].MaxCopyNumber, copyNumber);

            if(armStats[armIndex].MinCopyNumber == -1 || copyNumber < armStats[armIndex].MinCopyNumber)
                armStats[armIndex].MinCopyNumber = copyNumber;

            baseDistanceCopyNumber += segLength * copyNumber;

            int index = 0;
            while(index < cnSegments.size())
            {
                if(cnSegments.get(index)[SEG_CN_INDEX] > copyNumber)
                    break;

                ++index;
            }

            cnSegments.add(index, new double[] {(double)segLength, copyNumber});

            if(cnData.matchesSegment(CENTROMERE, false))
            {
                armStats[armIndex].CentromereCopyNumber = copyNumber;
                calcArmCopyNumber(armStats[armIndex], baseDistanceCopyNumber, cnSegments);
            }
            else if(cnData.matchesSegment(TELOMERE, false))
            {
                armStats[armIndex].TelomereCopyNumber = copyNumber;
                calcArmCopyNumber(armStats[armIndex], baseDistanceCopyNumber, cnSegments);
            }
        }

        armStats[P_ARM_INDEX].HasLOH = lohEvents.stream().anyMatch(x -> x.Chromosome.equals(chromosome) && x.armLoss(P_ARM));
        armStats[Q_ARM_INDEX].HasLOH = lohEvents.stream().anyMatch(x -> x.Chromosome.equals(chromosome) && x.armLoss(Q_ARM));

        return armStats;
    }

    private void calcArmCopyNumber(CnArmStats armStats, double baseDistanceCopyNumber, final List<double[]> cnSegments)
    {
        long armLength = getChromosomalArmLength(armStats.Chromosome, armStats.Arm);
        double halfArmLength = armLength * 0.5;

        armStats.AverageCopyNumber = baseDistanceCopyNumber / armLength;

        double cumulativeLength = 0;

        for(int i = 0; i < cnSegments.size(); ++i)
        {
            final double[] segment = cnSegments.get(i);
            double segLength = segment[SEG_LENGTH_INDEX];

            if(cumulativeLength + segLength >= halfArmLength)
            {
                if (i > 0)
                {
                    final double[] prevSegment = cnSegments.get(i-1);
                    double prevCopyNumber = cumulativeLength / halfArmLength * prevSegment[SEG_CN_INDEX];
                    double nextCopyNumber = (halfArmLength - cumulativeLength) / halfArmLength * segment[SEG_CN_INDEX];
                    armStats.MedianCopyNumber = (prevCopyNumber + nextCopyNumber) * 0.5;
                }
                else
                {
                    armStats.MedianCopyNumber = segment[SEG_CN_INDEX];
                }
            }
            else
            {
                cumulativeLength += segLength;
            }
        }
    }

    private boolean isCopyNumberNeutral(final LohEvent lohEvent)
    {
        if(lohEvent.chromosomeLoss())
            return false;

        final List<SvCNData> cnDataList = mCnDataLoader.getChrCnDataMap().get(lohEvent.Chromosome);

        if(cnDataList == null || cnDataList.isEmpty())
            return false;

        if(!lohEvent.SegStart.equals(TELOMERE))
        {
            final SvCNData startCnData = lohEvent.getCnData(true);

            if(startCnData.getIndex() == 0)
                return false;

            final SvCNData prevCnData = cnDataList.get(startCnData.getIndex() - 1);
            if (!copyNumbersEqual(startCnData.CopyNumber, prevCnData.CopyNumber))
                return false;
        }

        if(!lohEvent.SegEnd.equals(TELOMERE))
        {
            final SvCNData endCnData = lohEvent.getCnData(false);

            if(endCnData.getIndex() >= cnDataList.size() - 1)
                return false;

            final SvCNData nextCnData = cnDataList.get(endCnData.getIndex() + 1);
            if (!copyNumbersEqual(endCnData.CopyNumber, nextCnData.CopyNumber))
                return false;
        }

        return true;
    }

    private void writeLohData(final String sampleId)
    {
        try
        {
            if (mLohEventWriter == null)
            {
                String outputFileName = mOutputPath + "LNX_CN_LOH_EVENTS.csv";

                mLohEventWriter = createBufferedWriter(outputFileName, false);

                mLohEventWriter.write("SampleId,Ploidy,Chromosome,PosStart,PosEnd,SegStart,SegEnd");
                mLohEventWriter.write(",SegCount,Length,StartSV,EndSV,CnStart,CnEnd,CnNeutral");
                mLohEventWriter.newLine();
            }

            final List<LohEvent> lohEvents = mCnDataLoader.getLohData();
            double samplePloidy = mCnDataLoader.getPurityContext().bestFit().ploidy();

            for(final LohEvent lohData : lohEvents)
            {
                // report whether this LOH has copy number change on both ends or not
                boolean cnNeutral = isCopyNumberNeutral(lohData);

                mLohEventWriter.write(String.format("%s,%.2f,%s,%d,%d,%s,%s",
                        sampleId, samplePloidy, lohData.Chromosome, lohData.PosStart, lohData.PosEnd, lohData.SegStart, lohData.SegEnd));

                mLohEventWriter.write(String.format(",%d,%d,%s,%s",
                        lohData.SegCount, lohData.length(), lohData.StartSV, lohData.EndSV));

                mLohEventWriter.write(String.format(",%.2f,%.2f,%s",
                        lohData.getCnData(true).CopyNumber, lohData.getCnData(false).CopyNumber, cnNeutral));

                mLohEventWriter.newLine();
            }
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing to copy number LOH outputFile: {}", e.toString());
        }
    }

    private void writePloidyCalcData(final String sampleId)
    {
        try
        {
            if (mPloidyCalcWriter == null)
            {
                String outputFileName = mOutputPath + "LNX_CN_PLOIDY_CALC_DATA.csv";

                mPloidyCalcWriter = createBufferedWriter(outputFileName, false);

                mPloidyCalcWriter.write("SampleId,SvId,Type,Ploidy,VafStart,VafEnd,TumorRCStart,TumorRCEnd");
                mPloidyCalcWriter.write(",ChrStart,PosStart,OrientStart,MaxCNStart,CNChgStart,PrevDWCountStart,NextDWCountStart");
                mPloidyCalcWriter.write(",ChrEnd,PosEnd,OrientEnd,MaxCNEnd,CNChgEnd,PrevDWCountEnd,NextDWCountEnd");
                mPloidyCalcWriter.write(",EstPloidy,EstUncertainty,MinPloidy,MaxPloidy");

                mPloidyCalcWriter.newLine();
            }

            /*
            mPloidyCalcWriter.write(String.format("%s,%d,%s,%.4f,%.4f,%.4f,%d,%d",
                    sampleId, svData.id(), svData.type(), svData.ploidy(), adjVafStart, adjVafEnd,
                    tumorReadCountStart, tumorReadCountEnd));

            mPloidyCalcWriter.write(String.format(",%s,%d,%d,%.4f,%.4f,%d,%d",
                    svData.startChromosome(), svData.startPosition(), svData.startOrientation(),
                    maxCNStart, cnChgStart, startDepthData[0], startDepthData[1]));

            mPloidyCalcWriter.write(String.format(",%s,%d,%d,%.4f,%.4f,%d,%d",
                    svData.endChromosome(), svData.endPosition(), svData.endOrientation(), maxCNEnd, cnChgEnd,
                    endDepthData != null ? endDepthData[0] : 0, endDepthData != null ? endDepthData[1] : 0));

            mPloidyCalcWriter.write(String.format(",%.2f,%.2f,%.2f,%.2f",
                    ploidyEstimate, ploidyUncertainty,
                    ploidyEstimate - ploidyUncertainty,
                    ploidyEstimate + ploidyUncertainty));
            mPloidyCalcWriter.newLine();
            */
        }
        catch (final IOException e)
        {
            LOGGER.error("error writing to ploidy recalc outputFile: {}", e.toString());
        }
    }

    public void close()
    {
        closeBufferedWriter(mLohEventWriter);
        closeBufferedWriter(mPloidyCalcWriter);
        closeBufferedWriter(mChrArmWriter);
        closeBufferedWriter(mCnSegmentWriter);
    }

    public static void main(@NotNull final String[] args) throws ParseException, SQLException
    {
        final Options options = new Options();
        CopyNumberAnalyser.addCmdLineArgs(options);

        final CommandLineParser parser = new DefaultParser();
        final CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption(LOG_DEBUG))
        {
            Configurator.setRootLevel(Level.DEBUG);
        }

        String outputDir = formOutputPath(cmd.getOptionValue(DATA_OUTPUT_DIR));

        final DatabaseAccess dbAccess = cmd.hasOption(DB_URL) ? databaseAccess(cmd) : null;

        CopyNumberAnalyser cnAnalyser = new CopyNumberAnalyser(outputDir, dbAccess);
        cnAnalyser.loadConfig(cmd);

        cnAnalyser.runAnalysis();
        cnAnalyser.close();

        LOGGER.info("CN analysis complete");
    }


}
