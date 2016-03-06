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
package tr.com.serkanozal.mysafe.impl.instrument;

import static tr.com.serkanozal.mysafe.AllocatedMemoryStorage.INVALID;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import tr.com.serkanozal.mysafe.config.AllocationPoint;
import tr.com.serkanozal.mysafe.config.FreePoint;
import tr.com.serkanozal.mysafe.config.ReallocationPoint;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

class CustomMemoryManagementInstrumenter implements UnsafeUsageInstrumenter {

    private final boolean USE_CUSTOM_MEMORY_MANAGEMENT = 
            Boolean.getBoolean("mysafe.useCustomMemoryManagement");
    private final ClassPool CP = ClassPool.getDefault();
    private final Map<String, String> CONFIGURED_ALLOCATION_POINTS = new HashMap<String, String>();
    private final Map<String, String> CONFIGURED_FREE_POINTS = new HashMap<String, String>();
    private final Map<String, String> CONFIGURED_REALLOCATION_POINTS = new HashMap<String, String>();
    
    CustomMemoryManagementInstrumenter() {
        if (USE_CUSTOM_MEMORY_MANAGEMENT) {
            init();
        }
    }
    
    private void init() {
        try {
            InputStream mySafeConfigInputStream = 
                    CustomMemoryManagementInstrumenter.class.getClassLoader().
                        getResourceAsStream("mysafe-config.properties");
            if (mySafeConfigInputStream != null) {
                Properties mySafeConfigProps = new Properties();
                mySafeConfigProps.load(mySafeConfigInputStream);
                for (String propName : mySafeConfigProps.stringPropertyNames()) {
                    String propValue = mySafeConfigProps.getProperty(propName);
                    String[] propNameParts = propName.split("#");
                    if (propNameParts.length != 2) {
                        System.err.println("Invalid memory management point name: " + propName + 
                                           ". It must be in the form of \"<class_name>#<method_name>\".");
                        continue;
                    }
                    String className = propNameParts[0];
                    String methodName = propNameParts[1];
                    if ("ALLOCATION_POINT".equals(propValue)) {
                        CONFIGURED_ALLOCATION_POINTS.put(className, methodName);
                        System.out.println("Custom memory allocation point: " + 
                                           "className=" + className + ", methodName=" + methodName);
                    } else if ("FREE_POINT".equals(propValue)) {
                        CONFIGURED_FREE_POINTS.put(className, methodName);
                        System.out.println("Custom memory free point: " + 
                                           "className=" + className + ", methodName=" + methodName);
                    } else if ("REALLOCATION_POINT".equals(propValue)) {
                        CONFIGURED_REALLOCATION_POINTS.put(className, methodName);
                        System.out.println("Custom memory reallocation point: " + 
                                           "className=" + className + ", methodName=" + methodName);
                    } else {
                        System.err.println("Invalid memory management point type: " + propValue + 
                                           ". It can only be one of the " + 
                                           "\"ALLOCATION_POINT\", \"FREE_POINT\" and \"REALLOCATION_POINT\".");
                    }
                }
            }    
        } catch (Throwable t) {
            System.err.println("Error occured while trying to load config from " + 
                               "\"mysafe-config.properties\" file: " + t.getMessage());
            t.printStackTrace();
        }
        CP.importPackage("tr.com.serkanozal.mysafe");
        CP.importPackage("tr.com.serkanozal.mysafe.impl");
    }
    
    @Override
    public byte[] instrument(String className, byte[] classData) {
        if (USE_CUSTOM_MEMORY_MANAGEMENT) {
            try {
                CtClass clazz = CP.makeClass(new ByteArrayInputStream(classData));
                for (CtMethod method : clazz.getDeclaredMethods()) {
                    if (method.getName().equals(CONFIGURED_ALLOCATION_POINTS.get(clazz.getName())) 
                        || 
                        method.hasAnnotation(AllocationPoint.class)) {
                        instrumentAllocationPoint(clazz, method);
                    } else if (method.getName().equals(CONFIGURED_FREE_POINTS.get(clazz.getName()))
                               ||
                               method.hasAnnotation(FreePoint.class)) {
                        instrumentFreePoint(clazz, method);
                    } else if (method.getName().equals(CONFIGURED_REALLOCATION_POINTS.get(clazz.getName()))
                               ||
                               method.hasAnnotation(ReallocationPoint.class)) {
                        instrumentReallocationPoint(clazz, method);
                    }
                }
                return clazz.toBytecode();
            } catch (Throwable t) {
                System.err.println("Skipping instrumentation on " + className + " class. " + 
                                   "Because an error occured while instrumenting " + className + 
                                   " for custom memory management: " + t.getMessage());
                t.printStackTrace();
            }
            return classData;
        } else {
            return classData;
        }    
    }

    private void instrumentAllocationPoint(CtClass clazz, CtMethod method) 
            throws NotFoundException, CannotCompileException {
        /*
         * Annotated method must be in the form of 
         * "long $YOUR_ALLOCATION_METHOD_NAME$(long size, ...)"
         * by given parameter order.
         * As you can see, 
         *      - There might be other parameters rather than "size".
         *      - Return type can only be "long" and it must be allocated address.
         */
        
        CtClass returnType = method.getReturnType();
        CtClass[] paramTypes = method.getParameterTypes();
        
        if (paramTypes.length < 1) {
            throw new IllegalStateException("Parameter count must be >= 1. " +
                                            "Annotated method must be in the form of " + 
                                            "\"long $YOUR_ALLOCATION_METHOD_NAME$(long size, ...)\"");
        }
        if (!long.class.getName().equals(returnType.getName())) {
            throw new IllegalStateException("Return must be \"long\" and it must be allocated memory address. " +
                                            "Annotated method must be in the form of " + 
                                            "\"long $YOUR_ALLOCATION_METHOD_NAME$(long size, ...)\"");
        }
        if (!long.class.getName().equals(paramTypes[0].getName())) {
            throw new IllegalStateException("First parameter must be long and it must be allocation size. " +
                                            "Annotated method must be in the form of " + 
                                            "\"long $YOUR_ALLOCATION_METHOD_NAME$(long size, ...)\"");
        }

        String methodName = method.getName();
        String generatedMethodName = methodName;
        String actualMethodName = methodName + "$$$MySafe$$$";
        
        method.setName(actualMethodName);

        CtMethod generatedMethod = 
                new CtMethod(method.getReturnType(), 
                             generatedMethodName, 
                             paramTypes, 
                             method.getDeclaringClass());
        generatedMethod.setModifiers(method.getModifiers());
        
        StringBuilder actualMethodCallSignature = new StringBuilder(actualMethodName);
        actualMethodCallSignature.append("(");
        for (int i = 0; i < paramTypes.length; i++) {
            if (i != 0) {
                actualMethodCallSignature.append(", ");
            }
            actualMethodCallSignature.append("$" + (i + 1));
        }
        actualMethodCallSignature.append(");");
        
        /*
            beforeAllocateMemory(size);
            long address = doAllocateMemory(size, ...);
            afterAllocateMemory(size, address);
            return address; 
        */
        StringBuilder generatedMethodBody = new StringBuilder();
        generatedMethodBody.append("{").append("\n");
        generatedMethodBody.append("UnsafeDelegator.beforeAllocateMemory($1);").append("\n");
        generatedMethodBody.append("long address$$$MySafe$$$ = ").append(actualMethodCallSignature.toString()).append("\n");
        generatedMethodBody.append("UnsafeDelegator.afterAllocateMemory($1, address$$$MySafe$$$);").append("\n");
        generatedMethodBody.append("return address$$$MySafe$$$;").append("\n");
        generatedMethodBody.append("}");
        generatedMethod.setBody(generatedMethodBody.toString());

        clazz.addMethod(generatedMethod);
    }
    
    private void instrumentFreePoint(CtClass clazz, CtMethod method) 
            throws NotFoundException, CannotCompileException {
        /*
         * Annotated method must be in the form of 
         * "* $YOUR_FREE_METHOD_NAME$(long address, ...)"
         * by given parameter order.
         * As you can see, 
         *      - There might be other parameters rather than "address".
         *      - Return type can only be "void".
         */
        
        CtClass returnType = method.getReturnType();
        CtClass[] paramTypes = method.getParameterTypes();
        boolean returnsValue = null != returnType && !void.class.getName().equals(returnType.getName());
        
        if (paramTypes.length < 1) {
            throw new IllegalStateException("Parameter count must be >= 1. " +
                                            "Annotated method must be in the form of " + 
                                            "\"void $YOUR_FREE_METHOD_NAME$(long address, ...)\"");
        }
        if (returnsValue) {
            throw new IllegalStateException("Return type must be \"void\". " +
                                            "Annotated method must be in the form of " + 
                                            "\"void $YOUR_FREE_METHOD_NAME$(long address, ...)\"");
        }
        if (!long.class.getName().equals(paramTypes[0].getName())) {
            throw new IllegalStateException("First parameter must be long and it must be address. " +
                                            "Annotated method must be in the form of " + 
                                            "\"void $YOUR_FREE_METHOD_NAME$(long address, ...)\"");
        }
        
        String methodName = method.getName();
        String generatedMethodName = methodName;
        String actualMethodName = methodName + "$$$MySafe$$$";
        
        method.setName(actualMethodName);

        CtMethod generatedMethod = 
                new CtMethod(method.getReturnType(), 
                             generatedMethodName, 
                             paramTypes, 
                             method.getDeclaringClass());
        generatedMethod.setModifiers(method.getModifiers());
        
        StringBuilder actualMethodCallSignature = new StringBuilder(actualMethodName);
        actualMethodCallSignature.append("(");
        for (int i = 0; i < paramTypes.length; i++) {
            if (i != 0) {
                actualMethodCallSignature.append(", ");
            }
            actualMethodCallSignature.append("$" + (i + 1));
        }
        actualMethodCallSignature.append(");");
        
        /*
            long size = beforeFreeMemory(address);
            if (size != INVALID) {
                doFreeMemory(address, ...);
            }
            afterFreeMemory(address, size);   
        */
        StringBuilder generatedMethodBody = new StringBuilder();
        generatedMethodBody.append("{").append("\n");
        generatedMethodBody.append("long size$$$MySafe$$$ = UnsafeDelegator.beforeFreeMemory($1);").append("\n");
        generatedMethodBody.append("if (size$$$MySafe$$$ != ").append(INVALID).append(") {").append("\n");
        generatedMethodBody.append("\t").append(actualMethodCallSignature.toString()).append("\n");
        generatedMethodBody.append("}").append("\n");
        generatedMethodBody.append("UnsafeDelegator.afterFreeMemory($1, size$$$MySafe$$$);").append("\n");
        generatedMethodBody.append("}");
        generatedMethod.setBody(generatedMethodBody.toString());
        
        clazz.addMethod(generatedMethod);
    }
    
    private void instrumentReallocationPoint(CtClass clazz, CtMethod method) 
            throws NotFoundException, CannotCompileException {
        /*
         * Annotated method must be in the form of 
         * "* $YOUR_REALLOCATION_METHOD_NAME$(long oldAddress, long newSize, ...)"
         * by given parameter order.
         * As you can see, 
         *      - There might be other parameters rather than "oldAddress" and "newSize".
         *      - Return type can only be "long" and it must be reallocated address.
         */
        
        CtClass returnType = method.getReturnType();
        CtClass[] paramTypes = method.getParameterTypes();
        
        if (paramTypes.length < 2) {
            throw new IllegalStateException("Parameter count must be >= 2. " +
                                            "Annotated method must be in the form of " + 
                                            "\"long $YOUR_REALLOCATION_METHOD_NAME$(long oldAddress, long newSize, ...)\"");
        }
        if (!long.class.getName().equals(returnType.getName())) {
            throw new IllegalStateException("Return must be \"long\" and it must be reallocated memory address. " +
                                            "Annotated method must be in the form of " + 
                                            "\"long $YOUR_ALLOCATION_METHOD_NAME$(long size, ...)\"");
        }
        if (!long.class.getName().equals(paramTypes[0].getName()) 
            ||
            !long.class.getName().equals(paramTypes[1].getName())) {
            throw new IllegalStateException("First two parameters must be long and they must be old address and new size. " +
                                            "Annotated method must be in the form of " + 
                                            "\"long $YOUR_REALLOCATION_METHOD_NAME$(long oldAddress, long newSize, ...)\"");
        }
        
        String methodName = method.getName();
        String generatedMethodName = methodName;
        String actualMethodName = methodName + "$$$MySafe$$$";
        
        method.setName(actualMethodName);

        CtMethod generatedMethod = 
                new CtMethod(method.getReturnType(), 
                             generatedMethodName, 
                             paramTypes, 
                             method.getDeclaringClass());
        generatedMethod.setModifiers(method.getModifiers());
        
        StringBuilder actualMethodCallSignature = new StringBuilder(actualMethodName);
        actualMethodCallSignature.append("(");
        for (int i = 0; i < paramTypes.length; i++) {
            if (i != 0) {
                actualMethodCallSignature.append(", ");
            }
            actualMethodCallSignature.append("$" + (i + 1));
        }
        actualMethodCallSignature.append(");");
        
        /*
            long oldSize = beforeReallocateMemory(oldAddress);
            long newAddress = INVALID;
            if (oldSize != INVALID) {
                newAddress = doReallocateMemory(oldAddress, newSize, ...);
            }
            afterReallocateMemory(oldAddress, oldSize, newAddress, newSize);
            return newAddress; 
        */
        StringBuilder generatedMethodBody = new StringBuilder();
        generatedMethodBody.append("{").append("\n");
        generatedMethodBody.append("long oldSize$$$MySafe$$$ = UnsafeDelegator.beforeReallocateMemory($1);").append("\n");
        generatedMethodBody.append("long newAddress$$$MySafe$$$ = ").append(INVALID).append(";\n");
        generatedMethodBody.append("if (oldSize$$$MySafe$$$ != ").append(INVALID).append(") {").append("\n");
        generatedMethodBody.append("\t").append("newAddress$$$MySafe$$$ = ").append(actualMethodCallSignature.toString()).append("\n");
        generatedMethodBody.append("}").append("\n");
        generatedMethodBody.append("UnsafeDelegator.afterReallocateMemory").
                                append("(").
                                    append("$1, ").
                                    append("oldSize$$$MySafe$$$, ").
                                    append("newAddress$$$MySafe$$$, ").
                                    append("$2").
                                append(");").    
                            append("\n");
        generatedMethodBody.append("return newAddress$$$MySafe$$$;").append("\n");
        generatedMethodBody.append("}");
        generatedMethod.setBody(generatedMethodBody.toString());
        
        clazz.addMethod(generatedMethod);
    }
    
}
