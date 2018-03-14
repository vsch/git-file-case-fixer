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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBCheckboxMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.PairConsumer;
import com.vladsch.git.filecase.fixer.GitFileFixerProjectRoots.GitRepoFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.CommonBundle.getCancelButtonText;
import static com.intellij.openapi.ui.Messages.*;
import static com.intellij.util.ui.UIUtil.getWarningIcon;
import static com.vladsch.git.filecase.fixer.GitFixerConfiguration.FIX_PROMPT;

public class GitFileCaseFixerCheckinHandler extends CheckinHandler {
    final Project myProject;
    private final CheckinProjectPanel myCheckinProjectPanel;
    final GitFixerConfiguration myConfiguration;

    public GitFileCaseFixerCheckinHandler(CheckinProjectPanel checkinProjectPanel) {
        myProject = checkinProjectPanel.getProject();
        myCheckinProjectPanel = checkinProjectPanel;
        myConfiguration = GitFixerConfiguration.getInstance(myProject);
    }

    static void disableWhenDumb(@NotNull Project project, @NotNull JCheckBox checkBox, @NotNull String tooltip) {
        boolean dumb = DumbService.isDumb(project);
        checkBox.setEnabled(!dumb);
        checkBox.setToolTipText(dumb ? tooltip : "");
    }

    @Override
    public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
        JCheckBox checkBox = new JCheckBox("");
        return new RefreshableOnComponent() {
            @Override
            public JComponent getComponent() {
                JPanel panel = new JPanel(new BorderLayout(4, 0));
                panel.add(checkBox, BorderLayout.WEST);

                HyperlinkLabel linkLabel = new HyperlinkLabel("dummy", null);
                linkLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

                Runnable updateCheckBoxText = () -> {
                    String fileType = myConfiguration.CHECK_UNMODIFIED_FILES ? Bundle.message("git.filecase.check.type.unmodified") : Bundle.message("git.filecase.check.type.modified");

                    final String result;
                    if (myConfiguration.FIXER_ACTION == GitFixerConfiguration.FIX_GIT) {
                        result = Bundle.message("before.checkin.git.filecase.fixer.check", fileType, Bundle.message("git.filecase.fixer.name.git"));
                    } else if (myConfiguration.FIXER_ACTION == GitFixerConfiguration.FIX_FILE_SYSTEM) {
                        result = Bundle.message("before.checkin.git.filecase.fixer.check", fileType, Bundle.message("git.filecase.fixer.name.file-system"));
                    } else {
                        result = Bundle.message("before.checkin.git.filecase.fixer.check", fileType, Bundle.message("git.filecase.fixer.name.match.prompt"));
                    }

                    linkLabel.setHtmlText(result);
                };

                updateCheckBoxText.run();

                linkLabel.addHyperlinkListener( e -> {
                    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        String aUrl = (String) e.getDescription();
                        Point mouseScreenPos = MouseInfo.getPointerInfo().getLocation();
                        Point editorScreenPos = linkLabel.getLocationOnScreen();
                        Point mouseLinkPos = new Point(mouseScreenPos.x - editorScreenPos.x, mouseScreenPos.y - editorScreenPos.y);

                        if (mouseLinkPos.x < linkLabel.getWidth()/2) {
                            JBPopupMenu myPopupMenuActions = new JBPopupMenu("Actions");
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

                            myPopupMenuActions.show(linkLabel, mouseLinkPos.x, mouseLinkPos.y);
                        } else {
                            JBPopupMenu myPopupMenuActions = new JBPopupMenu("Actions");
                            final JBCheckboxMenuItem prompt = new JBCheckboxMenuItem(Bundle.message("git.filecase.fixer.name.menu.prompt"));
                            final JBCheckboxMenuItem fixFileCase = new JBCheckboxMenuItem(Bundle.message("git.filecase.fixer.name.menu.file-system"));
                            final JBCheckboxMenuItem fixGit = new JBCheckboxMenuItem(Bundle.message("git.filecase.fixer.name.menu.git"));

                            myPopupMenuActions.add(prompt);
                            myPopupMenuActions.add(fixFileCase);
                            myPopupMenuActions.add(fixGit);

                            Runnable updateCheckedState = () -> {
                                updateCheckBoxText.run();
                                prompt.setSelected(myConfiguration.FIXER_ACTION == FIX_PROMPT);
                                fixFileCase.setSelected(myConfiguration.FIXER_ACTION == GitFixerConfiguration.FIX_FILE_SYSTEM);
                                fixGit.setSelected(myConfiguration.FIXER_ACTION == GitFixerConfiguration.FIX_GIT);
                            };

                            updateCheckedState.run();

                            prompt.addActionListener(e1 -> {
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

                            myPopupMenuActions.show(linkLabel, mouseLinkPos.x, mouseLinkPos.y);
                        }
                    }
                });

                panel.add(linkLabel, BorderLayout.CENTER);
                return panel;
            }

            @Override
            public void refresh() {
            }

            @Override
            public void saveState() {
                myConfiguration.CHECK_FILE_CASE = checkBox.isSelected();
            }

            @Override
            public void restoreState() {
                checkBox.setSelected(myConfiguration.CHECK_FILE_CASE);
            }
        };
    }

    @Override
    public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
        if (!myConfiguration.CHECK_FILE_CASE) return ReturnResult.COMMIT;

        Collection<Change> changes = myCheckinProjectPanel.getSelectedChanges();

        // clear caches, just in case so we get a fresh index list from git
        GitFileFixerProjectRoots.getInstance(myProject).clearCaches();
        GitFileCaseFixerHandlerWorker worker = new GitFileCaseFixerHandlerWorker(myProject, changes, myConfiguration.FIXER_ACTION);

        Ref<Boolean> completed = Ref.create(Boolean.FALSE);
        ProgressManager.getInstance().run(new Task.Modal(myProject, "Looking for New and Edited Files...", true) {
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
                //myConfiguration.CHECK_UNMODIFIED_FILES = false;
                return ReturnResult.CLOSE_WINDOW;
            case NO:
                GitFileFixerProjectRoots.getInstance(myProject).clearCaches();
                //myConfiguration.CHECK_UNMODIFIED_FILES = false;
                return ReturnResult.COMMIT;
        }
        GitFileFixerProjectRoots.getInstance(myProject).clearCaches();
        return ReturnResult.CANCEL;
    }

    private void showFixes(GitFileCaseFixerHandlerWorker worker) {
        // after collecting results for display, clear caches
        ArrayList<GitRepoFile> mismatchedFiles = new ArrayList<>(worker.getMismatchedModifiedFiles());
        mismatchedFiles.addAll(worker.getMismatchedUnmodifiedFiles());
        GitFileCaseShowMismatchesDialog dialog = new GitFileCaseShowMismatchesDialog(WindowManager.getInstance().findVisibleFrame().getRootPane(), mismatchedFiles);
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
}
