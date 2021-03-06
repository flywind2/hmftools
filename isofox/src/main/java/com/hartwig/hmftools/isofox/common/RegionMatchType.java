package com.hartwig.hmftools.isofox.common;

import java.util.Iterator;
import java.util.Set;

public enum RegionMatchType
{
    NONE,
    EXON_BOUNDARY,  // read matches one exon boundary
    WITHIN_EXON,    // read fully contained within the exon
    EXON_MATCH,     // read fully contained within the exon
    EXON_INTRON,      // reads spanning to unmapped regions where adjacent regions exist
    INTRON;

    public static boolean validExonMatch(RegionMatchType type)
    {
        return type == EXON_BOUNDARY || type == WITHIN_EXON || type == EXON_MATCH;
    }

    public static boolean exonBoundary(RegionMatchType type)
    {
        return type == EXON_BOUNDARY || type == EXON_MATCH;
    }

    public static int matchRank(RegionMatchType type)
    {
        switch(type)
        {
            case EXON_BOUNDARY:
                return 4;
            case EXON_MATCH:
                return 4;
            case WITHIN_EXON:
                return 3;
            case EXON_INTRON:
                return 2;
            case INTRON:
                return 1;
            case NONE:
            default:
                return 0;
        }
    }

    public static RegionMatchType getHighestMatchType(final Set<RegionMatchType> types)
    {
        RegionMatchType highest = RegionMatchType.NONE;

        for(RegionMatchType type : types)
        {
            highest = matchRank(type) > matchRank(highest) ? type : highest;
        }

        return highest;

    }
}
