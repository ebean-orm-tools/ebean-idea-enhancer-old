/*
 * Copyright 2009 Mario Ivankovits
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

import static com.avaje.ebean.enhance.agent.InputStreamTransform.readBytes;

import com.avaje.ebean.enhance.agent.InputStreamTransform;
import com.avaje.ebean.enhance.agent.MessageOutput;
import com.avaje.ebean.enhance.agent.Transformer;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ActionRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This task actually hand all successfully compiled classes over to the Ebean weaver.
 *
 * @author Mario Ivankovits, mario@ops.co.at
 * @author yevgenyk - Updated 28/04/2014 for IDEA 13
 */
public class EbeanEnhancementTask {
    private static final int DEBUG = 1;

    private final CompileContext compileContext;
    private final Map<String, File> compiledClasses;

    public EbeanEnhancementTask(CompileContext compileContext, Map<String, File> compiledClasses) {
        this.compileContext = compileContext;
        this.compiledClasses = compiledClasses;
    }

    public void process() {
        try {
            ActionRunner.runInsideWriteAction(
                new ActionRunner.InterruptibleRunnable() {
                    @Override
                    public void run() throws Exception {
                        doProcess();
                    }
                }
            );
        } catch (Exception e) {
            compileContext.addMessage(CompilerMessageCategory.ERROR, e.getClass().getName() + ":" + e.getMessage(), null, -1, -1);
        }
    }

    private class DeliverenceBanjoClassLoader extends URLClassLoader {


        public DeliverenceBanjoClassLoader(final ClassLoader parent) {
            super(new URL[0], parent);
        }

        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {

            Class<?> clazz;
            File f = compiledClasses.get(name);
            if (f != null) {
                try (FileInputStream fis = new FileInputStream(f)) {
                    byte[] x = readBytes(fis);
                    clazz = defineClass(name, x, 0, x.length);
                } catch (IOException e) {
                    compileContext.addMessage(CompilerMessageCategory.ERROR, "Couldn't read file " + f, null, -1, -1);
                    clazz = super.loadClass(name);
                }
            } else {
                clazz = super.loadClass(name);
            }
            return clazz;
        }
    }

    private void doProcess() throws IOException, IllegalClassFormatException {
        compileContext.addMessage(CompilerMessageCategory.INFORMATION, "Ebean enhancement started ...", null, -1, -1);

        final IdeaClassBytesReader classBytesReader = new IdeaClassBytesReader(compileContext, compiledClasses);
        final Transformer transformer = new Transformer(classBytesReader, "detect=true;debug=" + DEBUG, null, null);

        transformer.setLogout(new MessageOutput() {
            @Override
            public void println(String message) {
                compileContext.addMessage(CompilerMessageCategory.INFORMATION, message, null, -1, -1);
            }
        });

        final ProgressIndicator progressIndicator = compileContext.getProgressIndicator();
        progressIndicator.setIndeterminate(true);
        progressIndicator.setText("Ebean enhancement");

        final InputStreamTransform isTransform = new InputStreamTransform(transformer, new DeliverenceBanjoClassLoader(getClass().getClassLoader()));

        for (Entry<String, File> entry : compiledClasses.entrySet()) {
            final String className = entry.getKey();
            final File file = entry.getValue();

            progressIndicator.setText2(className);

            final byte[] transformed = isTransform.transform(className, file);
            if (transformed != null) {
                final VirtualFile outputFile = VfsUtil.findFileByIoFile(file, true);
                outputFile.setBinaryContent(transformed);
            }
        }

        compileContext.addMessage(CompilerMessageCategory.INFORMATION, "Ebean enhancement done!", null, -1, -1);
    }
}
