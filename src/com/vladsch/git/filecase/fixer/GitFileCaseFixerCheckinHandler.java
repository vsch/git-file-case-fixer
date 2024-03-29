package com.vladsch.git.filecase.fixer;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task.Modal;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBCheckboxMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.PairConsumer;
import com.intellij.util.ui.JBUI;
import com.vladsch.git.filecase.fixer.GitFileFixerProjectRoots.GitRepoFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import static com.intellij.CommonBundle.getCancelButtonText;
import static com.intellij.openapi.ui.Messages.NO;
import static com.intellij.openapi.ui.Messages.YES;
import static com.intellij.openapi.ui.Messages.showYesNoCancelDialog;
import static com.intellij.util.ui.UIUtil.getWarningIcon;
import static com.vladsch.git.filecase.fixer.GitFixerConfiguration.FIX_PROMPT;

public class GitFileCaseFixerCheckinHandler extends CheckinHandler {
    final Project myProject;
    private final CheckinProjectPanel myCheckinProjectPanel;
    final GitFixerConfiguration myConfiguration;

    public GitFileCaseFixerCheckinHandler(CheckinProjectPanel checkinProjectPanel, @NotNull CommitContext commitContext) {
        myProject = checkinProjectPanel.getProject();
        myCheckinProjectPanel = checkinProjectPanel;
        myConfiguration = GitFixerConfiguration.getInstance(myProject);
    }

    @Override
    public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
        JCheckBox checkBox = new JCheckBox("");
        return new MyRefreshableOnComponent(checkBox, myConfiguration);
    }

    @Override
    public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
        if (!myConfiguration.CHECK_FILE_CASE) return ReturnResult.COMMIT;

        Collection<Change> changes = myCheckinProjectPanel.getSelectedChanges();

        // clear caches, just in case, so we get a fresh index list from git
        GitFileFixerProjectRoots.getInstance(myProject).clearCaches();
        GitFileCaseFixerHandlerWorker worker = new GitFileCaseFixerHandlerWorker(myProject, changes, myConfiguration.FIXER_ACTION);

        Ref<Boolean> completed = Ref.create(Boolean.FALSE);
        ProgressManager.getInstance().run(new Modal(myProject, "Looking for New and Edited Files...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                worker.execute();
            }

            @Override
            public void onSuccess() {
                completed.set(Boolean.TRUE);
            }
        });

        if (completed.get() && (worker.getMismatchedUnmodifiedFiles().isEmpty() && worker.getMismatchedModifiedFiles().isEmpty() &&
                worker.getMismatchedUnmodifiedFiles().isEmpty())) return ReturnResult.COMMIT;
        if (!completed.get()) return ReturnResult.CANCEL;
        return showResults(worker, executor);
    }

    private ReturnResult showResults(GitFileCaseFixerHandlerWorker worker, CommitExecutor executor) {
        if (myConfiguration.FIXER_ACTION == FIX_PROMPT) {
            String commitButtonText = executor != null ? executor.getActionText() : myCheckinProjectPanel.getCommitActionName();
            commitButtonText = StringUtil.trimEnd(commitButtonText, "...");

            String text = createDialogMessage(worker);
            boolean mismatchesFound = worker.getMismatchedUnmodifiedFiles().size() + worker.getMismatchedModifiedFiles().size() > 0;
            String title = Bundle.message("git.filecase.fixer.mismatches.found.title");
            if (mismatchesFound) {
                return askReviewOrCommit(worker, commitButtonText, text, title);
            } else if (worker.getMismatchedFixedFiles().size() > 0) {
                // TODO: show balloon with status
            }
        }
        GitFileFixerProjectRoots.getInstance(myProject).clearCaches();
        return ReturnResult.COMMIT;
    }

    @NotNull
    private ReturnResult askReviewOrCommit(
            @NotNull GitFileCaseFixerHandlerWorker worker,
            @NotNull String commitButton,
            @NotNull String text,
            @NotNull String title
    ) {
        String yesButton = Bundle.message("git.filecase.fixer.in.new.review.button");
        switch (showYesNoCancelDialog(myProject, text, title, yesButton, commitButton, getCancelButtonText(), getWarningIcon())) {
            case YES:
                showFixes(worker);
                return ReturnResult.CLOSE_WINDOW;
            case NO:
                GitFileFixerProjectRoots.getInstance(myProject).clearCaches();
                return ReturnResult.COMMIT;
        }
        GitFileFixerProjectRoots.getInstance(myProject).clearCaches();
        return ReturnResult.CANCEL;
    }

    private void showFixes(GitFileCaseFixerHandlerWorker worker) {
        // after collecting results for display, clear caches
        ArrayList<GitRepoFile> mismatchedFiles = new ArrayList<>(worker.getMismatchedModifiedFiles());
        mismatchedFiles.addAll(worker.getMismatchedUnmodifiedFiles());
        GitFileCaseShowMismatchesDialog dialog = new GitFileCaseShowMismatchesDialog(Objects.requireNonNull(WindowManager.getInstance().findVisibleFrame()).getRootPane(), mismatchedFiles);
        boolean result = dialog.showAndGet();
        if (result) {
            dialog.applyFixes();
        }
        GitFileFixerProjectRoots.getInstance(myProject).clearCaches();
    }

    private static String createDialogMessage(GitFileCaseFixerHandlerWorker worker) {
        int unmodified = worker.getMismatchedUnmodifiedFiles().size();
        int changed = worker.getMismatchedModifiedFiles().size();
        if (changed == 0) {
            return Bundle.message("git.filecase.fixer.handler.only.unmodified", unmodified);
        } else if (unmodified == 0) {
            return Bundle.message("git.filecase.fixer.handler.only.modified", changed);
        } else {
            return Bundle.message("git.filecase.fixer.handler.unmodified.modified", unmodified, changed);
        }
    }

    private static class MyRefreshableOnComponent implements RefreshableOnComponent, UnnamedConfigurable {
        private final JCheckBox myCheckBox;
        private final GitFixerConfiguration myConfiguration;

        public MyRefreshableOnComponent(JCheckBox checkBox, GitFixerConfiguration configuration) {
            myCheckBox = checkBox;
            myConfiguration = configuration;
        }

        @Override
        public @Nullable JComponent createComponent() {
            return getComponent();
        }

        @Override
        public boolean isModified() {
            return myConfiguration.CHECK_FILE_CASE != myCheckBox.isSelected();
        }

        @Override
        public void apply() throws ConfigurationException {
            saveState();
        }

        @Override
        public void reset() {
            restoreState();
        }

        @Override
        public JComponent getComponent() {
            JPanel panel = new JPanel(new BorderLayout(0, 0));
            JPanel contentPanel = new JPanel(new GridLayoutManager(1, 6, JBUI.emptyInsets(), 4, -1));
            panel.add(contentPanel, BorderLayout.WEST);

            JLabel prefix = new JLabel(Bundle.message("before.checkin.git.filecase.fixer.check.prefix"));
            LinkLabel<String> fileTypeLink = new LinkLabel<>("", null);
            JLabel middle = new JLabel(Bundle.message("before.checkin.git.filecase.fixer.check.middle"));
            LinkLabel<String> fixLink = new LinkLabel<>("", null);

            contentPanel.add(myCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 0, 0, null, null, null));
            contentPanel.add(prefix, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 0, 0, null, null, null));
            contentPanel.add(fileTypeLink, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 0, 0, null, null, null));
            contentPanel.add(middle, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 0, 0, null, null, null));
            contentPanel.add(fixLink, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, 0, 0, null, null, null));
            contentPanel.add(new Spacer(), new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_HORIZONTAL, 0, 0, null, null, null));

            Runnable updateCheckBoxText = () -> {
                String fileType = myConfiguration.CHECK_UNMODIFIED_FILES ? Bundle.message("git.filecase.check.type.unmodified") : Bundle.message("git.filecase.check.type.modified");

                fileTypeLink.setText(fileType);

                final String result;
                if (myConfiguration.FIXER_ACTION == GitFixerConfiguration.FIX_GIT) {
                    result = Bundle.message("git.filecase.fixer.name.git");
                } else if (myConfiguration.FIXER_ACTION == GitFixerConfiguration.FIX_FILE_SYSTEM) {
                    result = Bundle.message("git.filecase.fixer.name.file-system");
                } else {
                    result = Bundle.message("git.filecase.fixer.name.match.prompt");
                }

                fixLink.setText(result);
            };

            updateCheckBoxText.run();

            fileTypeLink.setListener((aSource, aLinkData) -> {
                JBPopupMenu myPopupMenuActions = new JBPopupMenu();
                final JBCheckboxMenuItem checkUnmodifiedFiles = new JBCheckboxMenuItem(Bundle.message("git.filecase.check.unmodified"));
                final JBCheckboxMenuItem checkModifiedFiles = new JBCheckboxMenuItem(Bundle.message("git.filecase.check.modified"));

                myPopupMenuActions.add(checkUnmodifiedFiles);
                myPopupMenuActions.add(checkModifiedFiles);

                Runnable updateCheckedState = () -> {
                    updateCheckBoxText.run();
                    checkUnmodifiedFiles.setSelected(myConfiguration.CHECK_UNMODIFIED_FILES);
                    checkModifiedFiles.setSelected(!myConfiguration.CHECK_UNMODIFIED_FILES);
                };

                updateCheckedState.run();

                checkUnmodifiedFiles.addActionListener(e1 -> {
                    myConfiguration.CHECK_UNMODIFIED_FILES = true;
                    updateCheckedState.run();
                });

                checkModifiedFiles.addActionListener(e1 -> {
                    myConfiguration.CHECK_UNMODIFIED_FILES = false;
                    updateCheckedState.run();
                });

                myPopupMenuActions.show(fileTypeLink, 10, fileTypeLink.getWidth());
            }, null);

            fixLink.setListener((aSource, aLinkData) -> {
                JBPopupMenu myPopupMenuActions = new JBPopupMenu();
                final JBCheckboxMenuItem fixAsk = new JBCheckboxMenuItem(Bundle.message("git.filecase.fixer.name.menu.prompt"));
                final JBCheckboxMenuItem fixFileCase = new JBCheckboxMenuItem(Bundle.message("git.filecase.fixer.name.menu.file-system"));
                final JBCheckboxMenuItem fixGit = new JBCheckboxMenuItem(Bundle.message("git.filecase.fixer.name.menu.git"));

                myPopupMenuActions.add(fixAsk);
                myPopupMenuActions.add(fixFileCase);
                myPopupMenuActions.add(fixGit);

                Runnable updateCheckedState = () -> {
                    updateCheckBoxText.run();
                    fixAsk.setSelected(myConfiguration.FIXER_ACTION == FIX_PROMPT);
                    fixFileCase.setSelected(myConfiguration.FIXER_ACTION == GitFixerConfiguration.FIX_FILE_SYSTEM);
                    fixGit.setSelected(myConfiguration.FIXER_ACTION == GitFixerConfiguration.FIX_GIT);
                };

                updateCheckedState.run();

                fixAsk.addActionListener(e1 -> {
                    myConfiguration.FIXER_ACTION = FIX_PROMPT;
                    updateCheckedState.run();
                });

                fixFileCase.addActionListener(e1 -> {
                    myConfiguration.FIXER_ACTION = GitFixerConfiguration.FIX_FILE_SYSTEM;
                    updateCheckedState.run();
                });

                fixGit.addActionListener(e1 -> {
                    myConfiguration.FIXER_ACTION = GitFixerConfiguration.FIX_GIT;
                    updateCheckedState.run();
                });

                myPopupMenuActions.show(fixLink, 10, fixLink.getWidth());
            }, null);

            return panel;
        }

        @Override
        public void refresh() {
        }

        @Override
        public void saveState() {
            myConfiguration.CHECK_FILE_CASE = myCheckBox.isSelected();
        }

        @Override
        public void restoreState() {
            myCheckBox.setSelected(myConfiguration.CHECK_FILE_CASE);
        }
    }
}
