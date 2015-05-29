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

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maintains the per project activate flag and setup the compiler stuff appropriate
 *
 * @author Mario Ivankovits, mario@ops.co.at
 * @author yevgenyk - Updated 28/04/2014 for IDEA 13
 */
@State(name = "ebeanEnhancement", storages = {
    @Storage(id = "ebeanEnhancement", file = StoragePathMacros.WORKSPACE_FILE)
})
public class EbeanActionComponent implements ProjectComponent, PersistentStateComponent<EbeanActionComponent.EbeanEnhancementState> {
    private final Project project;
    private final CompiledFileCollector compiledFileCollector;

    private final EbeanEnhancementState ebeanEnhancementState;

    public EbeanActionComponent(Project project) {
        this.project = project;
        this.compiledFileCollector = new CompiledFileCollector();
        this.ebeanEnhancementState = new EbeanEnhancementState();
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "Ebean Action Component";
    }

    @Override
    public void initComponent() {
    }

    @Override
    public void disposeComponent() {
    }

    @Override
    public void projectOpened() {
    }

    @Override
    public void projectClosed() {
        setEnabled(false);
    }

    public boolean isEnabled() {
        return ebeanEnhancementState.enabled;
    }

    public void setEnabled(boolean enabled) {
        if (!this.ebeanEnhancementState.enabled && enabled) {
            getCompilerManager().addCompilationStatusListener(compiledFileCollector);
        } else if (this.ebeanEnhancementState.enabled && !enabled) {
            getCompilerManager().removeCompilationStatusListener(compiledFileCollector);
        }
        this.ebeanEnhancementState.enabled = enabled;
    }

    private CompilerManager getCompilerManager() {
        return CompilerManager.getInstance(project);
    }

    @Nullable
    @Override
    public EbeanEnhancementState getState() {
        return ebeanEnhancementState;
    }

    @Override
    public void loadState(EbeanEnhancementState ebeanEnhancementState) {
        setEnabled(ebeanEnhancementState.enabled);
        XmlSerializerUtil.copyBean(ebeanEnhancementState, this.ebeanEnhancementState);
    }

    public static class EbeanEnhancementState {
        public boolean enabled;
    }
}
