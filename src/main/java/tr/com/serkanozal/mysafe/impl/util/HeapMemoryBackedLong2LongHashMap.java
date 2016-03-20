package tr.com.serkanozal.mysafe.impl.util;

import java.util.Arrays;

import sun.misc.Unsafe;

public class HeapMemoryBackedLong2LongHashMap extends AbstractLong2LongHashMap {

    private long[] entries;
    
    public HeapMemoryBackedLong2LongHashMap(long missingValue) {
        super(missingValue);
    }

    public HeapMemoryBackedLong2LongHashMap(Unsafe unsafe, long initialCapacity,
                                            double loadFactor, long missingValue) {
        super(initialCapacity, loadFactor, missingValue);
    }

    @Override
    protected long getEntry(long index) {
        assert index < Integer.MAX_VALUE : "Index cannot be bigger than maximum integer value!";
        return entries[(int) index];
    }

    @Override
    protected void setEntry(long index, long entry) {
        assert index < Integer.MAX_VALUE : "Index cannot be bigger than maximum integer value!";
        entries[(int) index] = entry;
    }

    @Override
    protected void fillEntries(long entry) {
        Arrays.fill(entries, entry);   
    }

    @Override
    protected void allocateEntries(long length) {
        assert length < Integer.MAX_VALUE : "Length cannot be bigger than maximum integer value!";
        entries = new long[(int) length];
    }
    
    @Override
    protected void rehash(long newCapacity) {
        final long[] oldEntries = entries;
        capacity(newCapacity);
        for (int i = 0; i < oldEntries.length; i += 2) {
            final long key = oldEntries[i];
            if (key != missingValue) {
                put(key, oldEntries[i + 1]);
            }
        }
    }

}
