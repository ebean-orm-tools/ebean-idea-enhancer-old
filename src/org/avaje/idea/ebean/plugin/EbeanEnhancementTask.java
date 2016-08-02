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

import com.avaje.ebean.enhance.agent.InputStreamTransform;
import com.avaje.ebean.enhance.agent.MessageOutput;
import com.avaje.ebean.enhance.agent.Transformer;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ActionRunner;
import org.avaje.ebean.typequery.agent.CombinedTransform;
import org.avaje.ebean.typequery.agent.QueryBeanTransformer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.avaje.ebean.enhance.agent.InputStreamTransform.readBytes;

/**
 * This task actually hand all successfully compiled classes over to the Ebean weaver.
 *
 * @author Mario Ivankovits, mario@ops.co.at
 * @author yevgenyk - Updated 28/04/2014 for IDEA 13
 */
class EbeanEnhancementTask {

  private static final int DEBUG = 0;

  private final CompileContext compileContext;

  private final Map<String, File> compiledClasses;

  EbeanEnhancementTask(CompileContext compileContext, Map<String, File> compiledClasses) {
    this.compileContext = compileContext;
    this.compiledClasses = compiledClasses;
  }

  void process() {
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

  /**
   * ClassLoader aware of the files being compiled by IDEA.
   */
  private class CompiledFilesAwareClassLoader extends URLClassLoader {

    CompiledFilesAwareClassLoader(ClassLoader parent) {
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

    Set<String> packages = new ManifestReader(compileContext).findManifests();

    compileContext.addMessage(CompilerMessageCategory.INFORMATION, "Ebean 8.x enhancement started ... packages:" + packages, null, -1, -1);

    IdeaClassBytesReader classBytesReader = new IdeaClassBytesReader(compileContext, compiledClasses);
    IdeaClassLoader baseClassLoader = new IdeaClassLoader(Thread.currentThread().getContextClassLoader(), classBytesReader);
    final CompiledFilesAwareClassLoader classLoader = new CompiledFilesAwareClassLoader(baseClassLoader);

    final Transformer transformer = new Transformer(classBytesReader, "debug=" + DEBUG, null);
    final QueryBeanTransformer queryBeanTransformer = new QueryBeanTransformer("debug=" + DEBUG, baseClassLoader, packages);

    final CombinedTransform combinedTransform = new CombinedTransform(transformer, queryBeanTransformer);

    transformer.setLogout(new MessageOutput() {
      @Override
      public void println(String message) {
        compileContext.addMessage(CompilerMessageCategory.INFORMATION, message, null, -1, -1);
      }
    });

    ProgressIndicator progressIndicator = compileContext.getProgressIndicator();
    progressIndicator.setIndeterminate(true);
    progressIndicator.setText("Ebean enhancement");

    for (Entry<String, File> entry : compiledClasses.entrySet()) {
      String className = entry.getKey();
      File file = entry.getValue();

      progressIndicator.setText2(className);

      try {
        byte[] origBytes = readFileBytes(file);

        CombinedTransform.Response response = combinedTransform.transform(classLoader, className, null, null, origBytes);
        if (response.isEnhanced()) {
          writeTransformed(file, response.getClassBytes());
          String msg = "enhanced: " + className + " type:" + (response.isFirst() ? " e" : "") + (response.isSecond() ? " q" : "");
          compileContext.addMessage(CompilerMessageCategory.INFORMATION, msg, null, -1, -1);
        }

      } catch (IOException e) {
        compileContext.addMessage(CompilerMessageCategory.ERROR, "IOException trying to enhance:" + className + " error:" + e.getMessage(), null, -1, -1);
      }
    }

    compileContext.addMessage(CompilerMessageCategory.INFORMATION, "Ebean enhancement done!", null, -1, -1);
  }

  /**
   * Write the transformed class bytes to the appropriate target classes file.
   */
  private void writeTransformed(File file, byte[] finalTransformed) throws IOException {
    VirtualFile outputFile = VfsUtil.findFileByIoFile(file, true);
    if (outputFile == null) {
      compileContext.addMessage(CompilerMessageCategory.ERROR, "OutputFile not found for: " + file, null, -1, -1);
    } else {
      outputFile.setBinaryContent(finalTransformed);
    }
  }

  private byte[] readFileBytes(File file) throws IOException {
    FileInputStream fis = new FileInputStream(file);
    try {
      return InputStreamTransform.readBytes(fis);
    } finally {
      try {
        fis.close();
      } catch (IOException e) {
        compileContext.addMessage(CompilerMessageCategory.ERROR, "Error closing FileInputStream:" + e.getMessage(), null, -1, -1);
      }
    }
  }
}
