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

package io.ebean.idea.ebean10.plugin;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import io.ebean.enhance.common.ClassBytesReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import net.jcip.annotations.NotThreadSafe;

import static java.util.Collections.singleton;
import static java.util.Optional.ofNullable;

/**
 * Lookup a class file by given class name.
 *
 * @author Mario Ivankovits, mario@ops.co.at
 * @author yevgenyk - Updated 28/04/2014 for IDEA 13
 */
@NotThreadSafe
public class IdeaClassBytesReader implements ClassBytesReader {

  private final CompileContext compileContext;
  private final Map<String, File> compiledClasses;
  private final JavaPsiFacade psiFacade;
  private final GlobalSearchScope globalSearchScope;
  private GlobalSearchScope searchScope;

  public IdeaClassBytesReader(CompileContext compileContext, Map<String, File> compiledClasses) {
    this.compileContext = compileContext;
    this.compiledClasses = compiledClasses;
    globalSearchScope = GlobalSearchScope.allScope(compileContext.getProject());
    this.searchScope = GlobalSearchScope.allScope(compileContext.getProject());
    this.psiFacade = JavaPsiFacade.getInstance(compileContext.getProject());
  }

  @Override
  public byte[] getClassBytes(String classNamePath, ClassLoader classLoader) {

    // Try to fetch the class from the list of files that we know was compiled during this run.
    final String className = classNamePath.replace('/', '.');
    final File compiledFile = compiledClasses.get(className);

    if (compiledFile != null) {
      try {
        return IOUtils.read(new FileInputStream(compiledFile));
      } catch (IOException e) {
        warn("Error reading file contents: " + compiledFile);
        return null;
      }
    }
    if ("Model".equals(className)) {
      return null;
    }

    // The class wasn't compiled on this run, look it up from the project structure (or it's dependencies).
    return lookupClassBytesFallback(classNamePath);
  }

  private byte[] lookupClassBytesFallback(String classNamePath) {
    // Create a Psi compatible className
    final String className = convertToClassName(classNamePath);
    try {

      final PsiClass psiClass = psiFacade.findClass(className, searchScope);
      if (psiClass == null) {
        warn("Couldn't find PsiClass for class: " + className);
        return null;
      }

      final PsiFile psiClassContainingFile = psiClass.getContainingFile();
      final VirtualFile containingFile = psiClassContainingFile.getVirtualFile();
      if (containingFile == null || !"class".equals(psiClassContainingFile.getFileType().getDefaultExtension())) {
        warn("Couldn't find containing class for PsiClass: " + psiClass);
        return null;
      }

      final VirtualFile classFile = getClassFileRelativeContainingFile(containingFile, classNamePath, psiClass, className);
      if (classFile == null) {
        warn("Couldn't find .class file for class: " + className);
        return null;
      }

      try {
        return classFile.contentsToByteArray();
      } catch (IOException e) {
        warn("Error reading file contents: " + classFile);
        return null;
      }
    } catch (IndexNotReadyException e) {
      // Could not read a class (looking for inherited @MappedSuperclass properties)
      warn("IndexNotReadyException reading file contents: " + className);
      return null;
    }
  }

  private VirtualFile getClassFileRelativeContainingFile(VirtualFile containingFile, String classNamePath, PsiClass psiClass, String className) {
    ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(psiClass.getProject());
    VirtualFile classRootForFile = projectFileIndex.getClassRootForFile(containingFile);
    if (classRootForFile == null) {
      warn("Couldn't find class root '" + className + "' in project file index.");
      return containingFile;
    }
    VirtualFile fileByRelativePath = classRootForFile.findFileByRelativePath(classNamePath + ".class");
    if (fileByRelativePath == null) {
      warn("Couldn't find file for class '" + className + "' in project file index.");
      return containingFile;
    }
    return fileByRelativePath;
  }

  private void warn(String message) {
    // warning level messages not showing in Messages so using INFORMATION here
    compileContext.addMessage(CompilerMessageCategory.INFORMATION, "WARN: " + message, null, -1, -1);
  }

  void setSearchScopeFromFile(final File file) {
    GlobalSearchScope searchScope = null;
    if (file != null) {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      if (virtualFile != null) {
        searchScope = findModule(virtualFile);
        if (searchScope == null) {
          String className = virtualFile.getName().replaceAll("$.*", "");
          final VirtualFile parentChild = virtualFile.getParent().findChild(className + ".class");
          if (parentChild != null) {
            searchScope = findModule(parentChild);
          }
        }
      }
      if (searchScope == null) {
        warn("Couldn't find the Module for file " + file);
      }
    }
    this.searchScope = ofNullable(searchScope).orElse(this.globalSearchScope);
  }

  private GlobalSearchScope findModule(final VirtualFile virtualFile) {
    for (Module module : compileContext.getCompileScope().getAffectedModules()) {
      Set<VirtualFile> mainRoot = singleton(compileContext.getModuleOutputDirectory(module));
      if (VfsUtil.isUnder(virtualFile, mainRoot)) {
        return module.getModuleWithDependenciesAndLibrariesScope(false);
      }
      Set<VirtualFile> testRoot = singleton(compileContext.getModuleOutputDirectoryForTests(module));
      if (VfsUtil.isUnder(virtualFile, testRoot)) {
        return module.getModuleWithDependenciesAndLibrariesScope(true);
      }
    }
    return null;
  }

  @NotNull
  private String convertToClassName(final String className) {
    return className.replace('/', '.').replace('$', '.');
  }
}