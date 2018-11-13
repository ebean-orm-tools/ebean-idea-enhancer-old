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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import io.ebean.enhance.Transformer;
import io.ebean.enhance.common.AgentManifest;
import io.ebean.enhance.common.ClassMetaCache;
import io.ebean.enhance.common.EnhanceContext;
import io.ebean.enhance.common.InputStreamTransform;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This task actually hand all successfully compiled classes over to the Ebean weaver.
 *
 * @author Mario Ivankovits, mario@ops.co.at
 * @author yevgenyk - Updated 28/04/2014 for IDEA 13
 */
class EbeanEnhancementTask {

	private static final Logger log = Logger.getInstance("io.ebean");

	private final ClassMetaCache metaCache;

	private final CompileContext compileContext;

	private final Map<String, File> compiledClasses;

	EbeanEnhancementTask(ClassMetaCache metaCache, CompileContext compileContext, Map<String, File> compiledClasses) {
		this.metaCache = metaCache;
		this.compileContext = compileContext;
		this.compiledClasses = compiledClasses;
	}

	void process() {

		if (!compiledClasses.isEmpty()) {
			Project project = compileContext.getProject();

			TransactionGuard.getInstance()
					.submitTransactionLater(project,
							() -> ApplicationManager.getApplication().runWriteAction(
									this::performEnhancement));
		}
	}

	private void performEnhancement() {
		try {
			Project project = compileContext.getProject();
			PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
			if (psiDocumentManager.hasUncommitedDocuments()) {
				psiDocumentManager.commitAllDocuments();
			}

			doProcess();
		} catch (Exception e) {
			log.error("Error performing Ebean enhancement", e);
			logError(e.getClass().getName() + ":" + e.getMessage());
		}
	}

	private void doProcess() throws IOException {

		ClassLoader classLoader = buildClassLoader();

		AgentManifest manifest = AgentManifest.read(classLoader, null);

		int debugLevel = debugLevel();

		logInfo("Ebean 11+ enhancement started, packages - "
				+ " entity: " + manifest.getEntityPackages()
				+ " transaction: " + manifest.getTransactionalPackages()
				+ " queryBean: " + manifest.getQuerybeanPackages()
				+ " debug: " + debugLevel + " v:1192 profileLocation:" + manifest.isEnableProfileLocation());

		EnhanceContext enhanceContext = new EnhanceContext(new BasicClassBytesReader(), "debug=" + debugLevel, manifest, metaCache);
		enhanceContext.setThrowOnError(true);

		Transformer transformer = new Transformer(enhanceContext);
		if (debugLevel > 0) {
			transformer.setLogout(this::logInfo);
		}

		ProgressIndicator progressIndicator = compileContext.getProgressIndicator();
		progressIndicator.setIndeterminate(true);
		progressIndicator.setText("Ebean enhancement");

		try {
			for (Entry<String, File> entry : compiledClasses.entrySet()) {
				String className = entry.getKey();
				progressIndicator.setText2(className);
				processEnhancement(classLoader, transformer, className, entry.getValue());
			}

			metaCache.setFallback();
			logInfo("Ebean enhancement done!  fbHits:" + metaCache.getFallbackHits());
		} catch (Throwable e) {
			log.error("Error processing enhancement", e);
			logError("Exception trying to enhance. Please try Build -> Rebuild Project, error:" + e.getMessage());
		}
	}

	private int debugLevel() {
		if (log.isTraceEnabled()) {
			return 3;
		} else if (log.isDebugEnabled()) {
			return 1;
		} else {
			return 0;
		}
	}

	private void processEnhancement(ClassLoader classLoader, Transformer transformer, String className, File file) {
		try {
			byte[] origBytes = readFileBytes(file);
			className = className.replace('.', '/');

			byte[] transformed = transformer.transform(classLoader, className, null, null, origBytes);
			if (transformed != null) {
				writeTransformed(file, transformed);
				logInfo("enhanced: " + className);
			}

		} catch (Exception e) {
      log.error("Exception trying to enhance:" + className, e);
      logError("Exception trying to enhance:" + className + " Please try Build -> Rebuild Project, error:" + e.getMessage());
		}
	}

	/**
	 * Build the base classLoader. Ideally we have the "compile classpath" but we don't have that here.
	 * (Agents use classLoader to determine common super classes etc).
	 */
	private ClassLoader buildClassLoader() throws MalformedURLException {

		Module[] modules = compileContext.getProjectCompileScope().getAffectedModules();

		List<URL> out = new ArrayList<>();
		for (Module module : modules) {
			addFileSystemUrl(out, compileContext.getModuleOutputDirectory(module));
			addFileSystemUrl(out, compileContext.getModuleOutputDirectoryForTests(module));
			addModulePaths(module, out);
		}

		ClassLoader pluginClassLoader = this.getClass().getClassLoader();
		URL[] urls = out.toArray(new URL[out.size()]);
		if (log.isTraceEnabled()) {
			log.trace("ClassPath: " + Arrays.toString(urls));
		}
		return new URLClassLoader(urls, pluginClassLoader);
	}

	private void addModulePaths(Module module, List<URL> out) {

		for (String pathEntry : OrderEnumerator.orderEntries(module).recursively().getPathsList().getPathList()) {
			try {
				out.add(new File(pathEntry).toURI().toURL());
			} catch (MalformedURLException e) {
				log.error("Error adding " + pathEntry + " to classpath", e);
			}
		}
	}

	private void logInfo(String msg) {
		compileContext.addMessage(CompilerMessageCategory.INFORMATION, msg, null, -1, -1);
	}

	private void logError(String msg) {
		compileContext.addMessage(CompilerMessageCategory.ERROR, msg, null, -1, -1);
	}

	private void addFileSystemUrl(List<URL> out, VirtualFile outDir) throws MalformedURLException {
		if (outDir != null) {
			String url = outDir.getUrl();
			if (outDir.isDirectory() && !url.endsWith("/")) {
				url = url + "/";
			}
			if ('\\' == File.separatorChar) {
				// take into account windows file system
				url = url.replace("file://", "file:/");
			}
			out.add(new URL(url));
		}
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
