/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.classloader;

import org.gradle.internal.util.Trie;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A ClassLoader which hides all non-system classes, packages and resources. Allows certain non-system packages and classes to be declared as visible. By default, only the Java system classes,
 * packages and resources are visible.
 */
public class FilteringClassLoader extends ClassLoader implements ClassLoaderHierarchy {
    private static final ClassLoader EXT_CLASS_LOADER;
    private static final Set<String> SYSTEM_PACKAGES = new HashSet<String>();
    private static final Set<String> ALLOWED_EXT_PACKAGES = new HashSet<String>(Arrays.asList(
        "com.sun.beans",
        "com.sun.source.util",
        "com.sun.tools.javac.api",
        "com.sun.tools.javac.code",
        "java.beans",
        "java.io",
        "java.lang",
        "java.lang.annotation",
        "java.lang.invoke",
        "java.lang.ref",
        "java.lang.reflect",
        "java.math",
        "java.net",
        "java.nio",
        "java.nio.channels",
        "java.nio.channels.spi",
        "java.nio.charset",
        "java.nio.charset.spi",
        "java.nio.file",
        "java.nio.file.attribute",
        "java.security",
        "java.security.cert",
        "java.text",
        "java.text.spi",
        "java.util",
        "java.util.concurrent",
        "java.util.concurrent.atomic",
        "java.util.concurrent.locks",
        "java.util.function",
        "java.util.jar",
        "java.util.logging",
        "java.util.regex",
        "java.util.spi",
        "java.util.zip",
        "javax.annotation.processing",
        "javax.lang.model",
        "javax.lang.model.element",
        "javax.lang.model.type",
        "javax.lang.model.util",
        "javax.tools",
        "jdk.internal.org.objectweb.asm",
        "sun.invoke.empty",
        "sun.invoke.util",
        "sun.launcher",
        "sun.misc",
        "sun.net",
        "sun.net.sdp",
        "sun.net.spi.nameservice",
        "sun.net.util",
        "sun.net.www",
        "sun.net.www.protocol.file",
        "sun.net.www.protocol.jar",
        "sun.nio",
        "sun.nio.ch",
        "sun.nio.cs",
        "sun.reflect",
        "sun.reflect.annotation",
        "sun.reflect.generics.factory",
        "sun.reflect.generics.parser",
        "sun.reflect.generics.reflectiveObjects",
        "sun.reflect.generics.repository",
        "sun.reflect.generics.scope",
        "sun.reflect.generics.tree",
        "sun.reflect.generics.visitor",
        "sun.reflect.misc",
        "sun.security.action",
        "sun.security.jca",
        "sun.security.provider",
        "sun.security.util",
        "sun.text.resources",
        "sun.text.resources.en",
        "sun.usagetracker",
        "sun.util", "sun.util.calendar",
        "sun.util.locale",
        "sun.util.locale.provider",
        "sun.util.logging",
        "sun.util.resources",
        "sun.util.resources.en",
        "sun.util.spi"
    ));

    public static final String DEFAULT_PACKAGE = "DEFAULT";
    private final Set<String> packageNames;
    private final TrieSet packagePrefixes;
    private final TrieSet resourcePrefixes;
    private final Set<String> resourceNames;
    private final Set<String> classNames;
    private final Set<String> disallowedClassNames;
    private final TrieSet disallowedPackagePrefixes;

    static {
        EXT_CLASS_LOADER = ClassLoaderUtils.getPlatformClassLoader();
        for (Package p : new RetrieveSystemPackagesClassLoader(EXT_CLASS_LOADER).getPackages()) {
            SYSTEM_PACKAGES.add(p.getName());
        }
        try {
            ClassLoader.registerAsParallelCapable();
        } catch (NoSuchMethodError ignore) {
            // Not supported on Java 6
        }
    }

    private static class RetrieveSystemPackagesClassLoader extends ClassLoader {
        RetrieveSystemPackagesClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Package[] getPackages() {
            return super.getPackages();
        }
    }

    public FilteringClassLoader(ClassLoader parent, Spec spec) {
        super(parent);
        packageNames = new HashSet<String>(spec.packageNames);
        packagePrefixes = new TrieSet(spec.packagePrefixes);
        resourceNames = new HashSet<String>(spec.resourceNames);
        resourcePrefixes = new TrieSet(spec.resourcePrefixes);
        classNames = new HashSet<String>(spec.classNames);
        disallowedClassNames = new HashSet<String>(spec.disallowedClassNames);
        disallowedPackagePrefixes = new TrieSet(spec.disallowedPackagePrefixes);
    }

    @Override
    public void visit(ClassLoaderVisitor visitor) {
        visitor.visitSpec(new Spec(classNames, packageNames, packagePrefixes, resourcePrefixes, resourceNames, disallowedClassNames, disallowedPackagePrefixes));
        visitor.visitParent(getParent());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            int endIndex = name.lastIndexOf(".");
            String pkg = endIndex>0 ? name.substring(0, endIndex) : "";
            if (ALLOWED_EXT_PACKAGES.contains(pkg)) {
                // don't try to load from ext classloader if the package is not known to us,
                // because it's costly (fills in a stack trace) and almost certainly a mistake
                return EXT_CLASS_LOADER.loadClass(name);
            }
        } catch (ClassNotFoundException ignore) {
            // ignore
        }

        if (!classAllowed(name)) {
            throw new FilteredClassException(name + " not found.");
        }

        Class<?> cl = super.loadClass(name, false);
        if (resolve) {
            resolveClass(cl);
        }

        return cl;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected Package getPackage(String name) {
        Package p = super.getPackage(name);
        if (p == null || !allowed(p)) {
            return null;
        }
        return p;
    }

    @Override
    protected Package[] getPackages() {
        List<Package> packages = new ArrayList<Package>();
        for (Package p : super.getPackages()) {
            if (allowed(p)) {
                packages.add(p);
            }
        }
        return packages.toArray(new Package[0]);
    }

    @Override
    public URL getResource(String name) {
        if (allowed(name)) {
            return super.getResource(name);
        }
        return EXT_CLASS_LOADER.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (allowed(name)) {
            return super.getResources(name);
        }
        return EXT_CLASS_LOADER.getResources(name);
    }

    @Override
    public String toString() {
        return FilteringClassLoader.class.getSimpleName() + "(" + getParent() + ")";
    }

    private boolean allowed(String resourceName) {
        return resourceNames.contains(resourceName)
            || resourcePrefixes.find(resourceName);
    }

    private boolean allowed(Package pkg) {
        String packageName = pkg.getName();
        if (disallowedPackagePrefixes.find(packageName)) {
            return false;
        }

        return SYSTEM_PACKAGES.contains(packageName)
            || packageNames.contains(packageName)
            || packagePrefixes.find(packageName);
    }

    private boolean classAllowed(String className) {
        if (disallowedClassNames.contains(className)) {
            return false;
        }

        if (classNames.contains(className)) {
            return true;
        }

        if (disallowedPackagePrefixes.find(className)) {
            return false;
        }

        return packagePrefixes.find(className)
            || (packagePrefixes.contains(DEFAULT_PACKAGE + ".") && isInDefaultPackage(className));
    }

    private boolean isInDefaultPackage(String className) {
        return !className.contains(".");
    }

    public static class Spec extends ClassLoaderSpec {

        final Set<String> packageNames = new HashSet<String>();
        final Set<String> packagePrefixes = new HashSet<String>();
        final Set<String> resourcePrefixes = new HashSet<String>();
        final Set<String> resourceNames = new HashSet<String>();
        final Set<String> classNames = new HashSet<String>();
        final Set<String> disallowedClassNames = new HashSet<String>();
        final Set<String> disallowedPackagePrefixes = new HashSet<String>();

        public Spec() {
        }

        public Spec(Spec spec) {
            this(
                spec.classNames,
                spec.packageNames,
                spec.packagePrefixes,
                spec.resourcePrefixes,
                spec.resourceNames,
                spec.disallowedClassNames,
                spec.disallowedPackagePrefixes
            );
        }

        public Spec(Iterable<String> classNames, Iterable<String> packageNames, Iterable<String> packagePrefixes, Iterable<String> resourcePrefixes, Iterable<String> resourceNames, Iterable<String> disallowedClassNames, Iterable<String> disallowedPackagePrefixes) {
            addAll(this.classNames, classNames);
            addAll(this.packageNames, packageNames);
            addAll(this.packagePrefixes, packagePrefixes);
            addAll(this.resourcePrefixes, resourcePrefixes);
            addAll(this.resourceNames, resourceNames);
            addAll(this.disallowedClassNames, disallowedClassNames);
            addAll(this.disallowedPackagePrefixes, disallowedPackagePrefixes);
        }

        private static void addAll(Collection<String> collection, Iterable<String> elements) {
            for (String element : elements) {
                collection.add(element);
            }
        }

        /**
         * Whether or not any constraints have been added to this filter.
         *
         * @return true if no constraints have been added
         */
        public boolean isEmpty() {
            return classNames.isEmpty()
                && packageNames.isEmpty()
                && packagePrefixes.isEmpty()
                && resourcePrefixes.isEmpty()
                && resourceNames.isEmpty()
                && disallowedClassNames.isEmpty()
                && disallowedPackagePrefixes.isEmpty();
        }

        /**
         * Marks a package and all its sub-packages as visible. Also makes resources in those packages visible.
         *
         * @param packageName the package name
         */
        public void allowPackage(String packageName) {
            packageNames.add(packageName);
            packagePrefixes.add(packageName + ".");
            resourcePrefixes.add(packageName.replace('.', '/') + '/');
        }

        /**
         * Marks a single class as visible.
         *
         * @param clazz the class
         */
        public void allowClass(Class<?> clazz) {
            classNames.add(clazz.getName());
        }

        /**
         * Marks a single class as not visible.
         *
         * @param className the class name
         */
        public void disallowClass(String className) {
            disallowedClassNames.add(className);
        }

        /**
         * Marks a package and all its sub-packages as not visible. Does not affect resources in those packages.
         *
         * @param packagePrefix the package prefix
         */
        public void disallowPackage(String packagePrefix) {
            disallowedPackagePrefixes.add(packagePrefix + ".");
        }

        /**
         * Marks all resources with the given prefix as visible.
         *
         * @param resourcePrefix the resource prefix
         */
        public void allowResources(String resourcePrefix) {
            resourcePrefixes.add(resourcePrefix + "/");
        }

        /**
         * Marks a single resource as visible.
         *
         * @param resourceName the resource name
         */
        public void allowResource(String resourceName) {
            resourceNames.add(resourceName);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            Spec other = (Spec) obj;
            return other.packageNames.equals(packageNames)
                && other.packagePrefixes.equals(packagePrefixes)
                && other.resourceNames.equals(resourceNames)
                && other.resourcePrefixes.equals(resourcePrefixes)
                && other.classNames.equals(classNames)
                && other.disallowedClassNames.equals(disallowedClassNames)
                && other.disallowedPackagePrefixes.equals(disallowedPackagePrefixes);
        }

        @Override
        public int hashCode() {
            return packageNames.hashCode()
                ^ packagePrefixes.hashCode()
                ^ resourceNames.hashCode()
                ^ resourcePrefixes.hashCode()
                ^ classNames.hashCode()
                ^ disallowedClassNames.hashCode()
                ^ disallowedPackagePrefixes.hashCode();
        }

        public Set<String> getPackageNames() {
            return packageNames;
        }

        public Set<String> getPackagePrefixes() {
            return packagePrefixes;
        }

        public Set<String> getResourcePrefixes() {
            return resourcePrefixes;
        }

        public Set<String> getResourceNames() {
            return resourceNames;
        }

        public Set<String> getClassNames() {
            return classNames;
        }

        public Set<String> getDisallowedClassNames() {
            return disallowedClassNames;
        }

        public Set<String> getDisallowedPackagePrefixes() {
            return disallowedPackagePrefixes;
        }
    }

    private static class TrieSet implements Iterable<String> {
        private final Trie trie;
        private final Set<String> set;

        public TrieSet(Collection<String> words) {
            this.trie = Trie.from(words);
            this.set = new HashSet<String>(words);
        }

        public boolean find(CharSequence seq) {
            return trie.find(seq);
        }

        public boolean contains(String seq) {
            return set.contains(seq);
        }

        @Nonnull
        @Override
        public Iterator<String> iterator() {
            return set.iterator();
        }
    }

    private static class FilteredClassException extends ClassNotFoundException {
        public FilteredClassException(String s) {
            super(s);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
