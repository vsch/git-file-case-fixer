package com.vladsch.git.filecase.fixer;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.vladsch.git.filecase.fixer.GitFileFixerProjectRoots.GitRepoFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static com.vladsch.git.filecase.fixer.GitFixerConfiguration.getInstance;

public class GitFileCaseFixerHandlerWorker {
    private final Project myProject;
    private final Collection<Change> myChanges;
    private final int myFixerAction;

    private final List<GitRepoFile> myMismatchedUnmodifiedFiles = new ArrayList<>();
    private final List<GitRepoFile> myMismatchedModifiedFiles = new ArrayList<>();
    private final List<GitRepoFile> myMismatchedFixedFiles = new ArrayList<>();

    public GitFileCaseFixerHandlerWorker(@NotNull Project project, @NotNull Collection<Change> changes, int fixerAction) {
        myProject = project;
        myChanges = changes;
        myFixerAction = fixerAction;
    }

    public void execute() {
        GitFileFixerProjectRoots projectRoots = GitFileFixerProjectRoots.getInstance(myProject);
        HashSet<String> checkedFiles = new HashSet<>();

        for (Change change : myChanges) {
            ProgressManager.checkCanceled();
            if (change.getAfterRevision() == null) continue;
            FilePath afterFilePath = change.getAfterRevision().getFile();

            final VirtualFile afterFile = getFileWithRefresh(afterFilePath);
            if (afterFile != null && !afterFile.isDirectory()) {
                checkedFiles.add(afterFile.getPath());

                // check for file case
                GitRepoFile repoFile = projectRoots.getGitRepoFile(afterFile);
                if (repoFile != null && !repoFile.filePath.equals(repoFile.gitPath)) {
                    myMismatchedModifiedFiles.add(repoFile);
                }
            }
        }

        // now check all other files under VCS
        if (getInstance(myProject).CHECK_UNMODIFIED_FILES) {
            projectRoots.visitAllIndexFiles((repoFile) -> {
                if (!checkedFiles.contains(repoFile.fullPath)) {
                    if (!repoFile.gitPath.equals(repoFile.filePath)) {
                        myMismatchedUnmodifiedFiles.add(repoFile);
                    }
                }
            });
        }

        if (myFixerAction != GitFixerConfiguration.FIX_PROMPT && (!myMismatchedUnmodifiedFiles.isEmpty() || !myMismatchedModifiedFiles.isEmpty())) {
            myMismatchedFixedFiles.addAll(myMismatchedModifiedFiles);
            myMismatchedFixedFiles.addAll(myMismatchedUnmodifiedFiles);

            // remove git paths then add file paths to change git case
            if (myFixerAction == GitFixerConfiguration.FIX_GIT) {
                // remove then add, combine them by git repo and convert in one shot
                GitFileFixerProjectRoots.fixGitFileCase(myMismatchedFixedFiles);
            } else if (myFixerAction == GitFixerConfiguration.FIX_FILE_SYSTEM) {
                // rename file
                GitFileFixerProjectRoots.fixFileSystemCase(myMismatchedFixedFiles);
            }
        }
    }

    @Nullable
    private static VirtualFile getFileWithRefresh(@NotNull FilePath filePath) {
        VirtualFile file = filePath.getVirtualFile();
        if (file == null) {
            file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(filePath.getIOFile());
        }
        return file;
    }

    public List<GitRepoFile> getMismatchedFixedFiles() {
        return myMismatchedFixedFiles;
    }

    public List<GitRepoFile> getMismatchedUnmodifiedFiles() {
        return myMismatchedUnmodifiedFiles;
    }

    public List<GitRepoFile> getMismatchedModifiedFiles() {
        return myMismatchedModifiedFiles;
    }
}
