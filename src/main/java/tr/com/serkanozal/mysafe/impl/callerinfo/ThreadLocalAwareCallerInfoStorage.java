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

import tr.com.serkanozal.mysafe.ThreadLocalMemoryUsageDecider;

public class ThreadLocalAwareCallerInfoStorage implements CallerInfoStorage {

    private final CallerInfoStorage globalCallerInfoStorage;
    private final CallerInfoStorage threadLocalCallerInfoStorage;
    private final ThreadLocalMemoryUsageDecider threadLocalMemoryUsageDecider;
    
    public ThreadLocalAwareCallerInfoStorage(CallerInfoStorage globalCallerInfoStorage, 
                                             CallerInfoStorage threadLocalCallerInfoStorage, 
                                             ThreadLocalMemoryUsageDecider threadLocalMemoryUsageDecider) {
        this.globalCallerInfoStorage = globalCallerInfoStorage;
        this.threadLocalCallerInfoStorage = threadLocalCallerInfoStorage;
        this.threadLocalMemoryUsageDecider = threadLocalMemoryUsageDecider;
    }
    
    private CallerInfoStorage callerInfoStorage() {
        if (threadLocalMemoryUsageDecider.isThreadLocal(Thread.currentThread())) {
            return threadLocalCallerInfoStorage;
        } else {
            return globalCallerInfoStorage;
        }
    }

    @Override
    public CallerInfo getCallerInfo(long callerInfoKey) {
        return callerInfoStorage().getCallerInfo(callerInfoKey);
    }

    @Override
    public CallerInfo putCallerInfo(long callerInfoKey, CallerInfo callerInfo) {
        return callerInfoStorage().putCallerInfo(callerInfoKey, callerInfo);
    }

    @Override
    public CallerInfo removeCallerInfo(long callerInfoKey) {
        return callerInfoStorage().removeCallerInfo(callerInfoKey);
    }

    @Override
    public CallerInfo findCallerInfoByConnectedAddress(long address) {
        CallerInfo callerInfo = threadLocalCallerInfoStorage.findCallerInfoByConnectedAddress(address);
        if (callerInfo != null) {
            return callerInfo;
        } else {
            return globalCallerInfoStorage.findCallerInfoByConnectedAddress(address);
        }    
    }

    @Override
    public void connectAddressWithCallerInfo(long address, CallerInfo callerInfo) {
        callerInfoStorage().connectAddressWithCallerInfo(address, callerInfo);
    }

    @Override
    public void disconnectAddressFromCallerInfo(long address) {
        callerInfoStorage().disconnectAddressFromCallerInfo(address);
    }

}
