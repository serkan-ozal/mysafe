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

import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongSortedMap;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.AllocatedMemoryIterator;
import tr.com.serkanozal.mysafe.AllocatedMemoryStorage;

public class ThreadLocalNavigatableAllocatedMemoryStorage extends AbstractThreadLocalAllocatedMemoryStorage {

    private static final boolean USE_INDEXED_MEMORY_ACCESS_CHECK = Boolean.getBoolean("mysafe.useIndexedMemoryAccessCheck");
    
    private final IndexedMemoryAccessChecker indexedMemoryAccessChecker;
    
    public ThreadLocalNavigatableAllocatedMemoryStorage(Unsafe unsafe, ScheduledExecutorService scheduler) {
        super(unsafe, scheduler);
        if (USE_INDEXED_MEMORY_ACCESS_CHECK) {
            indexedMemoryAccessChecker = new IndexedMemoryAccessChecker(unsafe);
        } else {
            indexedMemoryAccessChecker = null;
        }
    }

    @Override
    protected AllocatedMemoryStorage createInternalThreadLocalAllocatedMemoryStorage(Unsafe unsafe) {
        return new InternalThreadLocalNavigatableAllocatedMemoryStorage(unsafe);
    }
    
    private class InternalThreadLocalNavigatableAllocatedMemoryStorage 
            extends AbstractInternalThreadLocalAllocatedMemoryStorage {

        private final Long2LongSortedMap allocatedMemories = new Long2LongAVLTreeMap();
 
        private InternalThreadLocalNavigatableAllocatedMemoryStorage(Unsafe unsafe) {
            super(unsafe);
        }

        @Override
        public boolean contains(long address) {
            if (indexedMemoryAccessChecker != null) {
                int result = indexedMemoryAccessChecker.isAllocated(address);
                if (result == IndexedMemoryAccessChecker.ALLOCATED) {
                    return true;
                } else if (result == IndexedMemoryAccessChecker.NOT_ALLOCATED) {
                    return false;
                }
                // Not indexed, so check over allocated memories
            }
            Long2LongMap.Entry entry = allocatedMemories.tailMap(address).long2LongEntrySet().first();
            if (entry == null) {
                return false;
            }
            long startAddress = entry.getLongKey();
            long endAddress = startAddress + entry.getLongValue();
            return address >= startAddress && address <= endAddress;
        }
        
        @Override
        public boolean contains(long address, long size) {
            if (indexedMemoryAccessChecker != null) {
                int result = indexedMemoryAccessChecker.isAllocated(address, size);
                if (result == IndexedMemoryAccessChecker.ALLOCATED) {
                    return true;
                } else if (result == IndexedMemoryAccessChecker.NOT_ALLOCATED) {
                    return false;
                }
                // Not indexed, so check over allocated memories
            }
            Long2LongMap.Entry entry = allocatedMemories.tailMap(address).long2LongEntrySet().first();
            if (entry == null) {
                return false;
            }
            long startAddress = entry.getLongKey();
            long endAddress = startAddress + entry.getLongValue();
            return address >= startAddress && (address + size) <= endAddress;
        }

        @Override
        public long get(long address) {
            long size = allocatedMemories.get(address);
            return size != 0 ? size : INVALID;
        }

        @Override
        public void put(long address, long size) {
            acquire();
            try {
                allocatedMemories.put(address, size);
                if (indexedMemoryAccessChecker != null) {
                    indexedMemoryAccessChecker.markAllocated(address, size);
                }
            } finally {
                free();
            }
        }

        @Override
        public long remove(long address) {
            acquire();
            try {
                long size = allocatedMemories.remove(address);
                if (indexedMemoryAccessChecker != null && size != 0) {
                    indexedMemoryAccessChecker.markFree(address, size);
                }
                return size != 0 ? size : INVALID;
            } finally {
                free();
            }    
        }

        @Override
        public void iterate(final AllocatedMemoryIterator iterator) {
            acquire();
            try {
                for (Map.Entry<Long, Long> entry : allocatedMemories.entrySet()) {
                    long address = entry.getKey();
                    long size = entry.getValue();
                    iterator.onAllocatedMemory(address, size);
                }
            } finally {
                free();
            }    
        }
        
        @Override
        public boolean isEmpty() {
            acquire();
            try {
                return allocatedMemories.isEmpty();
            } finally {
                free();
            }
        }
        
    }
    
    private class IndexedMemoryAccessChecker {
        
        /*
         * <address_block> = <address> >> 3;
         * 
         * Structure of <address_block>:
         * +-----------------------------------------+
         * | <block_base>   | <block_no>             |
         * |================|========================|
         * | 29 bits        | 35 bits                |
         * +-----------------------------------------+
         * 
         * Structure of <block_no>:
         * +-----------------------------------------+
         * | <block_no_index1> | <block_no_index2>   |
         * |===================|=====================|
         * | 16 bits           | 19 bits             |
         * +-----------------------------------------+
         * 
         * Structure of <block_no_index2>:
         * +-----------------------------------------+
         * | <block_no_index2a> | <block_no_index2b> |
         * |====================|====================|
         * | 16 bits            | 3 bits             |
         * +-----------------------------------------+
         */
        
        private static final int ALLOCATED = 1;
        private static final int NOT_ALLOCATED = -1;
        private static final int NOT_INDEXED = 0;
        
        private final Unsafe unsafe;
        private final long rootIndexAddress;
        private final long sizeInfosAddress;
        private int blockBase;
        
        
        private IndexedMemoryAccessChecker(Unsafe unsafe) {
            this.unsafe = unsafe;
            
            // Allocate memory to store 65K addresses 
            int rootLength = (1 << 16) << (Long.SIZE / Byte.SIZE);
            this.rootIndexAddress = unsafe.allocateMemory(rootLength);
            unsafe.setMemory(rootIndexAddress, rootLength, (byte) 0x00);
            
            // Allocate memory to store 65K sizes 
            int sizesLength = (1 << 16) << (Integer.SIZE / Byte.SIZE);
            this.sizeInfosAddress = unsafe.allocateMemory(sizesLength);
            unsafe.setMemory(sizeInfosAddress, sizesLength, (byte) 0x00);
        }
        
        private int isAllocated(long address) {
            return isAllocated(address, 1);
        }
        
        private int isAllocated(long address, long size) {
            long addressBlock = address >> 3;
            long addressBlockEnd = (address + size - 1) >> 3;
            int blockBase = (int) (addressBlock >>> 35);
            int blockBaseEnd = (int) (addressBlockEnd >>> 35);
            
            if (blockBase != blockBaseEnd) {
                return NOT_INDEXED;
            }
            if (this.blockBase != blockBase) {
                return NOT_INDEXED;
            }
            
            long blockNoStart = addressBlock & 0x00000007FFFFFFFFL;
            long blockNoEnd = addressBlockEnd & 0x00000007FFFFFFFFL;
            
            for (long blockNo = blockNoStart; blockNo <= blockNoEnd; blockNo++) {
                int blockNoIndex1 = (int) (blockNo >>> 19);
                int blockNoIndex2 = (int) (blockNo & 0x0003FFFF);
                int blockNoIndex2a = blockNoIndex2 >>> 3;        
                int blockNoIndex2b = blockNoIndex2 & 0x00000007;  
                
                long blockNoIndex1Address = rootIndexAddress + (blockNoIndex1 << 3);
                long secondaryIndexAddress = unsafe.getLong(blockNoIndex1Address);
                if (secondaryIndexAddress == 0) {
                    secondaryIndexAddress = unsafe.allocateMemory(1 << 16);
                    unsafe.putLong(blockNoIndex1Address, secondaryIndexAddress);
                }
                
                long blockNoIndex2Address = secondaryIndexAddress + blockNoIndex2a;
                byte blockNoIndex2Value = unsafe.getByte(blockNoIndex2Address);
                if (!isBitSet(blockNoIndex2Value, blockNoIndex2b)) {
                    return NOT_ALLOCATED;
                }
            }    

            return ALLOCATED;
        }
        
        private boolean markAllocated(long address, long size) {
            return indexAddress(address, size, true);
        }
        
        private boolean markFree(long address, long size) {
            return indexAddress(address, size, false);
        }
        
        private boolean indexAddress(long address, long size, boolean mark) {
            long addressBlock = address >> 3;
            long addressBlockEnd = (address + size - 1) >> 3;
            int blockBase = (int) (addressBlock >>> 35);
            int blockBaseEnd = (int) (addressBlockEnd >>> 35);
            
            if (blockBase != blockBaseEnd) {
                return false;
            }
            if (this.blockBase == 0) {
                this.blockBase = blockBase;
            } else {
                if (this.blockBase != blockBase) {
                    addressBlockEnd = (1L << 35) - 1;
                }
            }
            
            long blockNoStart = addressBlock & 0x00000007FFFFFFFFL;
            long blockNoEnd = addressBlockEnd & 0x00000007FFFFFFFFL;
            
            for (long blockNo = blockNoStart; blockNo <= blockNoEnd; blockNo++) {
                int blockNoIndex1 = (int) (blockNo >>> 19);
                int blockNoIndex2 = (int) (blockNo & 0x0003FFFF);
                int blockNoIndex2a = blockNoIndex2 >>> 3;        
                int blockNoIndex2b = blockNoIndex2 & 0x00000007;  

                long blockNoIndex1Address = rootIndexAddress + (blockNoIndex1 << 3);
                long secondaryIndexAddress = unsafe.getLong(blockNoIndex1Address);
                if (secondaryIndexAddress == 0) {
                    long secondaryIndexLength = 1 << 16;
                    secondaryIndexAddress = unsafe.allocateMemory(secondaryIndexLength);
                    unsafe.setMemory(secondaryIndexAddress, secondaryIndexLength, (byte) 0);
                    unsafe.putLong(blockNoIndex1Address, secondaryIndexAddress);
                }
                
                long blockNoIndex2Address = secondaryIndexAddress + blockNoIndex2a;
                byte blockNoIndex2Value = unsafe.getByte(blockNoIndex2Address);
                long sizeInfoAddress = sizeInfosAddress + (blockNoIndex1 << 2);
                int indexCount = unsafe.getInt(sizeInfoAddress);
                if (mark) {
                    blockNoIndex2Value = setBit(blockNoIndex2Value, blockNoIndex2b);
                    indexCount++;
                } else {
                    blockNoIndex2Value = clearBit(blockNoIndex2Value, blockNoIndex2b);
                    indexCount--;
                    assert indexCount >= 0 : "Index count must not be negative!";
                    if (indexCount == 0) {
                        unsafe.freeMemory(secondaryIndexAddress);
                        unsafe.putLong(blockNoIndex1Address, 0L);
                    }
                }
                unsafe.putByte(blockNoIndex2Address, blockNoIndex2Value);
                unsafe.putInt(sizeInfoAddress, indexCount);
            }

            return true;
        }
        
        private byte setBit(byte value, int bit) {
            value |= 1 << bit;
            return value;
        }

        private byte clearBit(byte value, int bit) {
            value &= ~(1 << bit);
            return value;
        }
        
        private boolean isBitSet(byte value, int bit) {
            return (value & 1 << bit) != 0;
        }

    }

}

