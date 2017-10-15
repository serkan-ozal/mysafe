/*
 * Original work Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 * Modified work Copyright (c) 2017, Serkan OZAL, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tr.com.serkanozal.mysafe.impl.util;

/**
 * A Probing hashmap specialised for long key and value pairs.
 */
abstract class AbstractLong2LongHashMap implements Long2LongMap {
    
    public static final double DEFAULT_LOAD_FACTOR = 0.6;

    protected final double loadFactor;
    protected final long missingValue;

    protected long entriesLength;
    protected long capacity;
    protected long mask;
    protected long resizeThreshold;
    protected long size;

    public AbstractLong2LongHashMap(long initialCapacity, double loadFactor, long missingValue) {
        this(loadFactor, missingValue);
        capacity(nextPowerOfTwo(initialCapacity));
    }

    public AbstractLong2LongHashMap(long missingValue) {
        this(16, DEFAULT_LOAD_FACTOR, missingValue);
    }

    private AbstractLong2LongHashMap(double loadFactor, long missingValue) {
        this.loadFactor = loadFactor;
        this.missingValue = missingValue;
    }
    
    private long nextPowerOfTwo(final long value) {
        return 1 << (64 - Long.numberOfLeadingZeros(value - 1));
    }
    
    private long fastLongMix(long k) {
        // phi = 2^64 / goldenRatio
        final long phi = 0x9E3779B97F4A7C15L;
        long h = k * phi;
        h ^= h >>> 32;
        return h ^ (h >>> 16);
    }
    
    private long evenLongHash(final long value, final long mask) {
        final long h = fastLongMix(value);
        return h & mask & ~1;
    }
    
    abstract protected long getEntry(long index);
   
    abstract protected void setEntry(long index, long entry);
    
    abstract protected void fillEntries(long entry);
    
    abstract protected void allocateEntries(long length);
    
    abstract protected void rehash(long newCapacity);

    @Override
    public long size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public long get(final long key) {
        long index = evenLongHash(key, mask);
        long candidateKey;
        while ((candidateKey = getEntry(index)) != missingValue) {
            if (candidateKey == key) {
                return getEntry(index + 1);
            }
            index = next(index);
        }
        return missingValue;
    }

    @Override
    public long put(final long key, final long value) {
        assert key != missingValue : "Invalid key " + key;
        assert value != missingValue : "Invalid value " + value;
        long oldValue = missingValue;
        long index = evenLongHash(key, mask);
        long candidateKey;
        while ((candidateKey = getEntry(index)) != missingValue) {
            if (candidateKey == key) {
                oldValue = getEntry(index + 1);
                break;
            }
            index = next(index);
        }
        if (oldValue == missingValue) {
            ++size;
            setEntry(index, key);
        }
        setEntry(index + 1, value);
        checkResize();
        return oldValue;
    }

    private void checkResize() {
        if (size > resizeThreshold) {
            final long newCapacity = capacity << 1;
            if (newCapacity < 0) {
                throw new IllegalStateException("Max capacity reached at size=" + size);
            }
            rehash(newCapacity);
        }
    }

    @Override
    public void longForEach(final LongLongConsumer consumer) {
        for (long i = 0; i < entriesLength; i += 2) {
            final long key = getEntry(i);
            if (key != missingValue) {
                consumer.accept(getEntry(i), getEntry(i + 1));
            }
        }
    }

    @Override
    public LongLongCursor cursor() {
        return new LongLongCursorImpl();
    }

    private final class LongLongCursorImpl implements LongLongCursor {
        
        private long i = -2;

        public boolean advance() {
            do {
                i += 2;
            } while (i < entriesLength && getEntry(i) == missingValue);
            return i < entriesLength;
        }

        public long key() {
            return getEntry(i);
        }

        public long value() {
            return getEntry(i + 1);
        }
        
    }

    @Override
    public boolean containsKey(final long key) {
        return get(key) != missingValue;
    }

    @Override
    public boolean containsValue(final long value) {
        for (long i = 1; i < entriesLength; i += 2) {
            final long entryValue = getEntry(i);
            if (entryValue == value) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        fillEntries(missingValue);
        size = 0;
    }

    @Override
    public long remove(final long key) {
        long index = evenLongHash(key, mask);
        long candidateKey;
        while ((candidateKey = getEntry(index)) != missingValue) {
            if (candidateKey == key) {
                final long valueIndex = index + 1;
                final long oldValue = getEntry(valueIndex);
                setEntry(index, missingValue);
                setEntry(valueIndex, missingValue);
                size--;
                compactChain(index);
                return oldValue;
            }
            index = next(index);
        }
        return missingValue;
    }

    private void compactChain(long deleteIndex) {
        long index = deleteIndex;
        while (true) {
            index = next(index);
            if (getEntry(index) == missingValue) {
                return;
            }
            final long hash = evenLongHash(getEntry(index), mask);
            if ((index < hash && (hash <= deleteIndex || deleteIndex <= index))
                    || (hash <= deleteIndex && deleteIndex <= index)) {
                setEntry(deleteIndex, getEntry(index));
                setEntry(deleteIndex + 1, getEntry(index + 1));
                setEntry(index, missingValue);
                setEntry(index + 1, missingValue);
                deleteIndex = index;
            }
        }
    }
    
    private long next(final long index) {
        return (index + 2) & mask;
    }

    protected void capacity(final long newCapacity) {
        capacity = newCapacity;
        entriesLength = newCapacity * 2; 
        resizeThreshold = (long) (newCapacity * loadFactor);
        mask = entriesLength - 1;
        allocateEntries(entriesLength);
        size = 0;
        fillEntries(missingValue);
    }
    
}

