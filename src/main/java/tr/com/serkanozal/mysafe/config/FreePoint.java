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
 * Annotation to mark custom free points to be tracked.
 * </p>
 * <p>
 * Annotated method must be in the form of 
 * <tt>void $YOUR_FREE_METHOD_NAME$(long address, ...)</tt>
 * as given parameter order by default.
 * Order of <tt>address</tt> parameter can be configured via {@link #addressParameterOrder()}.
 * As you can see, 
 * <ul>
 *  <li>There might be other parameters rather than <tt>address</tt>.</li>
 *  <li>Return type can only be <tt>void</tt>.</li>
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
public @interface FreePoint {

    /**
     * Order of the <tt>address</tt> parameter. 
     * Note that parameter order starts from <tt>1</tt>.
     * 
     * @return the order of the <tt>address</tt> parameter
     */
    int addressParameterOrder() default FreePointConfig.DEFAULT_ADDRESS_PARAMETER_ORDER;
	
}
