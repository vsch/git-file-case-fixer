package com.vladsch.git.filecase.fixer;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
        name = "GitFileCaseFixerConfiguration",
        storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public final class GitFixerConfiguration implements PersistentStateComponent<GitFixerConfiguration> {
    public static final int FIX_PROMPT = 0;
    public static final int FIX_GIT = 1;
    public static final int FIX_FILE_SYSTEM = 2;

    public int FIXER_ACTION = FIX_PROMPT;
    public boolean CHECK_FILE_CASE = true;
    public boolean CHECK_UNMODIFIED_FILES = true;

    @Override
    public GitFixerConfiguration getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull GitFixerConfiguration state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static GitFixerConfiguration getInstance(@NotNull Project project) {
        return project.getService(GitFixerConfiguration.class);
    }
}
