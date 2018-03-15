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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.wm.WindowManager;
import com.vladsch.git.filecase.fixer.GitFileFixerProjectRoots.GitRepoFile;

import java.util.ArrayList;

public class ShowGitFileCaseFixMismatchedAction extends AnAction implements DumbAware {
    protected ShowGitFileCaseFixMismatchedAction() {
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            e.getPresentation().setEnabled(true);
            //e.getPresentation().putClientProperty(Toggleable.SELECTED_PROPERTY, styleSettings.USE_ACTUAL_CHAR_WIDTH);
        } else {
            e.getPresentation().setEnabled(false);
            e.getPresentation().setVisible(false);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            GitFileFixerProjectRoots projectRoots = GitFileFixerProjectRoots.getInstance(project);
            projectRoots.clearCaches();
            ArrayList<GitRepoFile> mismatchedFiles = new ArrayList<>();

            projectRoots.visitAllIndexFiles((repoFile) -> {
                if (!repoFile.gitPath.equals(repoFile.filePath)) {
                    mismatchedFiles.add(repoFile);
                }
            });

            if (!mismatchedFiles.isEmpty()) {
                GitFileCaseShowMismatchesDialog dialog = new GitFileCaseShowMismatchesDialog(WindowManager.getInstance().findVisibleFrame().getRootPane(), mismatchedFiles);
                boolean result = dialog.showAndGet();
                if (result) {
                    dialog.applyFixes();
                }
            } else {
                // show popup that there are no mismatches
                PopupUtil.showBalloonForComponent(WindowManager.getInstance().findVisibleFrame().getRootPane(), "No Mismatches between git and file system", MessageType.INFO, true, null);
                //PopupUtil.showBalloonForActiveComponent("No Mismatched file case", MessageType.INFO);
            }
            projectRoots.clearCaches();
        }
    }
}
