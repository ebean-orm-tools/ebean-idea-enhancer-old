/*
 * Copyright 2015 Yevgeny Krasik
 *
 *     This file is part of Ebean-idea-plugin.
 *
 *     Ebean-idea-plugin is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Ebean-idea-plugin is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Ebean-idea-plugin.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.avaje.idea.ebean.plugin;

import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yevgenyk - Updated 28/04/2014 for IDEA 13
 */
public class CompiledFileCollector implements CompilationStatusListener {
    private Map<String, File> compiledClasses = new HashMap<>();

    @Override
    public void fileGenerated(String outputRoot, String relativePath) {
        // Collect all valid compiled '.class' files
        final CompiledFile compiledFile = createCompiledFile(outputRoot, relativePath);
        if (compiledFile != null) {
            this.compiledClasses.put(compiledFile.className, compiledFile.file);
        }
    }

    private CompiledFile createCompiledFile(String outputRoot, String relativePath) {
        if (outputRoot == null || relativePath == null || !relativePath.endsWith(".class")) {
            return null;
        }

        final File file = new File(outputRoot, relativePath);
        if (!file.exists() || !isJavaClass(file)) {
            return null;
        }

        final String className = resolveClassName(relativePath);

        return new CompiledFile(file, className);
    }

    /**
     * Given a content path and a class file path, resolve the fully qualified class name
     */
    private String resolveClassName(String relativePath) {
        final int extensionPos = relativePath.lastIndexOf('.');
        return relativePath.substring(0, extensionPos).replace('/', '.');
    }

    /**
     * Check if the file is a java class by peeking the first two magic bytes and see if we need a 0xCAFE ;-)
     */
    private boolean isJavaClass(File file) {
        try (InputStream is = new FileInputStream(file)) {
            final byte[] buf = new byte[2];
            final int read = is.read(buf, 0, 2);
            if (read < buf.length) {
                return false;
            }
            return buf[0] == (byte) 0xCA &&
                   buf[1] == (byte) 0xFE;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void compilationFinished(boolean aborted,
                                    int errors,
                                    int warnings,
                                    CompileContext compileContext) {
        new EbeanEnhancementTask(compileContext, compiledClasses).process();
        this.compiledClasses = new HashMap<>();
    }

    public static class CompiledFile {
        private final File file;
        private final String className;

        private CompiledFile(File file, String className) {
            this.file = file;
            this.className = className;
        }

        public File getFile() {
            return file;
        }

        public String getClassName() {
            return className;
        }

        @Override
        public String toString() {
            return "CompiledFile{" +
                "file=" + file +
                ", className='" + className + '\'' +
                '}';
        }
    }
}
