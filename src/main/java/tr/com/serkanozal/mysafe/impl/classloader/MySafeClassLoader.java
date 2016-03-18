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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.CodeSigner;
import java.security.cert.Certificate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Manifest;

import sun.misc.Resource;
import sun.net.www.ParseUtil;
import tr.com.serkanozal.mysafe.impl.instrument.MySafeInstrumenter;
import tr.com.serkanozal.mysafe.impl.util.ClasspathUtil;

public class MySafeClassLoader extends URLClassLoader {

    private final MySafeInstrumenter mysafeInstrumenter;
    private final AtomicBoolean initialized;

    public MySafeClassLoader(ClassLoader parent) {
        super(findClasspathUrls(parent), null);
        this.mysafeInstrumenter = createMySafeInstrumenter(parent);
        try {
            Class<?> atomicBooleanClass = parent.loadClass("java.util.concurrent.atomic.AtomicBoolean");
            initialized = (AtomicBoolean) atomicBooleanClass.newInstance();
            
            URL[] classpathUrls = findClasspathUrls(parent);
            Field urlClasspathField = URLClassLoader.class.getDeclaredField("ucp");
            urlClasspathField.setAccessible(true);
            urlClasspathField.set(this, new MySafeAwareUrlClasspath(classpathUrls, mysafeInstrumenter));
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
    
    private MySafeInstrumenter createMySafeInstrumenter(ClassLoader parent) {
        try {
            Class<?> instrumenterFactoryClass = 
                    parent.loadClass("tr.com.serkanozal.mysafe.impl.instrument.MySafeInstrumenterFactory");
            Method factoryMethod = instrumenterFactoryClass.getMethod("createMySafeInstrumenter");
            return (MySafeInstrumenter) factoryMethod.invoke(instrumenterFactoryClass);
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
    
    private static URL getFileURL(File file) {
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {}

        try {
            return ParseUtil.fileToEncodedURL(file);
        } catch (MalformedURLException e) {
            // Should never happen since we specify the protocol...
            throw new InternalError();
        }
    }
    
    /**
     * This class loader supports dynamic additions to the class path
     * at runtime.
     *
     * @see java.lang.instrument.Instrumentation#appendToSystemClassPathSearch
     */
    @SuppressWarnings("unused")
    private void appendToClassPathForInstrumentation(String path) {
        assert(Thread.holdsLock(this));

        // addURL is a no-op if path already contains the URL
        super.addURL( getFileURL(new File(path)) );
    }

    private static class MySafeAwareUrlClasspath extends sun.misc.URLClassPath {

        private final MySafeInstrumenter mySafeInstrumenter;
        
        private MySafeAwareUrlClasspath(URL[] urls, MySafeInstrumenter mySafeInstrumenter) {
            super(urls);
            this.mySafeInstrumenter = mySafeInstrumenter;
        }

        @Override
        public Resource getResource(String name) {
            Resource resource = super.getResource(name);
            if (resource == null) {
                return null;
            }
            if (name.endsWith(".class")) {
                return new MySafeAwareClassResource(name, resource, mySafeInstrumenter);
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
                return new MySafeAwareClassResource(name, resource, mySafeInstrumenter);
            } else {
                return resource;
            }    
        }

    }

    private static class MySafeAwareClassResource extends sun.misc.Resource {

        private final String classResourceName;
        private final String className;
        private final sun.misc.Resource delegatedResource;
        private final MySafeInstrumenter mySafeInstrumenter;

        private MySafeAwareClassResource(String classResourceName, sun.misc.Resource delegatedResource,
                                         MySafeInstrumenter mySafeInstrumenter) {
            this.classResourceName = classResourceName;
            this.className = classResourceName.substring(0, classResourceName.indexOf(".")).replace("/", ".");
            this.delegatedResource = delegatedResource;
            this.mySafeInstrumenter = mySafeInstrumenter;
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
            byte[] b = mySafeInstrumenter.instrument(className, bb.array());
            return ByteBuffer.wrap(b);
        }

        @Override
        public byte[] getBytes() throws IOException {
            return mySafeInstrumenter.instrument(className, delegatedResource.getBytes());
        }

    }

}
