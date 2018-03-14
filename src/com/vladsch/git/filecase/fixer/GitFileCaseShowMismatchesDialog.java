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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.vladsch.git.filecase.fixer.GitFileFixerProjectRoots.GitRepoFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import static com.vladsch.git.filecase.fixer.GitFixerConfiguration.*;

public class GitFileCaseShowMismatchesDialog extends DialogWrapper {
    private static final Logger logger = Logger.getInstance(GitFileCaseShowMismatchesDialog.class);

    JPanel myMainPanel;
    private GitFileCaseShowMismatches myShowMatchesForm;

    public GitFileCaseShowMismatchesDialog(JComponent parent, List<GitRepoFile> repoFileList) {
        super(parent, false);

        myShowMatchesForm.setRepoFileList(repoFileList);

        init();
        setTitle(Bundle.message("show.mismatches.dialog.title"));
        setModal(true);
    }

    void updateOkButton() {
        setOKActionEnabled(false);
        for (GitRepoFile repoFile : myShowMatchesForm.getRepoFileList()) {
            if (repoFile.fixAction != FIX_PROMPT) {
                setOKActionEnabled(true);
            }
        }
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

    @NotNull
    @Override
    protected Action[] createActions() {
        super.createDefaultActions();
        setOKButtonText(Bundle.message("show.mismatches.dialog.ok.label"));
        return new Action[] { getOKAction(), getCancelAction() };
    }

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

    @NotNull
    @Override
    protected Action[] createLeftSideActions() {
        super.createDefaultActions();
        return new Action[] { new MyAction(Bundle.message("show.mismatches.dialog.fix-git"), () -> {
            forAllRepoFiles(FIX_GIT);
        }), new MyAction(Bundle.message("show.mismatches.dialog.fix-file"), () -> {
            forAllRepoFiles(FIX_FILE_SYSTEM);
        }), new MyAction(Bundle.message("show.mismatches.dialog.fix-none"), () -> {
            forAllRepoFiles(FIX_PROMPT);
        }),
        };
    }

    void applyFixes() {
        // remove git paths then add file paths to change git case
        List<GitRepoFile> allRepoFiles = myShowMatchesForm.getRepoFileList();
        ArrayList<GitRepoFile> fixGitList = new ArrayList<>();
        ArrayList<GitRepoFile> fixFileCaseList = new ArrayList<>();

        for (GitRepoFile repoFile : allRepoFiles) {
            if (repoFile.fixAction == FIX_FILE_SYSTEM) {
                fixFileCaseList.add(repoFile);
            } else if (repoFile.fixAction == FIX_GIT) {
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
