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
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import tr.com.serkanozal.mysafe.config.AllocationPoint;
import tr.com.serkanozal.mysafe.config.AllocationPointConfig;
import tr.com.serkanozal.mysafe.config.FreePoint;
import tr.com.serkanozal.mysafe.config.FreePointConfig;
import tr.com.serkanozal.mysafe.config.ReallocationPoint;
import tr.com.serkanozal.mysafe.config.ReallocationPointConfig;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LineNumberAttribute;

class CustomMemoryManagementInstrumenter implements MySafeInstrumenter {

    private final boolean USE_CUSTOM_MEMORY_MANAGEMENT = 
            Boolean.getBoolean("mysafe.useCustomMemoryManagement");
    private final String CUSTOM_MEMORY_MANAGEMENT_PACKAGE_PREFIX = 
            System.getProperty("mysafe.customMemoryManagementPackagePrefix");
    private final ClassPool CP = new MySafeClassPool();
    private Map<String, AllocationPointConfig> configuredAllocationPoints;
    private Map<String, FreePointConfig> configuredFreePoints;
    private Map<String, ReallocationPointConfig> configuredReallocationPoints;
    
    CustomMemoryManagementInstrumenter() {
        if (USE_CUSTOM_MEMORY_MANAGEMENT) {
            init();
        }
        CP.importPackage("tr.com.serkanozal.mysafe");
        CP.importPackage("tr.com.serkanozal.mysafe.impl");
    }
    
    private static class MySafeClassPool extends ClassPool {
        
        private MySafeClassPool() {
            appendSystemPath();
            importPackage("tr.com.serkanozal.mysafe");
            importPackage("tr.com.serkanozal.mysafe.impl");
        }
        
        @Override
        protected void cacheCtClass(String classname, CtClass c, boolean dynamic) {
            // No caching
        }
        
        @Override
        protected CtClass removeCached(String classname) {
            // Because there is no caching
            return null;
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
                    if (propNameParts.length < 2) {
                        System.err.println("Invalid memory management point name: " + propName + 
                                           ". It must be in the form of \"<class_name>#<method_name>(#<optional-config>)*\".");
                        continue;
                    }
                    String className = propNameParts[0];
                    String methodName = propNameParts[1];
                    if ("ALLOCATION_POINT".equals(propValue)) {
                        if (propNameParts.length > 3) {
                            System.err.println("Invalid allocation point definition: " + propName + 
                                               ". It must be in the form of \"<class_name>#<method_name>(#<size_parameter_order>)?\".");
                            continue;
                        }
                        AllocationPointConfig allocationPointConfig = null;
                        if (propNameParts.length == 2) {
                            allocationPointConfig = new AllocationPointConfig();
                        } else {
                            allocationPointConfig = new AllocationPointConfig(Integer.parseInt(propNameParts[2]));
                        }
                        if (configuredAllocationPoints == null) {
                            configuredAllocationPoints = new HashMap<String, AllocationPointConfig>();
                        }
                        configuredAllocationPoints.put(className + "#" + methodName, allocationPointConfig);
                        System.out.println("Custom memory allocation point: " + 
                                           "className=" + className + ", methodName=" + methodName);
                    } else if ("FREE_POINT".equals(propValue)) {
                        if (propNameParts.length > 3) {
                            System.err.println("Invalid free point definition: " + propName + 
                                               ". It must be in the form of \"<class_name>#<method_name>(#<address_parameter_order>)?\".");
                            continue;
                        }
                        FreePointConfig freePointConfig = null;
                        if (propNameParts.length == 2) {
                            freePointConfig = new FreePointConfig();
                        } else {
                            freePointConfig = new FreePointConfig(Integer.parseInt(propNameParts[2]));
                        }
                        if (configuredFreePoints == null) {
                            configuredFreePoints = new HashMap<String, FreePointConfig>();
                        }
                        configuredFreePoints.put(className + "#" + methodName, freePointConfig);
                        System.out.println("Custom memory free point: " + 
                                           "className=" + className + ", methodName=" + methodName);
                    } else if ("REALLOCATION_POINT".equals(propValue)) {
                        if (propNameParts.length > 4) {
                            System.err.println("Invalid reallocation point definition: " + propName + ". It must be in the form of " + 
                                               "\"<class_name>#<method_name>(#<old_address_parameter_order>(#<new_size_parameter_order>)?)?\".");
                            continue;
                        }
                        ReallocationPointConfig reallocationPointConfig = null;
                        if (propNameParts.length == 2) {
                            reallocationPointConfig = new ReallocationPointConfig();
                        } else if (propNameParts.length == 3) {
                            reallocationPointConfig = new ReallocationPointConfig(Integer.parseInt(propNameParts[2]));
                        } else {
                            reallocationPointConfig = new ReallocationPointConfig(Integer.parseInt(propNameParts[2]),
                                                                                  Integer.parseInt(propNameParts[3]));
                        }
                        if (configuredReallocationPoints == null) {
                            configuredReallocationPoints = new HashMap<String, ReallocationPointConfig>();
                        }
                        configuredReallocationPoints.put(className + "#" + methodName, reallocationPointConfig);
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
    }
    
    @Override
    public byte[] instrument(String className, byte[] classData) {
        if (USE_CUSTOM_MEMORY_MANAGEMENT) {
            if (CUSTOM_MEMORY_MANAGEMENT_PACKAGE_PREFIX != null && !className.startsWith(CUSTOM_MEMORY_MANAGEMENT_PACKAGE_PREFIX)) {
                return classData;
            }
            try {
                CtClass clazz = CP.makeClass(new ByteArrayInputStream(classData));
                boolean instrumented = false;
                for (CtMethod method : clazz.getDeclaredMethods()) {
                    AllocationPointConfig allocationPointConfig = null;
                    Object allocationPoint = null;
                    FreePointConfig freePointConfig = null;
                    Object freePoint = null;
                    ReallocationPointConfig reallocationPointConfig = null;
                    Object reallocationPoint = null;
                    if ((
                            configuredAllocationPoints != null 
                            && 
                            (allocationPointConfig = configuredAllocationPoints.get(clazz.getName() + "#" + method.getName())) != null
                        )    
                        || 
                        (allocationPoint = method.getAnnotation(AllocationPoint.class)) != null) {
                        if (allocationPointConfig == null) {
                            Method m = allocationPoint.getClass().getMethod("sizeParameterOrder");
                            allocationPointConfig = new AllocationPointConfig((Integer) m.invoke(allocationPoint));
                        }
                        instrumentAllocationPoint(clazz, method, allocationPointConfig);
                        instrumented = true;
                    } else if ((
                                    configuredFreePoints != null
                                    &&
                                    (freePointConfig = configuredFreePoints.get(clazz.getName() + "#" + method.getName())) != null 
                               )    
                               || 
                               (freePoint = method.getAnnotation(FreePoint.class)) != null) {
                        if (freePointConfig == null) {
                            Method m = freePoint.getClass().getMethod("addressParameterOrder");
                            freePointConfig = new FreePointConfig((Integer) m.invoke(freePoint));
                        }
                        instrumentFreePoint(clazz, method, freePointConfig);
                        instrumented = true;
                    } else if ((
                                    configuredReallocationPoints != null 
                                    &&
                                    (reallocationPointConfig = configuredReallocationPoints.get(clazz.getName() + "#" + method.getName())) != null
                               )    
                               || 
                               (reallocationPoint = method.getAnnotation(ReallocationPoint.class)) != null) {
                        if (reallocationPointConfig == null) {
                            Class<?> cls = reallocationPoint.getClass();
                            Method m1 = cls.getMethod("oldAddressParameterOrder");
                            Method m2 = cls.getMethod("newSizeParameterOrder");
                            reallocationPointConfig = 
                                    new ReallocationPointConfig((Integer) m1.invoke(reallocationPoint), 
                                                                (Integer) m2.invoke(reallocationPoint));
                        }
                        instrumentReallocationPoint(clazz, method, reallocationPointConfig);
                        instrumented = true;
                    }
                }
                if (instrumented) {
                    return clazz.toBytecode();
                } else {
                    return classData;
                }
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

    @SuppressWarnings("unchecked")
    private void instrumentAllocationPoint(CtClass clazz, CtMethod method, AllocationPointConfig allocationPointConfig) 
            throws NotFoundException, CannotCompileException {
        CtClass returnType = method.getReturnType();
        CtClass[] paramTypes = method.getParameterTypes();
        int sizeParamOrder = allocationPointConfig.sizeParameterOrder;
        
        if (sizeParamOrder < 1 || sizeParamOrder > paramTypes.length) {
            throw new IllegalStateException("Order of the `size` parameter, which is " + sizeParamOrder + ", must be positive number " +
                                            "and it cannot be bigger than parameter count which is " + paramTypes.length + ".");
        }
        if (!long.class.getName().equals(returnType.getName())) {
            throw new IllegalStateException("Return must be \"long\" and it must be allocated memory address. " +
                                            "Annotated method must be in the form of " + 
                                            "\"long $YOUR_ALLOCATION_METHOD_NAME$(long size, ...)\" by default " + 
                                            "and order of `size` parameter can be configured.");
        }
        if (!long.class.getName().equals(paramTypes[sizeParamOrder - 1].getName())) {
            throw new IllegalStateException("Size parameter must be long and it must be allocation size. " +
                                            "Annotated method must be in the form of " + 
                                            "\"long $YOUR_ALLOCATION_METHOD_NAME$(long size, ...)\" by default " + 
                                            "and order of `size` parameter can be configured.");
        }

        String methodName = method.getName();
        String generatedMethodName = methodName;
        String actualMethodName = methodName + "$$$MySafe$$$";
        
        CtMethod generatedMethod = new CtMethod(method, method.getDeclaringClass(), null);
        generatedMethod.setName(generatedMethodName);
        method.setName(actualMethodName);
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
        generatedMethodBody.append("MySafeDelegator.beforeAllocateMemory($").append(sizeParamOrder).append(");").append("\n");
        generatedMethodBody.append("long address$$$MySafe$$$ = ").append(actualMethodCallSignature.toString()).append("\n");
        generatedMethodBody.append("MySafeDelegator.afterAllocateMemory($").append(sizeParamOrder).append(", address$$$MySafe$$$);").append("\n");
        generatedMethodBody.append("return address$$$MySafe$$$;").append("\n");
        generatedMethodBody.append("}");
        generatedMethod.setBody(generatedMethodBody.toString());
        
        CodeAttribute originalCodeAttribute = method.getMethodInfo().getCodeAttribute();
        LineNumberAttribute originalLineNumberAttribute = (LineNumberAttribute) originalCodeAttribute.getAttribute(LineNumberAttribute.tag);
        CodeAttribute generatedCodeAttribute = generatedMethod.getMethodInfo().getCodeAttribute();
        generatedCodeAttribute.getAttributes().add(originalLineNumberAttribute);

        clazz.addMethod(generatedMethod);
    }
    
    @SuppressWarnings("unchecked")
    private void instrumentFreePoint(CtClass clazz, CtMethod method, FreePointConfig freePointConfig) 
            throws NotFoundException, CannotCompileException {
        CtClass returnType = method.getReturnType();
        CtClass[] paramTypes = method.getParameterTypes();
        boolean returnsValue = null != returnType && !void.class.getName().equals(returnType.getName());
        int addressParamOrder = freePointConfig.addressParameterOrder;
        
        if (addressParamOrder < 1 || addressParamOrder > paramTypes.length) {
            throw new IllegalStateException("Order of the `address` parameter, which is " + addressParamOrder + ", must be positive number " +
                                            "and it cannot be bigger than parameter count which is " + paramTypes.length + ".");
        }
        if (returnsValue) {
            throw new IllegalStateException("Return type must be \"void\". " +
                                            "Annotated method must be in the form of " + 
                                            "\"void $YOUR_FREE_METHOD_NAME$(long address, ...)\" by default " + 
                                            "and order of the `address` parameter can be configured.");
        }
        if (!long.class.getName().equals(paramTypes[addressParamOrder - 1].getName())) {
            throw new IllegalStateException("First parameter must be long and it must be address. " +
                                            "Annotated method must be in the form of " + 
                                            "\"void $YOUR_FREE_METHOD_NAME$(long address, ...)\" by default " + 
                                            "and order of the `address` parameter can be configured.");
        }
        
        String methodName = method.getName();
        String generatedMethodName = methodName;
        String actualMethodName = methodName + "$$$MySafe$$$";
        
        CtMethod generatedMethod = new CtMethod(method, method.getDeclaringClass(), null);
        generatedMethod.setName(generatedMethodName);
        method.setName(actualMethodName);
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
        generatedMethodBody.append("long size$$$MySafe$$$ = MySafeDelegator.beforeFreeMemory($").append(addressParamOrder).append(");").append("\n");
        generatedMethodBody.append("if (size$$$MySafe$$$ != ").append(INVALID).append(") {").append("\n");
        generatedMethodBody.append("\t").append(actualMethodCallSignature.toString()).append("\n");
        generatedMethodBody.append("}").append("\n");
        generatedMethodBody.append("MySafeDelegator.afterFreeMemory($").append(addressParamOrder).append(", size$$$MySafe$$$);").append("\n");
        generatedMethodBody.append("}");
        generatedMethod.setBody(generatedMethodBody.toString());
        
        CodeAttribute originalCodeAttribute = method.getMethodInfo().getCodeAttribute();
        LineNumberAttribute originalLineNumberAttribute = (LineNumberAttribute) originalCodeAttribute.getAttribute(LineNumberAttribute.tag);
        CodeAttribute generatedCodeAttribute = generatedMethod.getMethodInfo().getCodeAttribute();
        generatedCodeAttribute.getAttributes().add(originalLineNumberAttribute);
        
        clazz.addMethod(generatedMethod);
    }
    
    @SuppressWarnings("unchecked")
    private void instrumentReallocationPoint(CtClass clazz, CtMethod method, ReallocationPointConfig reallocationPointConfig) 
            throws NotFoundException, CannotCompileException {
        CtClass returnType = method.getReturnType();
        CtClass[] paramTypes = method.getParameterTypes();
        int oldAddressParamOrder = reallocationPointConfig.oldAddressParameterOrder;
        int newSizeParamOrder = reallocationPointConfig.newSizeParameterOrder;
        
        if (oldAddressParamOrder < 1 || oldAddressParamOrder > paramTypes.length
            ||
            newSizeParamOrder < 1 || newSizeParamOrder > paramTypes.length) {
            throw new IllegalStateException("Orders of the `old address` and `new size` parameters, which are " + 
                                            oldAddressParamOrder + " and " + newSizeParamOrder + ", must be positive number " +
                                            "and they cannot be bigger than parameter count which is " + paramTypes.length + ".");
        }
        if (!long.class.getName().equals(returnType.getName())) {
            throw new IllegalStateException("Return must be \"long\" and it must be reallocated memory address. " +
                                            "Annotated method must be in the form of " + 
                                            "\"long $YOUR_ALLOCATION_METHOD_NAME$(long size, ...)\" by default " + 
                                            "and order of the `oldAddress` `newSize` parameters can be configured.");
        }
        if (!long.class.getName().equals(paramTypes[oldAddressParamOrder - 1].getName()) 
            ||
            !long.class.getName().equals(paramTypes[newSizeParamOrder - 1].getName())) {
            throw new IllegalStateException("First two parameters must be long and they must be old address and new size. " +
                                            "Annotated method must be in the form of " + 
                                            "\"long $YOUR_REALLOCATION_METHOD_NAME$(long oldAddress, long newSize, ...)\" by default " + 
                                            "and order of the `oldAddress` `newSize` parameters can be configured.");
        }
        
        String methodName = method.getName();
        String generatedMethodName = methodName;
        String actualMethodName = methodName + "$$$MySafe$$$";
        
        CtMethod generatedMethod = new CtMethod(method, method.getDeclaringClass(), null);
        generatedMethod.setName(generatedMethodName);
        method.setName(actualMethodName);
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
        generatedMethodBody.append("long oldSize$$$MySafe$$$ = MySafeDelegator.beforeReallocateMemory($").append(oldAddressParamOrder).append(");").append("\n");
        generatedMethodBody.append("long newAddress$$$MySafe$$$ = ").append(INVALID).append(";\n");
        generatedMethodBody.append("if (oldSize$$$MySafe$$$ != ").append(INVALID).append(") {").append("\n");
        generatedMethodBody.append("\t").append("newAddress$$$MySafe$$$ = ").append(actualMethodCallSignature.toString()).append("\n");
        generatedMethodBody.append("}").append("\n");
        generatedMethodBody.append("MySafeDelegator.afterReallocateMemory").
                                append("(").
                                    append("$").append(oldAddressParamOrder).append(", ").
                                    append("oldSize$$$MySafe$$$, ").
                                    append("newAddress$$$MySafe$$$, ").
                                    append("$").append(newSizeParamOrder).
                                append(");").    
                            append("\n");
        generatedMethodBody.append("return newAddress$$$MySafe$$$;").append("\n");
        generatedMethodBody.append("}");
        generatedMethod.setBody(generatedMethodBody.toString());
        
        CodeAttribute originalCodeAttribute = method.getMethodInfo().getCodeAttribute();
        LineNumberAttribute originalLineNumberAttribute = (LineNumberAttribute) originalCodeAttribute.getAttribute(LineNumberAttribute.tag);
        CodeAttribute generatedCodeAttribute = generatedMethod.getMethodInfo().getCodeAttribute();
        generatedCodeAttribute.getAttributes().add(originalLineNumberAttribute);
        
        clazz.addMethod(generatedMethod);
    }
    
}
