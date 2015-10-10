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
package tr.com.serkanozal.mysafe;

import java.io.Serializable;

/**
 * Holds allocated memory information with its start address and size in bytes.
 * 
 * @author Serkan OZAL
 */
@SuppressWarnings("serial")
public class AllocatedMemoryInfo implements Serializable {

    private final long address;
    private final long size;

    public AllocatedMemoryInfo(long address, long size) {
        this.address = address;
        this.size = size;
    }
    
    public long getAddress() {
        return address;
    }
    
    public long getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "AllocatedMemoryInfo [address=" + address + ", size=" + size + "]";
    }

}
