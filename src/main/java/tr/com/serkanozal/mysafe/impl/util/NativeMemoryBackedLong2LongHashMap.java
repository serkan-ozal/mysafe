/*
 * Copyright (c) 2017, Serkan OZAL, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tr.com.serkanozal.mysafe.impl.util;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.MySafe;

public class NativeMemoryBackedLong2LongHashMap extends AbstractLong2LongHashMap {

    private final Unsafe unsafe;
    private long entriesAddress;
    
    public NativeMemoryBackedLong2LongHashMap(Unsafe unsafe, long missingValue) {
        super(missingValue);
        this.unsafe = unsafe;
    }

    public NativeMemoryBackedLong2LongHashMap(Unsafe unsafe, long initialCapacity,
                                              double loadFactor, long missingValue) {
        super(initialCapacity, loadFactor, missingValue);
        this.unsafe = unsafe;
    }

    @Override
    protected long getEntry(long index) {
        return unsafe.getLong(entriesAddress + (index << 3));
    }

    @Override
    protected void setEntry(long index, long entry) {
        unsafe.putLong(entriesAddress + (index << 3), entry);
    }

    @Override
    protected void fillEntries(long entry) {
        if (unsafe == null) {
            Unsafe u = MySafe.getUnsafe();
            for (long i = 0; i < entriesLength; i++) {
                u.putLong(entriesAddress + (i << 3), missingValue);
            } 
        } else {
            for (long i = 0; i < entriesLength; i++) {
                unsafe.putLong(entriesAddress + (i << 3), missingValue);
            }  
        }    
    }

    @Override
    protected void allocateEntries(long length) {
        if (unsafe == null) {
            entriesAddress = MySafe.getUnsafe().allocateMemory(length << 3);
        } else {
            entriesAddress = unsafe.allocateMemory(length << 3);
        }
    }
    
    @Override
    protected void rehash(long newCapacity) {
        final long oldEntriesAddress = entriesAddress;
        final long oldEntriesLength = entriesLength;
        capacity(newCapacity);
        for (long i = 0; i < oldEntriesLength; i += 2) {
            final long key = unsafe.getLong(oldEntriesAddress + (i << 3));
            if (key != missingValue) {
                put(key, unsafe.getLong(oldEntriesAddress + ((i + 1) << 3)));
            }
        }
        unsafe.freeMemory(oldEntriesAddress);
    }

}
