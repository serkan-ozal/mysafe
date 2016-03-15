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

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.impl.util.Long2LongHashMap;
import tr.com.serkanozal.mysafe.impl.util.Long2ObjectHashMap;

public class ThreadLocalDefaultCallerInfoStorage extends AbstractThreadLocalCallerInfoStorage {

    public ThreadLocalDefaultCallerInfoStorage(Unsafe unsafe) {
        super(unsafe);
    }

    @Override
    protected CallerInfoStorage createInternalThreadLocalCallerInfoStorage(Unsafe unsafe) {
        return new InternalThreadLocalDefaultCallerInfoStorage(unsafe);
    }
    
    private class InternalThreadLocalDefaultCallerInfoStorage 
            extends AbstractInternalThreadLocalCallerInfoStorage {

        private final Long2ObjectHashMap<CallerInfo> callerInfoMap =
                new Long2ObjectHashMap<CallerInfo>();
        private final Long2LongHashMap allocationCallerInfoMap = 
                new Long2LongHashMap(-1);
 
        private InternalThreadLocalDefaultCallerInfoStorage(Unsafe unsafe) {
            super(unsafe);
        }

        @Override
        public CallerInfo getCallerInfo(long callerInfoKey) {
            return callerInfoMap.get(callerInfoKey);
        }

        @Override
        public CallerInfo putCallerInfo(long callerInfoKey, CallerInfo callerInfo) {
            acquire();
            try {
                return callerInfoMap.put(callerInfoKey, callerInfo);
            } finally {
                free();
            }
        }

        @Override
        public CallerInfo removeCallerInfo(long callerInfoKey) {
            acquire();
            try {
                return callerInfoMap.remove(callerInfoKey);
            } finally {
                free();
            }    
        }

        @Override
        public CallerInfo findCallerInfoByConnectedAddress(long address) {
            acquire();
            try {
                long callerInfoKey = allocationCallerInfoMap.get(address);
                if (callerInfoKey != -1) {
                    return callerInfoMap.get(callerInfoKey);
                } else {
                    return null;
                }
            } finally {
                free();
            }
        }

        @Override
        public void connectAddressWithCallerInfo(long address, CallerInfo callerInfo) {
            acquire();
            try {
                allocationCallerInfoMap.put(address, callerInfo.key);
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

    }

}

