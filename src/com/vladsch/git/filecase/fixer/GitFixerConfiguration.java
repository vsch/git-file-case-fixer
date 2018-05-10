/*
 * MIT License
 *
 * Copyright (c) 2018, Vladimir Schneider, vladimir.schneider@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.vladsch.git.filecase.fixer;

import com.intellij.openapi.components.*;
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
        return ServiceManager.getService(project, GitFixerConfiguration.class);
    }
}
