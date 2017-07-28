package com.hartwig.hmftools.breakpointinspector;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;

enum HMFVariantType {
    DEL,
    INV3,
    INV5,
    DUP;

    static String getOrientation(final HMFVariantType type) {
        switch (type) {
            case DEL:
                return "INNIE";
            case INV3:
                return "TANDEM_RIGHT";
            case INV5:
                return "TANDEM_LEFT";
            case DUP:
                return "OUTIE";
            default:
                return "ERROR";
        }
    }
}

class Range {
    int Start;
    int End;

    Range(int start, int end) {
        Start = start;
        End = end;
    }
}

class HMFVariantContext {
    Location MantaBP1;
    Range Uncertainty1;
    Location MantaBP2;
    Range Uncertainty2;
    HMFVariantType Type;
    HashSet<String> Filter = Sets.newHashSet();

    int OrientationBP1 = 0;
    int OrientationBP2 = 0;

    HMFVariantContext(final Location bp1, final Location bp2, final HMFVariantType type) {
        MantaBP1 = bp1;
        MantaBP2 = bp2;
        Type = type;
    }
}

class Util {

    static List<String> prefixList(final List<String> list, final String prefix) {
        return list.stream().map(s -> prefix + s).collect(Collectors.toList());
    }

}