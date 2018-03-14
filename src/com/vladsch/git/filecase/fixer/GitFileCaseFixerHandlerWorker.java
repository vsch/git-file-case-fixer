package com.vladsch.git.filecase.fixer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.vladsch.git.filecase.fixer.GitFileFixerProjectRoots.GitRepoFile;
import com.vladsch.git.filecase.fixer.GitFileFixerProjectRoots.GitRepoFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.vladsch.git.filecase.fixer.GitFixerConfiguration.getInstance;

public class GitFileCaseFixerHandlerWorker {
    private final static Logger LOG = Logger.getInstance("com.vladsch.git.filecase.fixer");
    private static final int GIT_MAX_PARAMS = 20; // how many file paths to pass to git mv at one time

    private final Project myProject;
    private final Collection<Change> myChanges;
    private final int myFixerAction;

    private final List<GitRepoFile> myMismatchedUnmodifiedFiles = new ArrayList<>();
    private final List<GitRepoFile> myMismatchedModifiedFiles = new ArrayList<>();
    private final List<GitRepoFile> myMismatchedFixedFiles = new ArrayList<>();

    public GitFileCaseFixerHandlerWorker(@NotNull Project project, @NotNull Collection<Change> changes, @Nullable int fixerAction) {
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

            checkedFiles.add(afterFilePath.getPath());

            final VirtualFile afterFile = getFileWithRefresh(afterFilePath);
            if (afterFile != null && !afterFile.isDirectory()) {
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

            myMismatchedUnmodifiedFiles.clear();
            myMismatchedModifiedFiles.clear();

            // remove then add
            if (myFixerAction == GitFixerConfiguration.FIX_MATCH_FILE_SYSTEM) {
                // remove then add, combine them by git repo and convert in one shot
                HashMap<GitRepoFiles, ArrayList<GitRepoFile>> repoFileMap = new HashMap<>();

                for (GitRepoFile repoFile : myMismatchedFixedFiles) {
                    GitRepoFiles gitRepoFiles = repoFile.gitRepo;
                    ArrayList<GitRepoFile> repoFileList = repoFileMap.computeIfAbsent(gitRepoFiles, repoFiles -> new ArrayList<>());
                    repoFileList.add(repoFile);
                }

                for (GitRepoFiles gitRepoFiles : repoFileMap.keySet()) {
                    ArrayList<GitRepoFile> repoFiles = repoFileMap.get(gitRepoFiles);
                    ArrayList<String> gitPaths = new ArrayList<>(repoFiles.size());
                    ArrayList<String> filePaths = new ArrayList<>(repoFiles.size());

                    for (GitRepoFile repoFile : repoFiles) {
                        gitPaths.add(repoFile.gitPath);
                        filePaths.add(repoFile.filePath);
                    }

                    int iMax = repoFiles.size();
                    for (int i = 0; i < iMax; i += GIT_MAX_PARAMS) {
                        int nextI = i + GIT_MAX_PARAMS > iMax ? iMax : i + GIT_MAX_PARAMS;
                        GitRepoFile.matchFileSystem(gitRepoFiles, gitPaths.subList(i, nextI), filePaths.subList(i, nextI));
                    }
                }
            } else if (myFixerAction == GitFixerConfiguration.FIX_MATCH_GIT) {
                // rename file
                ApplicationManager.getApplication().invokeLater(() -> {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        for (GitRepoFile repoFile : myMismatchedFixedFiles) {
                            assert repoFile.gitRepo.myRepoRoot != null;
                            GitRepoFile.matchGit(repoFile.gitRepo, repoFile.filePath, repoFile.gitPath, repoFile.fullPath);
                            repoFile.matchGit();
                        }
                    });
                });
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
