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
package tr.com.serkanozal.mysafe.impl.allocpath.storage;

import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

public class DefaultAllocationPathStorage implements AllocationPathStorage {

    private final NonBlockingHashMapLong<Long> allocationPathMap =
            new NonBlockingHashMapLong<Long>(1024, false);

    @Override
    public long getAllocationPathKey(long address) {
        return allocationPathMap.get(address);
    }
    
    @Override
    public void connectAddressWithAllocationPath(long address, long allocationPathKey) {
        allocationPathMap.put(address, (Long) allocationPathKey);
    }

    @Override
    public void disconnectAddressFromAllocationPath(long address) {
        allocationPathMap.remove(address);
    }

    @Override
    public boolean isEmpty() {
        return allocationPathMap.isEmpty();
    }

}
