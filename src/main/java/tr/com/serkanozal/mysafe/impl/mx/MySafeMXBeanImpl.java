/*
 * Copyright (c) 1986-2015, Serkan OZAL, All Rights Reserved.
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
package tr.com.serkanozal.mysafe.impl.mx;

import java.util.ArrayList;
import java.util.List;

import tr.com.serkanozal.mysafe.AllocatedMemoryInfo;
import tr.com.serkanozal.mysafe.AllocatedMemoryIterator;
import tr.com.serkanozal.mysafe.MySafe;
import tr.com.serkanozal.mysafe.MySafeMXBean;

public class MySafeMXBeanImpl implements MySafeMXBean {

    @Override
    public long getAllocatedMemorySize() {
        return MySafe.getAllocatedMemorySize();
    }

    @Override
    public AllocatedMemoryInfo[] getAllocatedMemories() {
        final List<AllocatedMemoryInfo> allocatedMemories = new ArrayList<AllocatedMemoryInfo>();
        MySafe.iterateOnAllocatedMemories(new AllocatedMemoryIterator() {
            @Override
            public void onAllocatedMemory(long address, long size) {
                allocatedMemories.add(new AllocatedMemoryInfo(address, size));
            }
        });
        return allocatedMemories.toArray(new AllocatedMemoryInfo[0]);
    }

    @Override
    public void enableSafeMode() {
        MySafe.enableSafeMode();
    }

    @Override
    public void disableSafeMode() {
        MySafe.disableSafeMode();
    }
    
}
