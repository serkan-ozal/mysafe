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
package tr.com.serkanozal.mysafe.impl.classloader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Manifest;

import sun.misc.Resource;
import tr.com.serkanozal.mysafe.impl.instrument.UnsafeUsageInstrumenter;
import tr.com.serkanozal.mysafe.impl.util.ClasspathUtil;

public class MySafeClassLoader extends URLClassLoader {

    private final UnsafeUsageInstrumenter unsafeUsageInstrumenter;
    private final AtomicBoolean initialized;

    public MySafeClassLoader(ClassLoader parent) {
        super(findClasspathUrls(parent), null);
        this.unsafeUsageInstrumenter = createUnsafeUsageInstrumenter(parent);
        try {
            Class<?> atomicBooleanClass = parent.loadClass("java.util.concurrent.atomic.AtomicBoolean");
            initialized = (AtomicBoolean) atomicBooleanClass.newInstance();
            
            URL[] classpathUrls = findClasspathUrls(parent);
            Field urlClasspathField = URLClassLoader.class.getDeclaredField("ucp");
            urlClasspathField.setAccessible(true);
            urlClasspathField.set(this, new UnsafeAwareUrlClasspath(classpathUrls, unsafeUsageInstrumenter));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
    
    private void ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            try {
                Class<?> mySafeClass = loadClass("tr.com.serkanozal.mysafe.MySafe");
                Method initMethod = mySafeClass.getMethod("initialize");
                initMethod.invoke(mySafeClass);
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            } 
        }    
    }
    
    private UnsafeUsageInstrumenter createUnsafeUsageInstrumenter(ClassLoader parent) {
        try {
            Class<?> instrumenterFactoryClass = 
                    parent.loadClass("tr.com.serkanozal.mysafe.impl.instrument.UnsafeUsageInstrumenterFactory");
            Method factoryMethod = instrumenterFactoryClass.getMethod("createUnsafeUsageInstrumenter");
            return (UnsafeUsageInstrumenter) factoryMethod.invoke(instrumenterFactoryClass);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }   
    }

    private static URL[] findClasspathUrls(ClassLoader classLoader) {
        Set<URL> urls = ClasspathUtil.findClasspathUrls(classLoader);
        return urls.toArray(new URL[0]);
    }
    
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (!"tr/com/serkanozal/mysafe/MySafe".equals(name)) {
            ensureInitialized();
        }
        
        return super.loadClass(name);
    }

    private static class UnsafeAwareUrlClasspath extends sun.misc.URLClassPath {

        private final UnsafeUsageInstrumenter unsafeUsageInstrumenter;
        
        private UnsafeAwareUrlClasspath(URL[] urls, UnsafeUsageInstrumenter unsafeUsageInstrumenter) {
            super(urls);
            this.unsafeUsageInstrumenter = unsafeUsageInstrumenter;
        }

        @Override
        public Resource getResource(String name) {
            Resource resource = super.getResource(name);
            if (resource == null) {
                return null;
            }
            if (name.endsWith(".class")) {
                return new UnsafeAwareClassResource(name, resource, unsafeUsageInstrumenter);
            } else {
                return resource;
            }
        }

        @Override
        public Resource getResource(String name, boolean check) {
            Resource resource = super.getResource(name, check);
            if (resource == null) {
                return null;
            }
            if (name.endsWith(".class")) {
                return new UnsafeAwareClassResource(name, resource, unsafeUsageInstrumenter);
            } else {
                return resource;
            }    
        }

    }

    private static class UnsafeAwareClassResource extends sun.misc.Resource {

        private final String classResourceName;
        private final String className;
        private final sun.misc.Resource delegatedResource;
        private final UnsafeUsageInstrumenter unsafeUsageInstrumenter;

        private UnsafeAwareClassResource(String classResourceName, sun.misc.Resource delegatedResource,
                                         UnsafeUsageInstrumenter unsafeUsageInstrumenter) {
            this.classResourceName = classResourceName;
            this.className = classResourceName.substring(0, classResourceName.indexOf(".")).replace("/", ".");
            this.delegatedResource = delegatedResource;
            this.unsafeUsageInstrumenter = unsafeUsageInstrumenter;
        }

        @Override
        public String getName() {
            return classResourceName;
        }

        @Override
        public URL getURL() {
            return  delegatedResource.getURL();
        }

        @Override
        public URL getCodeSourceURL() {
            return delegatedResource.getCodeSourceURL();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return delegatedResource.getInputStream();
        }

        @Override
        public int getContentLength() throws IOException {
            return delegatedResource.getContentLength();
        }
        
        @Override
        public Certificate[] getCertificates() {
            return delegatedResource.getCertificates();
        }
        
        @Override
        public CodeSigner[] getCodeSigners() {
            return delegatedResource.getCodeSigners();
        }
        
        @Override
        public Manifest getManifest() throws IOException {
            return delegatedResource.getManifest();
        }

        @Override
        public ByteBuffer getByteBuffer() throws IOException {
            ByteBuffer bb = delegatedResource.getByteBuffer();
            if (bb == null) {
                return null;
            }
            byte[] b = unsafeUsageInstrumenter.instrument(className, bb.array());
            return ByteBuffer.wrap(b);
        }

        @Override
        public byte[] getBytes() throws IOException {
            return unsafeUsageInstrumenter.instrument(className, delegatedResource.getBytes());
        }

    }

}
