/*
 * Copyright (c) 1986-2016, Serkan OZAL, All Rights Reserved.
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
package tr.com.serkanozal.mysafe.impl.storage;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.AllocatedMemoryIterator;
import tr.com.serkanozal.mysafe.AllocatedMemoryStorage;
import tr.com.serkanozal.mysafe.impl.util.Long2LongHashMap;
import tr.com.serkanozal.mysafe.impl.util.Long2LongHashMap.LongLongConsumer;
import tr.com.serkanozal.mysafe.impl.util.Long2LongHashMap.LongLongCursor;

public class ThreadLocalDefaultAllocatedMemoryStorage extends AbstractThreadLocalAllocatedMemoryStorage {

    public ThreadLocalDefaultAllocatedMemoryStorage(Unsafe unsafe) {
        super(unsafe);
    }

    @Override
    protected AllocatedMemoryStorage createInternalThreadLocalAllocatedMemoryStorage(Unsafe unsafe) {
        return new InternalThreadLocalDefaultAllocatedMemoryStorage(unsafe);
    }
    
    private class InternalThreadLocalDefaultAllocatedMemoryStorage 
            extends AbstractInternalThreadLocalAllocatedMemoryStorage {

        private final Long2LongHashMap allocatedMemories = new Long2LongHashMap(INVALID);
 
        private InternalThreadLocalDefaultAllocatedMemoryStorage(Unsafe unsafe) {
            super(unsafe);
        }

        @Override
        public boolean contains(long address) {
            return allocatedMemories.containsKey(address);
        }

        @Override
        public boolean contains(long address, long size) {
            LongLongCursor cursor = allocatedMemories.cursor();
            while (cursor.advance()) {
                long startAddress = cursor.key();
                long endAddress = startAddress + cursor.value();
                if (address >= startAddress && address <= endAddress) {
                    return true;
                }  
            }
            return false;
        }

        @Override
        public long get(long address) {
            return allocatedMemories.get(address);
        }

        @Override
        public void put(long address, long size) {
            acquire();
            try {
                allocatedMemories.put(address, size);
            } finally {
                free();
            }
        }

        @Override
        public long remove(long address) {
            acquire();
            try {
                return allocatedMemories.remove(address);
            } finally {
                free();
            }    
        }

        @Override
        public void iterate(final AllocatedMemoryIterator iterator) {
            acquire();
            try {
                allocatedMemories.longForEach(new LongLongConsumer() {
                    @Override
                    public void accept(long key, long value) {
                        iterator.onAllocatedMemory(key, value);
                    }
                });
            } finally {
                free();
            }    
        }
        
    }

}

