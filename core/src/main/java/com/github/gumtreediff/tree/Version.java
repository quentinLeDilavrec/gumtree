package com.github.gumtreediff.tree;

// TODO use ℚ because it's dense, it will avoid recounting all versions if we want to add a new version inbetween existing versions
public interface Version extends Comparable<Version> {

    public Version TOP = new Version() {
        public COMP_RES partiallyCompareTo(Version other){
            return other == TOP ? COMP_RES.EQUAL: COMP_RES.SUPERIOR;
        }
    };

    public Version BOT = new Version() {
        public COMP_RES partiallyCompareTo(Version other){
            return other == BOT ? COMP_RES.EQUAL: COMP_RES.INFERIOR;
        }
    };

    /**
     * Result of a comparison between versions
     */
    public enum COMP_RES {
        INFERIOR, EQUAL, SUPERIOR, PARALLEL, UNKNOWN;
    }

    public COMP_RES partiallyCompareTo(Version other);

    @Override
    public default int compareTo(Version other) {
        switch (partiallyCompareTo(other)) {
            case INFERIOR:
                return -1;
            case EQUAL:
                return 0;
            case SUPERIOR:
                return 1;
            case PARALLEL:
                throw new RuntimeException(COMP_RES.PARALLEL.name() + " " + this.toString() + " " + other.toString());
            default:
                switch (other.partiallyCompareTo(this)) { // to satisfy permutivity prop
                    case INFERIOR:
                        return 1;
                    case EQUAL:
                        return 0;
                    case SUPERIOR:
                        return -1;
                    case PARALLEL:
                        throw new RuntimeException(COMP_RES.PARALLEL.name() + " " + this.toString() + " " + other.toString());
                    default:
                        throw new RuntimeException(COMP_RES.UNKNOWN.name() + " " + this.toString() + " " + other.toString());
                }
        }
    }
}
