/*
 * Original work Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 * Modified work Copyright (c) 1986-2016, Serkan OZAL, All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tr.com.serkanozal.mysafe.impl.accessor;

import sun.misc.Unsafe;

/**
 * Creates {@link UnsafeMemoryAccessor} implementation to be used for memory accesses 
 * depending on the underlying platform itself.
 */
public final class UnsafeMemoryAccessorFactory {

    private UnsafeMemoryAccessorFactory() {
    }

    private static boolean isUnalignedAccessAllowed() {
        String arch = System.getProperty("os.arch");
        // list of architectures copied from OpenJDK - java.nio.Bits::unaligned
        return arch.equals("i386") || arch.equals("x86") || arch.equals("amd64") || arch.equals("x86_64");
    }

    /**
     * Creates the {@link UnsafeMemoryAccessor} implementation to be used for memory accesses.
     * 
     * @param unsafe the {@link Unsafe} instance
     * @return the {@link UnsafeMemoryAccessor} implementation to be used for memory accesses
     */
    public static UnsafeMemoryAccessor createUnsafeMemoryAccessor(Unsafe unsafe) {
        if (isUnalignedAccessAllowed()) {
            return new StandardUnsafeMemoryAccessor();
        } else {
            return new AlignmentAwareUnsafeMemoryAccessor(unsafe);
        }
    }

}
