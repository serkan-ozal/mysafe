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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

public class DefaultCallerInfoStorage implements CallerInfoStorage {

    private final ConcurrentMap<Long, CallerInfo> callerInfoMap;
    private final NonBlockingHashMapLong<Long> allocationCallerInfoMap =
            new NonBlockingHashMapLong<Long>(1024, false);
    
    public DefaultCallerInfoStorage() {
        this.callerInfoMap = new ConcurrentHashMap<Long, CallerInfo>();
    }
    
    public DefaultCallerInfoStorage(ConcurrentMap<Long, CallerInfo> callerInfoMap) {
        this.callerInfoMap = callerInfoMap;
    }
    
    @Override
    public CallerInfo getCallerInfo(long callerInfoKey) {
        return callerInfoMap.get(callerInfoKey);
    }

    @Override
    public CallerInfo putCallerInfo(long callerInfoKey, CallerInfo callerInfo) {
        return callerInfoMap.putIfAbsent(callerInfoKey, callerInfo);
    }

    @Override
    public CallerInfo removeCallerInfo(long callerInfoKey) {
        return callerInfoMap.remove(callerInfoKey);
    }
    
    @Override
    public CallerInfo findCallerInfoByConnectedAddress(long address) {
        Long callerInfoKey = allocationCallerInfoMap.get(address);
        if (callerInfoKey != null) {
            return callerInfoMap.get(callerInfoKey);
        } else {
            return null;
        }
    }

    @Override
    public void connectAddressWithCallerInfo(long address, long callerInfoKey) {
        allocationCallerInfoMap.put(address, (Long) callerInfoKey);
    }

    @Override
    public void disconnectAddressFromCallerInfo(long address) {
        allocationCallerInfoMap.remove(address);
    }

    @Override
    public boolean isEmpty() {
        return allocationCallerInfoMap.isEmpty();
    }

}
