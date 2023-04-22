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
import java.util.Objects;

public class ShowGitFileCaseFixMismatchedAction extends AnAction implements DumbAware {
    protected ShowGitFileCaseFixMismatchedAction() {
    }

    @Override
    public void update(AnActionEvent e) {
        boolean enabled = e.getProject() != null;
        
        e.getPresentation().setEnabled(enabled);
        e.getPresentation().setVisible(enabled);
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
                GitFileCaseShowMismatchesDialog dialog = new GitFileCaseShowMismatchesDialog(Objects.requireNonNull(WindowManager.getInstance().findVisibleFrame()).getRootPane(), mismatchedFiles);
                boolean result = dialog.showAndGet();
                if (result) {
                    dialog.applyFixes();
                }
            } else {
                // show popup that there are no mismatches
                PopupUtil.showBalloonForActiveComponent("No Mismatches between git and file system", MessageType.INFO);
                //PopupUtil.showBalloonForActiveComponent("No Mismatched file case", MessageType.INFO);
            }
            projectRoots.clearCaches();
        }
    }
}
