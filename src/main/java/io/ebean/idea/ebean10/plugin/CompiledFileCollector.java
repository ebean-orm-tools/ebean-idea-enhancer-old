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

package io.ebean.idea.ebean10.plugin;

import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import io.ebean.enhance.common.ClassMetaCache;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author yevgenyk - Updated 28/04/2014 for IDEA 13
 */
public class CompiledFileCollector implements CompilationStatusListener {

  private Map<String, CompiledFile> compiledClasses = new HashMap<>();

  private final ClassMetaCache metaCache = new ClassMetaCache();

  @Override
  public void fileGenerated(String outputRoot, String relativePath) {

    // Collect all valid compiled '.class' files
    CompiledFile compiledFile = createCompiledFile(outputRoot, relativePath);
    if (compiledFile != null) {
      addClass(compiledFile);
    }
  }

  private void addClass(CompiledFile compiledFile) {
    this.compiledClasses.put(compiledFile.className, compiledFile);
  }

  private CompiledFile createCompiledFile(String outputRoot, String relativePath) {

    if (outputRoot == null || relativePath == null || !relativePath.endsWith(".class")) {
      return null;
    }

    File file = new File(outputRoot, relativePath);
    if (!file.exists()) {
      return null;
    }

    String className = resolveClassName(relativePath);
    return new CompiledFile(file, className, outputRoot);
  }

  /**
   * Given a content path and a class file path, resolve the fully qualified class name
   */
  private static String resolveClassName(String relativePath) {
    int extensionPos = relativePath.lastIndexOf('.');
    return relativePath.substring(0, extensionPos).replace('/', '.');
  }

  @Override
  public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {


    Map<String,File> asFileMap = new LinkedHashMap<>();

    Collection<CompiledFile> values = compiledClasses.values();
    for (CompiledFile value : values) {
      addEntry(asFileMap, value);
      CompiledFile qb = value.toQueryBean();
      if (qb != null) {
        addEntry(asFileMap, qb);
      }
      CompiledFile assocBean = value.toQueryAssocBean();
      if (assocBean != null) {
        addEntry(asFileMap, assocBean);
      }
    }

    new EbeanEnhancementTask(metaCache, compileContext, asFileMap).process();
    this.compiledClasses = new HashMap<>();
  }

  private void addEntry(Map<String, File> asFileMap, CompiledFile value) {
    asFileMap.put(value.className, value.file);
  }

  private static class CompiledFile {

    private final File file;

    private final String className;
    private final String pkgDir;
    private final String shortName;
    private final String outputRoot;

    private CompiledFile(File file, String className) {
      this.file = file;
      this.className = className;
      this.outputRoot = null;
      this.pkgDir = null;
      this.shortName = null;
    }

    private CompiledFile(File file, String className, String outputRoot) {
      this.file = file;
      this.className = className;
      this.outputRoot = outputRoot;

      int pos = className.lastIndexOf('.');
      if (pos == -1) {
        this.pkgDir = null;
        this.shortName = null;
      } else {
        this.pkgDir = className.substring(0, pos).replace('.','/');
        this.shortName = className.substring(pos+1);
      }

    }

    /**
     * Return a query bean (or null) based on naming convention.
     */
    CompiledFile toQueryBean() {
      if (pkgDir != null) {
        return getFile(pkgDir+"/query/Q"+shortName+".class");
      }
      return null;
    }

    /**
     * Return a assoc query bean (or null) based on naming convention.
     */
    CompiledFile toQueryAssocBean() {
      if (pkgDir != null) {
        return getFile(pkgDir+"/query/assoc/QAssoc"+shortName+".class");
      }
      return null;
    }

    private CompiledFile getFile(String assocClassName) {

      File file = new File(outputRoot, assocClassName);
      if (file.exists()) {
        return new CompiledFile(file, resolveClassName(assocClassName));
      }
      return null;
    }

    @Override
    public String toString() {
      return "CompiledFile[file:" + file +"  className:" + className + "]";
    }
  }
}
