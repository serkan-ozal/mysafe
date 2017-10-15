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
package tr.com.serkanozal.mysafe;

/**
 * <p>
 * Contract point to decide if current thread uses (allocate/free/read/write) 
 * memory as thread-local or not. 
 * </p>
 * 
 * <p>
 * Thread-local mode means that every thread has its own memory pool.
 * they only allocate/free/read/write memory only owned by this pool and
 * no other thread interacts with this pool.
 * </p>
 * 
 * @author Serkan OZAL
 */
public interface ThreadLocalMemoryUsageDecider {
    
    /**
     * Returns <tt>true</tt> if current thread uses (allocate/free/read/write)
     * memory as thread-local, otherwise returns <tt>false</tt>.
     * 
     * @param currentThread the current thread uses (allocate/free/read/write) memory
     * @return <tt>true</tt> if current thread uses (allocate/free/read/write)
     *         memory as thread-local, otherwise <tt>false</tt>
     */
    boolean isThreadLocal(Thread currentThread);
    
}
