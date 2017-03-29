package org.avaje.idea.ebean10.plugin;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import io.ebean.enhance.querybean.AgentManifestReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Helper to read the ebean.mf and ebean-typequery.mf manifest files.
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
  Set<String> findManifests() {

    AgentManifestReader manifestReader = new AgentManifestReader();

    ModuleManager moduleManager = ModuleManager.getInstance(compileContext.getProject());
    Module[] modules = moduleManager.getModules();

    // read manifest files via module target directories
    for (Module module : modules) {
      VirtualFile outputDirectory = compileContext.getModuleOutputDirectory(module);
      if (outputDirectory != null) {
        readManifest(manifestReader, outputDirectory, "META-INF/ebean-typequery.mf");
        readManifest(manifestReader, outputDirectory, "META-INF/ebean.mf");
      }
    }

    Project project = compileContext.getProject();
    GlobalSearchScope searchScope = GlobalSearchScope.allScope(compileContext.getProject());

    // read manifest files via project search
    readByName(manifestReader, project, searchScope, "ebean-typequery.mf");
    readByName(manifestReader, project, searchScope, "ebean.mf");
    return manifestReader.getPackages();
  }

  private void readByName(AgentManifestReader manifestReader, Project project, GlobalSearchScope searchScope, String fileName) {
    PsiFile[] files = FilenameIndex.getFilesByName(project, fileName, searchScope);
    for (int i = 0; i < files.length; i++) {
      //compileContext.addMessage(CompilerMessageCategory.INFORMATION, "... found by search:" + files[i].getVirtualFile(), null, -1, -1);
      manifestReader.addRaw(files[i].getText());
    }
  }

  private void readManifest(AgentManifestReader manifestReader, VirtualFile outputDirectory, String path) {
    VirtualFile mf = outputDirectory.findFileByRelativePath(path);
    if (mf != null) {
      try {
        //compileContext.addMessage(CompilerMessageCategory.INFORMATION, "... read mf:" + outputDirectory + " path:" + path, null, -1, -1);
        readManifest(manifestReader, mf.getInputStream());
      } catch (IOException e) {
        compileContext.addMessage(CompilerMessageCategory.ERROR, "Error reading " + path + " from " + outputDirectory + " error:" + e, null, -1, -1);
      }
    }
  }

  /**
   * Read the packages from the manifest file.
   */
  private void readManifest(AgentManifestReader manifestReader, InputStream is) throws IOException {
    Manifest man = new Manifest(is);
    Attributes attributes = man.getMainAttributes();
    String packages = attributes.getValue("packages");
    if (packages != null) {
      manifestReader.addRaw(packages);
    }
  }

}
