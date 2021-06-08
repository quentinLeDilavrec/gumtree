package com.github.gumtreediff.tree;

public final class VersionInt implements Version {
    public final int i;

    public VersionInt(int i) {
        this.i = i;
    }

    @Override
    public COMP_RES partiallyCompareTo(Version other) {
        if (other instanceof VersionInt) {
            int j = ((VersionInt) other).i;
            return j == i ? Version.COMP_RES.EQUAL
                    : (i < j ? Version.COMP_RES.INFERIOR : Version.COMP_RES.SUPERIOR);
        } else {
            return Version.COMP_RES.UNKNOWN;
        }
    }
    @Override
    public String toString() {
        return Integer.toString(i);
    }
}