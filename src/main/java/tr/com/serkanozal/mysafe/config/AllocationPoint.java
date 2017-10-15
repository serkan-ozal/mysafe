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
package tr.com.serkanozal.mysafe.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * Annotation to mark custom allocation points to be tracked.
 * </p>
 * <p>
 * Annotated method must be in the form of 
 * <tt>long $YOUR_ALLOCATION_METHOD_NAME$(long size, ...)</tt>
 * as given parameter order by default.
 * Order of <tt>size</tt> parameter can be configured via {@link #sizeParameterOrder()}.
 * As you can see, 
 * <ul>
 *  <li>There might be other parameters rather than <tt>size</tt>.</li>
 *  <li>Return type can only be <tt>long</tt> and it must be allocated address.</li>
 * </ul> 
 * </p> 
 * <p>
 * Also note that the marked method must be concrete method.
 * Must not be neither method definition on interface nor on abstract class.
 * </p>
 * 
 * @author Serkan OZAL
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllocationPoint {

    /**
     * Order of the <tt>size</tt> parameter. 
     * Note that parameter order starts from <tt>1</tt>.
     * 
     * @return the order of the <tt>size</tt> parameter
     */
    int sizeParameterOrder() default AllocationPointConfig.DEFAULT_SIZE_PARAMETER_ORDER;
	
}
