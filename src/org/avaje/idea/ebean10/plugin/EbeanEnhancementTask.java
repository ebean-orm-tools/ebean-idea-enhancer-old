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

package org.avaje.idea.ebean10.plugin;

import io.ebean.enhance.agent.InputStreamTransform;
import io.ebean.enhance.agent.Transformer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ActionRunner;
import io.ebean.typequery.agent.CombinedTransform;
import io.ebean.typequery.agent.QueryBeanTransformer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.instrument.IllegalClassFormatException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * This task actually hand all successfully compiled classes over to the Ebean weaver.
 *
 * @author Mario Ivankovits, mario@ops.co.at
 * @author yevgenyk - Updated 28/04/2014 for IDEA 13
 */
class EbeanEnhancementTask {

  private int debugLevel;

  private final CompileContext compileContext;

  private final Map<String, File> compiledClasses;

  EbeanEnhancementTask(CompileContext compileContext, Map<String, File> compiledClasses) {
    this.compileContext = compileContext;
    this.compiledClasses = compiledClasses;
    String envDebug = System.getProperty("ebean_idea_debug");
    if (envDebug != null) {
      try {
        debugLevel = Integer.parseInt(envDebug);
      } catch (NumberFormatException e) {
        debugLevel = 2;
      }
    }
  }

  void process() {
    try {
      ActionRunner.runInsideWriteAction(this::doProcess);
    } catch (Exception e) {
      logError(e.getClass().getName() + ":" + e.getMessage());
    }
  }

  private void doProcess() throws IOException, IllegalClassFormatException {

    Set<String> packages = new ManifestReader(compileContext).findManifests();

    logInfo("Ebean 10.x enhancement started ... packages:" + packages+" debug:"+debugLevel);

    IdeaClassBytesReader classBytesReader = new IdeaClassBytesReader(compileContext, compiledClasses);
    IdeaClassLoader classLoader = new IdeaClassLoader(getClass().getClassLoader(), classBytesReader);

    Transformer transformer = new Transformer(classBytesReader, "debug=" + debugLevel, null);
    QueryBeanTransformer queryBeanTransformer = new QueryBeanTransformer("debug=" + debugLevel, classLoader, packages);

    CombinedTransform combinedTransform = new CombinedTransform(transformer, queryBeanTransformer);

    transformer.setLogout(msg -> logInfo(msg));

    queryBeanTransformer.setMessageListener(msg -> logInfo(msg));

    ProgressIndicator progressIndicator = compileContext.getProgressIndicator();
    progressIndicator.setIndeterminate(true);
    progressIndicator.setText("Ebean enhancement");

    for (Entry<String, File> entry : compiledClasses.entrySet()) {
      String className = entry.getKey();
      File file = entry.getValue();

      progressIndicator.setText2(className);

      ApplicationManager.getApplication().runWriteAction(() ->
          processEnhancement(classLoader, combinedTransform, className, file));
    }

    logInfo("Ebean enhancement done!");
  }

  private void processEnhancement(IdeaClassLoader classLoader, CombinedTransform combinedTransform, String className, File file) {
    try {
      byte[] origBytes = readFileBytes(file);

      CombinedTransform.Response response = combinedTransform.transform(classLoader, className, null, null, origBytes);
      if (response.isEnhanced()) {
        writeTransformed(file, response.getClassBytes());
        logInfo("enhanced: " + className + " type:" + (response.isFirst() ? " e" : "") + (response.isSecond() ? " q" : ""));
      }

    } catch (Exception e) {
      logError("Exception trying to enhance:" + className + " error:" + e.getMessage());
    }
  }

  private void logInfo(String msg) {
    compileContext.addMessage(CompilerMessageCategory.INFORMATION, msg, null, -1, -1);
  }

  private void logError(String msg) {
    compileContext.addMessage(CompilerMessageCategory.ERROR, msg, null, -1, -1);
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
