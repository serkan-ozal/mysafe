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
package tr.com.serkanozal.mysafe.impl.callerinfo;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.impl.util.HeapMemoryBackedLong2LongHashMap;
import tr.com.serkanozal.mysafe.impl.util.Long2LongMap;
import tr.com.serkanozal.mysafe.impl.util.NativeMemoryBackedLong2LongHashMap;

public class ThreadLocalDefaultCallerInfoStorage extends AbstractThreadLocalCallerInfoStorage {

    private static final boolean USE_NATIVE_MEMORY = Boolean.getBoolean("mysafe.useNativeMemoryForStorageWhenSupported");
    
    public ThreadLocalDefaultCallerInfoStorage(Unsafe unsafe, ScheduledExecutorService scheduler) {
        super(unsafe, scheduler);
    }
    
    public ThreadLocalDefaultCallerInfoStorage(Unsafe unsafe, 
                                               ConcurrentMap<Long, CallerInfo> callerInfoMap,
                                               ScheduledExecutorService scheduler) {
        super(unsafe, callerInfoMap, scheduler);
    }

    @Override
    protected CallerInfoStorage createInternalThreadLocalCallerInfoStorage(Unsafe unsafe) {
        return new InternalThreadLocalDefaultCallerInfoStorage(unsafe);
    }
    
    private class InternalThreadLocalDefaultCallerInfoStorage 
            extends AbstractInternalThreadLocalCallerInfoStorage {

        private final Long2LongMap allocationCallerInfoMap = 
                USE_NATIVE_MEMORY 
                    ? new NativeMemoryBackedLong2LongHashMap(unsafe, -1)
                    : new HeapMemoryBackedLong2LongHashMap(-1);
 
        private InternalThreadLocalDefaultCallerInfoStorage(Unsafe unsafe) {
            super(unsafe);
        }

        @Override
        public CallerInfo findCallerInfoByConnectedAddress(long address) {
            acquire();
            try {
                long callerInfoKey = allocationCallerInfoMap.get(address);
                if (callerInfoKey != -1) {
                    return ThreadLocalDefaultCallerInfoStorage.this.getCallerInfo(callerInfoKey);
                } else {
                    return null;
                }
            } finally {
                free();
            }
        }

        @Override
        public void connectAddressWithCallerInfo(long address, long callerInfoKey) {
            acquire();
            try {
                allocationCallerInfoMap.put(address, callerInfoKey);
            } finally {
                free();
            }
        }

        @Override
        public void disconnectAddressFromCallerInfo(long address) {
            acquire();
            try {
                allocationCallerInfoMap.remove(address);
            } finally {
                free();
            }
        }
        
        @Override
        public boolean isEmpty() {
            acquire();
            try {
                return allocationCallerInfoMap.isEmpty();
            } finally {
                free();
            }
        }

    }

}

