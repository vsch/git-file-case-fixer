package com.vladsch.git.filecase.fixer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vcs.changes.ChangesViewManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.vladsch.git.filecase.fixer.GitFileFixerProjectRoots.GitRepoFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import static com.vladsch.git.filecase.fixer.GitFixerConfiguration.FIX_FILE_SYSTEM;
import static com.vladsch.git.filecase.fixer.GitFixerConfiguration.FIX_GIT;
import static com.vladsch.git.filecase.fixer.GitFixerConfiguration.FIX_PROMPT;

public class GitFileCaseShowMismatchesDialog extends DialogWrapper {
    JPanel myMainPanel;
    private GitFileCaseShowMismatches myShowMatchesForm;

    public GitFileCaseShowMismatchesDialog(JComponent parent, List<GitRepoFile> repoFileList) {
        super(parent, false);

        myShowMatchesForm.setRepoFileList(repoFileList);

        init();
        setTitle(Bundle.message("show.mismatches.dialog.title"));
        setModal(true);
    }

    @Nullable
    @Override
    protected String getDimensionServiceKey() {
        return "GitFileCaseFixer.ShowMismatchesDialog";
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        myShowMatchesForm = new GitFileCaseShowMismatches();
    }

    @Override
    protected Action @NotNull [] createActions() {
        createDefaultActions();
        setOKButtonText(Bundle.message("show.mismatches.dialog.ok.label"));
        return new Action[] { getOKAction(), getCancelAction() };
    }

    @SuppressWarnings("SerializableInnerClassWithNonSerializableOuterClass")
    protected class MyAction extends OkAction {
        final private Runnable runnable;

        protected MyAction(String name, Runnable runnable) {
            super();
            putValue(Action.NAME, name);
            this.runnable = runnable;
        }

        @Override
        protected void doAction(ActionEvent e) {
            runnable.run();
        }
    }

    void forAllRepoFiles(int fixAction) {
        int iMax = myShowMatchesForm.myRepoFilesModel.getRowCount();
        for (int i = 0; i < iMax; i++) {
            myShowMatchesForm.myRepoFilesModel.setValueAt(GitFileCaseShowMismatches.fixChoices[fixAction], i, 1);
        }
    }

    @Override
    protected Action @NotNull [] createLeftSideActions() {
        createDefaultActions();
        return new Action[] { 
                new MyAction(Bundle.message("show.mismatches.dialog.fix-git"), () -> forAllRepoFiles(FIX_GIT))
                , new MyAction(Bundle.message("show.mismatches.dialog.fix-file"), () -> forAllRepoFiles(FIX_FILE_SYSTEM))
                , new MyAction(Bundle.message("show.mismatches.dialog.fix-none"), () -> forAllRepoFiles(FIX_PROMPT)),
        };
    }

    void applyFixes() {
        // remove git paths then add file paths to change git case
        List<GitRepoFile> allRepoFiles = myShowMatchesForm.getRepoFileList();
        ArrayList<GitRepoFile> fixGitList = new ArrayList<>();
        ArrayList<GitRepoFile> fixFileCaseList = new ArrayList<>();
        Project project = null;

        for (GitRepoFile repoFile : allRepoFiles) {
            if (repoFile.fixAction == FIX_FILE_SYSTEM) {
                project = repoFile.gitRepo.myProject;
                fixFileCaseList.add(repoFile);
            } else if (repoFile.fixAction == FIX_GIT) {
                project = repoFile.gitRepo.myProject;
                fixGitList.add(repoFile);
            }
        }

        if (!fixGitList.isEmpty()) {
            GitFileFixerProjectRoots.fixGitFileCase(fixGitList);
        }

        if (!fixFileCaseList.isEmpty()) {
            // rename file
            GitFileFixerProjectRoots.fixFileSystemCase(fixFileCaseList);
        }

        if (project != null) {
            Project finalProject = project;
            VirtualFileManager.getInstance().asyncRefresh(() -> ChangesViewManager.getInstance(finalProject).scheduleRefresh());
        }
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        // see if errors
        return null;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return myShowMatchesForm.myMainPanel;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return myMainPanel;
    }
}
