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

import java.util.concurrent.ScheduledExecutorService;

import sun.misc.Unsafe;
import tr.com.serkanozal.mysafe.ThreadLocalMemoryUsageDecider;

public class ThreadLocalAwareCallerInfoStorage implements CallerInfoStorage {

    private final CallerInfoStorage globalCallerInfoStorage;
    private final CallerInfoStorage threadLocalCallerInfoStorage;
    private final ThreadLocalMemoryUsageDecider threadLocalMemoryUsageDecider;
    
    public ThreadLocalAwareCallerInfoStorage(Unsafe unsafe, 
                                             ThreadLocalMemoryUsageDecider threadLocalMemoryUsageDecider,
                                             ScheduledExecutorService scheduler) {
        this.globalCallerInfoStorage = new DefaultCallerInfoStorage();
        this.threadLocalCallerInfoStorage = new ThreadLocalDefaultCallerInfoStorage(unsafe, scheduler);
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
    public long getCallerInfoKey(long address) {
        return callerInfoStorage().getCallerInfoKey(address);
    }

    @Override
    public void connectAddressWithCallerInfo(long address, long callerInfoKey) {
        callerInfoStorage().connectAddressWithCallerInfo(address, callerInfoKey);
    }

    @Override
    public void disconnectAddressFromCallerInfo(long address) {
        callerInfoStorage().disconnectAddressFromCallerInfo(address);
    }
    
    @Override
    public boolean isEmpty() {
        if (threadLocalCallerInfoStorage.isEmpty()) {
            return globalCallerInfoStorage.isEmpty();
        }
        return false;
    }

}
