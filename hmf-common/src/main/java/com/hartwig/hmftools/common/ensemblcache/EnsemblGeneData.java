package com.hartwig.hmftools.common.ensemblcache;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EnsemblGeneData
{
    public final String GeneId; // aka StableId
    public final String GeneName;
    public final String Chromosome;
    public final byte Strand;
    public final long GeneStart;
    public final long GeneEnd;
    public final String KaryotypeBand;

    public EnsemblGeneData(
            String geneId, String geneName, String chromosome, byte strand, long geneStart, long geneEnd, String karyotypeBand)
    {
        GeneId = geneId;
        GeneName = geneName;
        Chromosome = chromosome;
        Strand = strand;
        GeneStart = geneStart;
        GeneEnd = geneEnd;
        KaryotypeBand = karyotypeBand;
    }

    public boolean forwardStrand() { return Strand == 1; }
    public boolean reverseStrand() { return Strand == -1; }

    public long length() { return GeneEnd - GeneStart; }

    public String toString()
    {
        return String.format("%s:%s chr(%s) pos(%d-%d) strand(%d)",
                GeneId, GeneName, Chromosome, GeneStart, GeneEnd, Strand);
    }

}