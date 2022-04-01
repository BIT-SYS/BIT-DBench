/*
 * Copyright 2021 Sejin Im
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

package io.github.imsejin.common.tool;

import io.github.imsejin.common.annotation.ExcludeFromGeneratedJacocoReport;
import io.github.imsejin.common.assertion.Asserts;
import io.github.imsejin.common.util.ClassUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.util.stream.Collectors.toSet;

/**
 * Class finder
 *
 * @see <a href="https://stackoverflow.com/questions/3222638/get-all-of-the-classes-in-the-classpath#answer-19554704">
 * Get all of the Classes in the Classpath
 * </a>
 */
public final class ClassFinder {

    @ExcludeFromGeneratedJacocoReport
    private ClassFinder() {
        throw new UnsupportedOperationException(getClass().getName() + " is not allowed to instantiate");
    }

    /**
     * Returns all classes that extends the given class.
     *
     * @param superclass superclass
     * @return all subclasses
     */
    public static Set<Class<?>> getAllSubclasses(Class<?> superclass) {
        return getAllSubclasses(superclass, SearchPolicy.ALL, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Returns all classes that extends the given class.
     *
     * @param superclass   superclass
     * @param searchPolicy policy of search
     * @return all subclasses
     */
    public static Set<Class<?>> getAllSubclasses(Class<?> superclass, SearchPolicy searchPolicy) {
        return getAllSubclasses(superclass, searchPolicy, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Returns all classes that extends the given class.
     *
     * @param superclass   superclass
     * @param searchPolicy policy of search
     * @param classLoader  class loader
     * @return all subclasses
     */
    public static Set<Class<?>> getAllSubclasses(Class<?> superclass, SearchPolicy searchPolicy, ClassLoader classLoader) {
        List<Class<?>> subclasses = new ArrayList<>();
        ClassFinder.findClasses(name -> {
            try {
                return subclasses.add(Class.forName(name, false, classLoader));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        return subclasses.stream().filter(clazz -> searchPolicy.search(superclass, clazz)).collect(toSet());
    }

    /**
     * Visits all classes in classpath.
     *
     * @param visitor visitor
     */
    public static void findClasses(Visitor<String> visitor) {
        String javaHome = System.getProperty("java.home");

        File file = new File(javaHome, "lib");
        if (file.exists()) {
            findClasses0(file, file, true, visitor);
        }

        String classpath = System.getProperty("java.class.path");
        String[] paths = classpath.split(File.pathSeparator); // System.getProperty("path.separator")
        for (String path : paths) {
            file = new File(path);
            if (!file.exists()) continue;

            findClasses0(file, file, false, visitor);
        }
    }

    private static boolean findClasses0(File root, File file, boolean includeJars, Visitor<String> visitor) {
        if (file.isDirectory()) {
            for (File child : Objects.requireNonNull(file.listFiles())) {
                if (!findClasses0(root, child, includeJars, visitor)) {
                    return false;
                }
            }
        }

        String filename = file.getName().toLowerCase();
        if (includeJars && filename.endsWith(".jar")) {
            JarFile jar;
            try {
                jar = new JarFile(file);
            } catch (IOException ignored) {
                return true;
            }

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                int extIndex = name.lastIndexOf(".class");
                if (extIndex > 0) {
                    if (!visitor.visit(name.substring(0, extIndex).replace('/', '.'))) {
                        return false;
                    }
                }
            }
        } else if (filename.endsWith(".class")) {
            return visitor.visit(createClassName(root, file));
        }

        return true;
    }

    private static String createClassName(File root, File file) {
        StringBuilder sb = new StringBuilder();
        String fileName = file.getName();
        sb.append(fileName, 0, fileName.lastIndexOf(".class"));
        file = file.getParentFile();
        while (file != null && !file.equals(root)) {
            sb.insert(0, '.').insert(0, file.getName());
            file = file.getParentFile();
        }

        return sb.toString();
    }

    @FunctionalInterface
    public interface Visitor<T> {
        /**
         * @return {@code true} if the algorithm should visit more results,
         * {@code false} if it should terminate now.
         */
        boolean visit(T t);
    }

    public enum SearchPolicy {
        /**
         * Search all subclasses, but not implementations of interface.
         */
        CLASS {
            @Override
            public boolean search(@Nonnull Class<?> superclass, Class<?> subclass) {
                return ClassUtils.isSuperclass(superclass, subclass);
            }
        },

        /**
         * Search all subclasses and implementations.
         */
        ALL {
            @Override
            public boolean search(@Nonnull Class<?> superclass, Class<?> subclass) {
                Asserts.that(superclass).isNotNull();
                if (subclass == null || superclass == subclass) return false;

                return superclass.isAssignableFrom(subclass);
            }
        };

        public abstract boolean search(@Nonnull Class<?> superclass, Class<?> subclass);
    }

}
