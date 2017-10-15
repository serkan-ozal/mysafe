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
package tr.com.serkanozal.mysafe.impl.allocpath;

public class AllocationPath {

    private static final int MAX_ALLOCATION_PATH_DEPTH_LIMIT = 4;
    private static final int DEFAULT_MAX_ALLOCATION_PATH_DEPTH = MAX_ALLOCATION_PATH_DEPTH_LIMIT;
    public static final int MAX_ALLOCATION_PATH_DEPTH =
            Math.min(MAX_ALLOCATION_PATH_DEPTH_LIMIT,
                     Integer.getInteger("mysafe.maxAllocationPathDepth", DEFAULT_MAX_ALLOCATION_PATH_DEPTH));

    public final long key;
    public final String[] callPoints;

    public AllocationPath(long key, String[] callPoints) {
        this.key = key;
        this.callPoints = callPoints;
    }

}
