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
package tr.com.serkanozal.mysafe;

import javax.management.MXBean;

/**
 * Contract point for exporting MySafe as MX bean.
 * 
 * @author Serkan OZAL
 */
@MXBean
public interface MySafeMXBean {

    /**
     * Gets the allocated memory size in bytes.
     * 
     * @return the allocated memory size in bytes
     */
    long getAllocatedMemorySize();
    
    /**
     * Returns the allocated memory addresses and size as array of {@link AllocatedMemoryInfo}.
     * 
     * @return the allocated memory addresses and size as array of {@link AllocatedMemoryInfo}
     */
    AllocatedMemoryInfo[] getAllocatedMemories();
    
    /**
     * Returns <tt>true</tt> if safe memory management (allocate/free/reallocate) 
     * mode is enabled, otherwise returns <tt>false</tt>.
     * 
     * @return <code>true</tt> if safe memory management (allocate/free/reallocate) 
     *         is enabled, otherwise <tt>false</tt>
     */
    boolean isSafeMemoryManagementModeEnabled();
    
    /**
     * Returns <tt>true</tt> if safe memory access (read/write) 
     * mode is enabled, otherwise returns <tt>false</tt>.
     * 
     * @return <code>true</tt> if safe memory access (read/write) 
     *         is enabled, otherwise <tt>false</tt>
     */
    boolean isSafeMemoryAccessModeEnabled();
    
}
