package io.ebean.idea.ebean10.plugin;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.ebean.enhance.common.AgentManifest;

import java.io.IOException;

/**
 * Helper to read the ebean.mf manifest files.
 * <p>
 * These tell the query bean enhancement which beans are query beans and hence
 * which get field calls should be replaced with query bean method calls.
 * </p>
 */
class ManifestReader {

  private final CompileContext compileContext;

  ManifestReader(CompileContext compileContext) {
    this.compileContext = compileContext;
  }

  /**
   * Find the type query manifest files externally to the agent as classLoader getResources does
   * not work for the agent when run in the IDEA plugin.
   *
   * @return The packages containing type query beans (this is required for the enhancement).
   */
  AgentManifest findManifests() {

    AgentManifest manifestReader = new AgentManifest();

    ModuleManager moduleManager = ModuleManager.getInstance(compileContext.getProject());
    Module[] modules = moduleManager.getModules();

    // read manifest files via module target directories
    for (Module module : modules) {
      VirtualFile outputDirectory = compileContext.getModuleOutputDirectory(module);
      if (outputDirectory != null) {
        readManifest(manifestReader, outputDirectory, "META-INF/ebean.mf");
        readManifest(manifestReader, outputDirectory, "ebean.mf");
      }
    }

    Project project = compileContext.getProject();
    GlobalSearchScope searchScope = GlobalSearchScope.allScope(compileContext.getProject());

    // read manifest files via project search
    readByName(manifestReader, project, searchScope, "ebean.mf");
    return manifestReader;
  }

  private void readByName(AgentManifest manifestReader, Project project, GlobalSearchScope searchScope, String fileName) {
    PsiFile[] files = FilenameIndex.getFilesByName(project, fileName, searchScope);
    for (int i = 0; i < files.length; i++) {
      try {
        VirtualFile file = files[i].getVirtualFile();
        if (file.exists()) {
          manifestReader.addResource(file.getInputStream());
        }
      } catch (IOException e) {
        compileContext.addMessage(CompilerMessageCategory.ERROR, "Error reading ebean manifest file error:" + e, null, -1, -1);
      }
    }
  }

  private void readManifest(AgentManifest manifestReader, VirtualFile outputDirectory, String path) {
    VirtualFile mf = outputDirectory.findFileByRelativePath(path);
    if (mf != null && mf.exists()) {
      try {
        //compileContext.addMessage(CompilerMessageCategory.INFORMATION, "... read mf:" + outputDirectory + " path:" + path, null, -1, -1);
        manifestReader.addResource(mf.getInputStream());
      } catch (IOException e) {
        compileContext.addMessage(CompilerMessageCategory.ERROR, "Error reading " + path + " from " + outputDirectory + " error:" + e, null, -1, -1);
      }
    }
  }

}
