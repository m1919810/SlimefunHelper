package me.matl114.utils.collections;

public class FlatBitsetUtil {
    private static final int LOG2_LONG = 6;
    private static final long ALL_SET = -1L;
    private static final int BITS_PER_LONG = Long.SIZE;
    public static int firstClear(final long[] bitset, final int from, final int to) {
        if ((from | to | (to - from)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        // like firstSet, but invert the bitset

        int bitsetIdx = from >>> LOG2_LONG;
        int bitIdx = from & ~(BITS_PER_LONG - 1);

        long tmp = (~bitset[bitsetIdx]) & (ALL_SET << from);
        for (;;) {
            if (tmp != 0L) {
                final int ret = bitIdx | Long.numberOfTrailingZeros(tmp);
                return ret >= to ? -1 : ret;
            }

            bitIdx += BITS_PER_LONG;

            if (bitIdx >= to) {
                return -1;
            }

            tmp = ~bitset[++bitsetIdx];
        }
    }
    public static boolean isRangeSet(final long[] bitset, final int from, final int to) {
        return firstClear(bitset, from, to) == -1;
    }
}
