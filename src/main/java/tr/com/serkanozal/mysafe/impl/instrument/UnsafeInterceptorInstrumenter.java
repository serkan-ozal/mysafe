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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import tr.com.serkanozal.mysafe.config.IgnoreByMySafe;

class UnsafeInterceptorInstrumenter implements MySafeInstrumenter {

    private final boolean USE_CUSTOM_MEMORY_MANAGEMENT = 
            Boolean.getBoolean("mysafe.useCustomMemoryManagement");
    private final boolean SAFE_MEMORY_ACCESS_MODE_ENABLED;
    
    UnsafeInterceptorInstrumenter() {
        if (USE_CUSTOM_MEMORY_MANAGEMENT) {
            SAFE_MEMORY_ACCESS_MODE_ENABLED = false;
        } else {
            SAFE_MEMORY_ACCESS_MODE_ENABLED = Boolean.getBoolean("mysafe.enableSafeMemoryAccessMode");
        }
    }
    
    @Override
    public byte[] instrument(String className, byte[] classData) {
        if (!SAFE_MEMORY_ACCESS_MODE_ENABLED && USE_CUSTOM_MEMORY_MANAGEMENT) {
            return classData;
        }
        
        if (className.startsWith("tr.com.serkanozal.mysafe.impl")
                || className.startsWith("org.cliffc.high_scale_lib")
                || "sun.misc.Unsafe".equals(className)) {
            return classData;
        }

        ClassReader cr = new ClassReader(classData);
        UnsafeClassWriter cw = new UnsafeClassWriter(USE_CUSTOM_MEMORY_MANAGEMENT, SAFE_MEMORY_ACCESS_MODE_ENABLED);
        try {
            cr.accept(cw, ClassReader.EXPAND_FRAMES);
            if (cw.unsafeUsageExist) {
                return cw.toByteArray();
            } else {
                return classData;
            }
        } catch (IgnoreUnsafeException e) {
            return classData;
        }
    }
    
    @SuppressWarnings("serial")
    private static class IgnoreUnsafeException extends RuntimeException {
        
    }
    
    private static class UnsafeClassWriter extends ClassWriter {
        
        private static final String IGNORE_UNSAFE_ANNOTATION_DESC = 
                "L" + IgnoreByMySafe.class.getName().replace(".", "/") + ";";
        
        private final boolean useCustomMemoryManagement;
        private final boolean safeMemoryAccessModeEnabled;
        private boolean unsafeUsageExist = false;

        private UnsafeClassWriter(boolean useCustomMemoryManagement, boolean safeMemoryAccessModeEnabled) {
            super(0);
            this.useCustomMemoryManagement = useCustomMemoryManagement;
            this.safeMemoryAccessModeEnabled = safeMemoryAccessModeEnabled;
        }
        
        @Override
        public AnnotationVisitor visitAnnotation(String annotationDesc, boolean isVisibleAtRuntime) {
            if (IGNORE_UNSAFE_ANNOTATION_DESC.equals(annotationDesc)) {
                throw new IgnoreUnsafeException();
            }
            return super.visitAnnotation(annotationDesc, isVisibleAtRuntime);
        }
        
        @Override
        public MethodVisitor visitMethod(final int access, final String name,
                final String desc, final String signature, final String[] exceptions) {
            MethodVisitor mv =  super.visitMethod(access, name, desc, signature, exceptions);
            return new MethodAdapter(mv) {
                @Override
                public void visitMethodInsn(int opcode, String ownerClassName,
                        String methodName, String signature) {
                    if ("sun/misc/Unsafe".equals(ownerClassName)) {  
                        unsafeUsageExist = true;

                        if ("allocateMemory".equals(methodName) && !useCustomMemoryManagement) {
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC, 
                                    "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                    "allocateMemory", 
                                    "(Lsun/misc/Unsafe;J)J");   
                        } else if ("freeMemory".equals(methodName) && !useCustomMemoryManagement) {
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC, 
                                    "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                    "freeMemory", 
                                    "(Lsun/misc/Unsafe;J)V");   
                        } else if ("reallocateMemory".equals(methodName) && !useCustomMemoryManagement) {
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC, 
                                    "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                    "reallocateMemory", 
                                    "(Lsun/misc/Unsafe;JJ)J");   
                        } 
                        
                        /////////////////////////////////////////////////////////////////////////////
                        
                        else {
                            if (!safeMemoryAccessModeEnabled) {
                                super.visitMethodInsn(opcode, ownerClassName, methodName, signature);
                            }
                            
                            /////////////////////////////////////////////////////////////////////////////
                            
                            else if ("getBoolean".equals(methodName) && "(Ljava/lang/Object;I)Z".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getBoolean", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;I)Z");   
                            } else if ("putBoolean".equals(methodName) && "(Ljava/lang/Object;IZ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putBoolean", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;IZ)V");   
                            } else if ("getByte".equals(methodName) && "(Ljava/lang/Object;I)B".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getByte", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;I)B");   
                            } else if ("putByte".equals(methodName) && "(Ljava/lang/Object;IB)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putByte", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;IB)V");   
                            } else if ("getChar".equals(methodName) && "(Ljava/lang/Object;I)C".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getChar", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;I)C");   
                            } else if ("putChar".equals(methodName) && "(Ljava/lang/Object;IC)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putChar", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;IC)V");   
                            } else if ("getShort".equals(methodName) && "(Ljava/lang/Object;I)S".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getShort", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;I)S");   
                            } else if ("putShort".equals(methodName) && "(Ljava/lang/Object;IS)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putShort", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;IS)V");   
                            } else if ("getInt".equals(methodName) && "(Ljava/lang/Object;I)I".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getInt", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;I)I");   
                            } else if ("putInt".equals(methodName) && "(Ljava/lang/Object;II)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putInt", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;II)V");   
                            } else if ("getFloat".equals(methodName) && "(Ljava/lang/Object;I)F".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getFloat", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;I)F");   
                            } else if ("putFloat".equals(methodName) && "(Ljava/lang/Object;IF)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putFloat", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;IF)V");   
                            } else if ("getLong".equals(methodName) && "(Ljava/lang/Object;I)J".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getLong", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;I)J");   
                            } else if ("putLong".equals(methodName) && "(Ljava/lang/Object;IJ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putLong", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;IJ)V");   
                            } else if ("getDouble".equals(methodName) && "(Ljava/lang/Object;I)D".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getDouble", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;I)D");   
                            } else if ("putDouble".equals(methodName) && "(Ljava/lang/Object;ID)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putDouble", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;ID)V");   
                            } else if ("getObject".equals(methodName) && "(Ljava/lang/Object;I)Ljava/lang/Object;".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getObject", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;I)Ljava/lang/Object;");   
                            } else if ("putObject".equals(methodName) && "(Ljava/lang/Object;ILjava/lang/Object;)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putObject", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;ILjava/lang/Object;)V");   
                            }  
                            
                            /////////////////////////////////////////////////////////////////////////////
                            
                            else if ("getBoolean".equals(methodName) && "(Ljava/lang/Object;J)Z".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getBoolean", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)Z");   
                            } else if ("putBoolean".equals(methodName) && "(Ljava/lang/Object;JZ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putBoolean", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JZ)V");   
                            } else if ("getByte".equals(methodName) && "(Ljava/lang/Object;J)B".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getByte", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)B");   
                            } else if ("putByte".equals(methodName) && "(Ljava/lang/Object;JB)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putByte", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JB)V");   
                            } else if ("getChar".equals(methodName) && "(Ljava/lang/Object;J)C".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getChar", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)C");   
                            } else if ("putChar".equals(methodName) && "(Ljava/lang/Object;JC)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putChar", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JC)V");   
                            } else if ("getShort".equals(methodName) && "(Ljava/lang/Object;J)S".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getShort", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)S");   
                            } else if ("putShort".equals(methodName) && "(Ljava/lang/Object;JS)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putShort", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JS)V");   
                            } else if ("getInt".equals(methodName) && "(Ljava/lang/Object;J)I".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getInt", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)I");   
                            } else if ("putInt".equals(methodName) && "(Ljava/lang/Object;JI)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putInt", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JI)V");   
                            } else if ("getFloat".equals(methodName) && "(Ljava/lang/Object;J)F".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getFloat", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)F");   
                            } else if ("putFloat".equals(methodName) && "(Ljava/lang/Object;JF)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putFloat", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JF)V");   
                            } else if ("getLong".equals(methodName) && "(Ljava/lang/Object;J)J".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getLong", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)J");   
                            } else if ("putLong".equals(methodName) && "(Ljava/lang/Object;JJ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putLong", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JJ)V");   
                            } else if ("getDouble".equals(methodName) && "(Ljava/lang/Object;J)D".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getDouble", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)D");   
                            } else if ("putDouble".equals(methodName) && "(Ljava/lang/Object;JD)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putDouble", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JD)V");   
                            } else if ("getObject".equals(methodName) && "(Ljava/lang/Object;J)Ljava/lang/Object;".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getObject", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)Ljava/lang/Object;");   
                            } else if ("putObject".equals(methodName) && "(Ljava/lang/Object;JLjava/lang/Object;)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putObject", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JLjava/lang/Object;)V");   
                            }  
                            
                            /////////////////////////////////////////////////////////////////////////////
                            
                            else if ("getBooleanVolatile".equals(methodName) && "(Ljava/lang/Object;J)Z".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getBooleanVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)Z");   
                            } else if ("putBooleanVolatile".equals(methodName) && "(Ljava/lang/Object;JZ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putBooleanVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JZ)V");   
                            } else if ("getByteVolatile".equals(methodName) && "(Ljava/lang/Object;J)B".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getByteVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)B");   
                            } else if ("putByteVolatile".equals(methodName) && "(Ljava/lang/Object;JB)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putByteVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JB)V");   
                            } else if ("getCharVolatile".equals(methodName) && "(Ljava/lang/Object;J)C".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getCharVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)C");   
                            } else if ("putCharVolatile".equals(methodName) && "(Ljava/lang/Object;JC)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putCharVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JC)V");   
                            } else if ("getShortVolatile".equals(methodName) && "(Ljava/lang/Object;J)S".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getShortVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)S");   
                            } else if ("putShortVolatile".equals(methodName) && "(Ljava/lang/Object;JS)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putShortVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JS)V");   
                            } else if ("getIntVolatile".equals(methodName) && "(Ljava/lang/Object;J)I".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getIntVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)I");   
                            } else if ("putIntVolatile".equals(methodName) && "(Ljava/lang/Object;JI)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putIntVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JI)V");   
                            } else if ("getFloatVolatile".equals(methodName) && "(Ljava/lang/Object;J)F".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getFloatVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)F");   
                            } else if ("putFloatVolatile".equals(methodName) && "(Ljava/lang/Object;JF)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putFloatVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JF)V");   
                            } else if ("getLongVolatile".equals(methodName) && "(Ljava/lang/Object;J)J".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getLongVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)J");   
                            } else if ("putLongVolatile".equals(methodName) && "(Ljava/lang/Object;JJ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putLongVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JJ)V");   
                            } else if ("getDoubleVolatile".equals(methodName) && "(Ljava/lang/Object;J)D".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getDoubleVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)D");   
                            } else if ("putDoubleVolatile".equals(methodName) && "(Ljava/lang/Object;JD)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putDoubleVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JD)V");   
                            } else if ("getObjectVolatile".equals(methodName) && "(Ljava/lang/Object;J)Ljava/lang/Object;".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getObjectVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;J)Ljava/lang/Object;");   
                            } else if ("putObjectVolatile".equals(methodName) && "(Ljava/lang/Object;JLjava/lang/Object;)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putObjectVolatile", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JLjava/lang/Object;)V");   
                            }
                            
                            /////////////////////////////////////////////////////////////////////////////
                            
                            else if ("getBoolean".equals(methodName) && "(J)Z".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getBoolean", 
                                        "(Lsun/misc/Unsafe;J)Z");   
                            } else if ("putBoolean".equals(methodName) && "(JZ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putBoolean", 
                                        "(Lsun/misc/Unsafe;JZ)V");   
                            } else if ("getByte".equals(methodName) && "(J)B".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getByte", 
                                        "(Lsun/misc/Unsafe;J)B");   
                            } else if ("putByte".equals(methodName) && "(JB)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putByte", 
                                        "(Lsun/misc/Unsafe;JB)V");   
                            } else if ("getChar".equals(methodName) && "(J)C".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getChar", 
                                        "(Lsun/misc/Unsafe;J)C");   
                            } else if ("putChar".equals(methodName) && "(JC)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putChar", 
                                        "(Lsun/misc/Unsafe;JC)V");   
                            } else if ("getShort".equals(methodName) && "(J)S".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getShort", 
                                        "(Lsun/misc/Unsafe;J)S");   
                            } else if ("putShort".equals(methodName) && "(JS)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putShort", 
                                        "(Lsun/misc/Unsafe;JS)V");   
                            } else if ("getInt".equals(methodName) && "(J)I".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getInt", 
                                        "(Lsun/misc/Unsafe;J)I");   
                            } else if ("putInt".equals(methodName) && "(JI)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putInt", 
                                        "(Lsun/misc/Unsafe;JI)V");   
                            } else if ("getFloat".equals(methodName) && "(J)F".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getFloat", 
                                        "(Lsun/misc/Unsafe;J)F");   
                            } else if ("putFloat".equals(methodName) && "(JF)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putFloat", 
                                        "(Lsun/misc/Unsafe;JF)V");   
                            } else if ("getLong".equals(methodName) && "(J)J".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getLong", 
                                        "(Lsun/misc/Unsafe;J)J");   
                            } else if ("putLong".equals(methodName) && "(JJ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putLong", 
                                        "(Lsun/misc/Unsafe;JJ)V");   
                            } else if ("getDouble".equals(methodName) && "(J)D".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getDouble", 
                                        "(Lsun/misc/Unsafe;J)D");   
                            } else if ("putDouble".equals(methodName) && "(JD)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putDouble", 
                                        "(Lsun/misc/Unsafe;JD)V");   
                            } 
                            
                            /////////////////////////////////////////////////////////////////////////////
                            
                            else if ("getAddress".equals(methodName) && "(J)J".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getAddress", 
                                        "(Lsun/misc/Unsafe;J)J");   
                            } else if ("putAddress".equals(methodName) && "(JJ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putAddress", 
                                        "(Lsun/misc/Unsafe;JJ)V");   
                            } 
                            
                            /////////////////////////////////////////////////////////////////////////////
                            
                            else if ("setMemory".equals(methodName) && "(JJB)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "setMemory", 
                                        "(Lsun/misc/Unsafe;JJB)V");   
                            } else if ("setMemory".equals(methodName) && "(Ljava/lang/Object;JJB)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "setMemory", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JJB)V");   
                            } else if ("copyMemory".equals(methodName) && "(JJJ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "copyMemory", 
                                        "(Lsun/misc/Unsafe;JJJ)V");   
                            } else if ("copyMemory".equals(methodName) && "(Ljava/lang/Object;JLjava/lang/Object;JJ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "copyMemory", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JLjava/lang/Object;JJ)V");   
                            } 
                            
                            /////////////////////////////////////////////////////////////////////////////
                            
                            else if ("compareAndSwapInt".equals(methodName) && "(Ljava/lang/Object;JII)Z".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "compareAndSwapInt", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JII)Z");   
                            } else if ("compareAndSwapLong".equals(methodName) && "(Ljava/lang/Object;JJJ)Z".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "compareAndSwapLong", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JJJ)Z");   
                            } else if ("compareAndSwapObject".equals(methodName) && "(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "compareAndSwapLong", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z");   
                            } 
                            
                            /////////////////////////////////////////////////////////////////////////////
                            
                            else if ("putOrderedInt".equals(methodName) && "(Ljava/lang/Object;JI)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putOrderedInt", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JI)V");   
                            } else if ("putOrderedLong".equals(methodName) && "(Ljava/lang/Object;JJ)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putOrderedLong", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JJ)V");   
                            } else if ("putOrderedObject".equals(methodName) && "(Ljava/lang/Object;JLjava/lang/Object;)V".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "putOrderedObject", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JLjava/lang/Object;)V");   
                            } 
                            
                            /////////////////////////////////////////////////////////////////////////////
                            
                            else if ("getAndAddInt".equals(methodName) && "(Ljava/lang/Object;JI)I".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getAndAddInt", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JI)I");   
                            } else if ("getAndAddLong".equals(methodName) && "(Ljava/lang/Object;JJ)J".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getAndAddLong", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JJ)J");   
                            } else if ("getAndSetInt".equals(methodName) && "(Ljava/lang/Object;JI)I".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getAndSetInt", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JI)I");   
                            } else if ("getAndSetLong".equals(methodName) && "(Ljava/lang/Object;JJ)J".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getAndSetLong", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JJ)J");   
                            } else if ("getAndSetObject".equals(methodName) && "(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;".equals(signature)) {
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC, 
                                        "tr/com/serkanozal/mysafe/impl/MySafeDelegator",
                                        "getAndSetObject", 
                                        "(Lsun/misc/Unsafe;Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;");   
                            }
                            
                            /////////////////////////////////////////////////////////////////////////////
    
                            else {
                                super.visitMethodInsn(opcode, ownerClassName, methodName, signature);
                            } 
                        }        
                    } else {
                        super.visitMethodInsn(opcode, ownerClassName, methodName, signature);
                    }     
                }
            };
        }

    }
    
}
