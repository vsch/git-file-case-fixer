package com.vladsch.git.filecase.fixer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.intellij.vcsUtil.VcsFileUtil.FILE_PATH_LIMIT;

public class GitFileFixerProjectRoots implements Disposable, DumbAware {
    static final Logger LOG = Logger.getInstance("com.vladsch.git.filecase.fixer");

    @NotNull final ProjectLevelVcsManager projectLevelVcsManager;
    @NotNull final static GitRepoFiles NULL_GIT_REPO_FILES = new GitRepoFiles();

    final private Project myProject;

    @NotNull Collection<GitRepository> vcsRoots = new ArrayList<>();
    @NotNull ArrayList<GitRepoFiles> vcsGitFilesList = new ArrayList<>();
    HashMap<String, GitRepoFiles> gitPathMap = new HashMap<>();  // file path (lowercase) to Map<git path lowercase, git path>

    @NotNull
    public static GitFileFixerProjectRoots getInstance(@NotNull Project project) {
        return project.getService(GitFileFixerProjectRoots.class);
    }

    static Git getGitInstance() {
        return ApplicationManager.getApplication().getService(git4idea.commands.Git.class);
    }

    @NotNull
    static Set<String> gitFiles(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
        GitLineHandler handler = new GitLineHandler(project, root, GitCommand.LS_FILES);
        handler.setSilent(true);
        //handler.addParameters("--ignored", "--others", "--exclude-standard");
        handler.endOptions();
        //handler.addParameters(paths);
        String output = "";

        Future<Git> future = PooledThreadExecutor.INSTANCE.submit(GitFileFixerProjectRoots::getGitInstance);
        Git gitInstance = null;
        try {
            gitInstance = future.get();
        } catch (InterruptedException | ExecutionException ignored) {
        }

        if (gitInstance != null) {
            output = StringUtil.join(gitInstance.runCommand(handler).getOutput(), "\n");
        }
        return new HashSet<>(Arrays.asList(StringUtil.splitByLines(output)));
    }

    public static void fixFileSystemCase(final List<GitRepoFile> fixFileCaseList) {
        ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
            for (GitRepoFile repoFile : fixFileCaseList) {
                assert repoFile.gitRepo.myRepoRoot != null;
                repoFile.matchGit();
            }
        }));
    }

    public static void fixGitFileCase(final List<GitRepoFile> fixGitList) {
        // remove then add, combine them by git repo and convert in one shot
        HashMap<GitRepoFiles, ArrayList<GitRepoFile>> repoFileMap = new HashMap<>();

        for (GitRepoFile repoFile : fixGitList) {
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
            int lastParam = 0;
            int gitPathsLength = 0;
            int filePathsLength = 0;
            for (int i = 0; i < iMax; i++) {
                gitPathsLength += gitPaths.get(i).length();
                filePathsLength += filePaths.get(i).length();

                if (gitPathsLength > FILE_PATH_LIMIT || filePathsLength > FILE_PATH_LIMIT) {
                    // process lastParam to i-1
                    GitRepoFile.matchFileSystem(gitRepoFiles, gitPaths.subList(lastParam, i - 1), filePaths.subList(lastParam, i - 1));

                    lastParam = i - 1;
                    gitPathsLength = gitPaths.get(i).length();
                    filePathsLength = filePaths.get(i).length();
                }
            }

            if (lastParam < iMax) {
                // process last params
                GitRepoFile.matchFileSystem(gitRepoFiles, gitPaths.subList(lastParam, iMax), filePaths.subList(lastParam, iMax));
            }
        }
    }

    static class GitRepoFile {
        final GitRepoFiles gitRepo;
        final String fullPath;
        final String gitPath;
        final String filePath;
        int fixAction;

        public GitRepoFile(final GitRepoFiles repoFiles, final String fullPath, final String gitPath, final String filePath) {
            gitRepo = repoFiles;
            this.fullPath = fullPath;
            this.gitPath = gitPath;
            this.filePath = filePath;
            fixAction = GitFixerConfiguration.FIX_PROMPT;
        }

        static void matchFileSystem(final GitRepoFiles gitRepo, final List<String> gitPaths, final List<String> filePaths) {
            assert gitRepo.myProject != null;
            assert gitRepo.myRepoRoot != null;
            GitLineHandler rmHandler = new GitLineHandler(gitRepo.myProject, gitRepo.myRepoRoot, GitCommand.RM);
            rmHandler.addParameters("--cached");
            rmHandler.endOptions();
            rmHandler.addParameters(gitPaths);
            try {
                getGitInstance().runCommand(rmHandler).getOutput();
                GitLineHandler addHandler = new GitLineHandler(gitRepo.myProject, gitRepo.myRepoRoot, GitCommand.ADD);
                addHandler.addParameters("--ignore-errors");
                addHandler.endOptions();
                addHandler.addParameters(filePaths);
                getGitInstance().runCommand(addHandler).getOutput();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void matchGit() {
            if (!gitPath.equals(filePath)) {
                assert gitRepo.myRepoRoot != null;
                ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> matchGit(gitRepo, filePath, gitPath, fullPath)));
            }
        }

        static void fixFileCase(@Nullable VirtualFile file, @Nullable File path) throws IOException {
            if (file != null && path != null) {
                String filePath = file.getPath();
                String pathPath = path.getPath();
                if (filePath.toLowerCase().endsWith(pathPath.toLowerCase())) {
                    filePath = filePath.substring(filePath.length() - pathPath.length());

                    if (filePath.equalsIgnoreCase(pathPath) && !filePath.equals(pathPath)) {
                        // rename file and possibly parent directories
                        if (!file.getName().equals(path.getName())) {
                            // rename file
                            file.rename(file, path.getName());
                        }

                        fixFileCase(file.getParent(), path.getParentFile());
                    }
                }
            }
        }

        static void matchGit(final GitRepoFiles gitRepo, final String filePath, final String gitPath, final String fullPath) {
            assert gitRepo.myRepoRoot != null;
            VirtualFile file = gitRepo.myRepoRoot.findFileByRelativePath(filePath);

            if (file != null) {
                try {
                    WriteCommandAction.runWriteCommandAction(gitRepo.myProject, Bundle.message("git.filecase.fixer.rename.mismatched.file"), null, () -> {
                        File gitFile = new File(gitPath);
                        try {
                            // rename the parent directories that do not match also
                            fixFileCase(file, gitFile);
                            VcsDirtyScopeManager.getInstance(gitRepo.myProject).fileDirty(file);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }
            } else {
                LOG.error(String.format("Mismatched file %s was not found by relative path %s from %s", fullPath, filePath, gitRepo.myRepoRoot.getPath()));
            }
        }
    }

    static class GitRepoFiles {
        final VirtualFile myRepoRoot;
        final Project myProject;
        final Map<String, String> myIndexFiles;
        final String myRepoPrefix;

        GitRepoFiles() {
            myIndexFiles = new HashMap<>();
            myRepoRoot = null;
            myProject = null;
            myRepoPrefix = "";
        }

        public GitRepoFiles(final Project project, final VirtualFile repoRoot) {
            myProject = project;
            myRepoRoot = repoRoot;
            myIndexFiles = new HashMap<>();

            String absolutePath = repoRoot.getPath();
            if (absolutePath.endsWith("/.")) {
                absolutePath = absolutePath.substring(0, absolutePath.length() - "/.".length());
            }

            myRepoPrefix = absolutePath.toLowerCase() + "/";
        }

        void loadIndexFiles() {
            myIndexFiles.clear();

            try {
                Set<String> paths = gitFiles(myProject, myRepoRoot);
                for (String path : paths) {
                    myIndexFiles.put(path.toLowerCase(), path);
                }
            } catch (VcsException e) {
                e.printStackTrace();
            }
        }
    }

    public GitFileFixerProjectRoots(@NotNull final Project project) {
        myProject = project;
        projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);

        //noinspection ThisEscapedInObjectConstruction
        @Nullable MessageBusConnection messageBus = myProject.getMessageBus().connect(this);
        messageBus.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, () -> {
            vcsRoots.clear();
            List<GitRepository> repositories = myProject.getService(GitRepositoryManager.class).getRepositories();
            for (GitRepository repository : repositories) {
                if (repository != null) {
                    vcsRoots.add(repository);
                }
            }
            clearCaches();
        });
    }

    void clearCaches() {
        gitPathMap.clear();
        vcsGitFilesList.clear();
    }

    void visitAllIndexFiles(Consumer<GitRepoFile> fileVisitor) {
        initializeGitRepoFilesList();
        for (GitRepoFiles repoFiles : vcsGitFilesList) {
            VirtualFile rootDir = repoFiles.myRepoRoot;
            String rootPrefix = repoFiles.myRepoPrefix;

            for (String gitPath : repoFiles.myIndexFiles.values()) {
                try {
                    VirtualFile virtualFile = rootDir.findFileByRelativePath(gitPath);
                    if (virtualFile != null) {
                        String fullPath = virtualFile.getPath();
                        if (fullPath.length() >= rootPrefix.length()) {
                            String filePath = fullPath.substring(rootPrefix.length());
                            fileVisitor.consume(new GitRepoFile(repoFiles, fullPath, gitPath, filePath));
                        }
                    }
                } catch (InvalidVirtualFileAccessException ignored) {
                    //e.printStackTrace();
                }
            }
        }
    }

    @Nullable
    GitRepoFile getGitRepoFile(VirtualFile file) {
        // find the VcsRoot for this file and then get its index entry
        GitRepoFiles repositoryPathMap;

        String fileDir = file.getParent().getPath().toLowerCase() + "/";

        if (!file.getFileSystem().isCaseSensitive()) {
            repositoryPathMap = gitPathMap.get(fileDir);
            if (repositoryPathMap == null) {
                // go through the roots
                initializeGitRepoFilesList();
            }

            for (GitRepoFiles gitRepoFiles : vcsGitFilesList) {
                if (fileDir.startsWith(gitRepoFiles.myRepoPrefix)) {
                    // the one
                    repositoryPathMap = gitRepoFiles;
                    break;
                }
            }

            // let's create a jGit repository for it
            if (repositoryPathMap == null) {
                repositoryPathMap = NULL_GIT_REPO_FILES;
            }

            gitPathMap.put(fileDir, repositoryPathMap);

            // get the file's git path if not a null repo
            if (repositoryPathMap != NULL_GIT_REPO_FILES) {
                String fileGitPath = file.getPath().substring(repositoryPathMap.myRepoPrefix.length());
                String gitFilePath = repositoryPathMap.myIndexFiles.get(fileGitPath.toLowerCase());
                return new GitRepoFile(repositoryPathMap, file.getPath(), gitFilePath, fileGitPath);
            }
        }

        return null;
    }

    void initializeGitRepoFilesList() {
        GitRepoFiles repositoryPathMap;

        if (vcsGitFilesList.isEmpty()) {
            for (GitRepository root : vcsRoots) {
                repositoryPathMap = new GitRepoFiles(myProject, root.getRoot());
                vcsGitFilesList.add(repositoryPathMap);
                repositoryPathMap.loadIndexFiles();
            }

            // sort by longest prefix
            vcsGitFilesList.sort(Comparator.comparingInt(o -> -o.myRepoPrefix.length()));
        }
    }

    @Override
    public void dispose() {

    }
}
