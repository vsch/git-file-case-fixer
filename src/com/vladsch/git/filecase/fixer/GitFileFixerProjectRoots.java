package com.vladsch.git.filecase.fixer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GitFileFixerProjectRoots extends AbstractProjectComponent implements DumbAware {
    @NotNull final ProjectLevelVcsManager projectLevelVcsManager;
    @NotNull final static GitRepoFiles NULL_GIT_REPO_FILES = new GitRepoFiles();

    static final Logger LOG = Logger.getInstance("com.vladsch.git.filecase.fixer");

    @Nullable
    private MessageBusConnection messageBus;

    @NotNull Collection<GitRepository> vcsRoots = new ArrayList<>();
    @NotNull ArrayList<GitRepoFiles> vcsGitFilesList = new ArrayList<>();
    HashMap<String, GitRepoFiles> gitPathMap = new HashMap<>();  // file path (lowercase) to Map<git path lowercase, git path>

    @NotNull
    public static GitFileFixerProjectRoots getInstance(@NotNull final Project project) {
        return project.getComponent(GitFileFixerProjectRoots.class);
    }

    @NotNull
    static Set<String> gitFiles(@NotNull Project project, @NotNull VirtualFile root) throws VcsException {
        GitLineHandler handler = new GitLineHandler(project, root, GitCommand.LS_FILES);
        handler.setSilent(true);
        //handler.addParameters("--ignored", "--others", "--exclude-standard");
        handler.endOptions();
        //handler.addParameters(paths);
        String output = Git.getInstance().runCommand(handler).getOutputOrThrow();

        Set<String> nonIgnoredFiles = new HashSet<>(Arrays.asList(StringUtil.splitByLines(output)));
        return nonIgnoredFiles;
    }

    static class GitRepoFile {
        final GitRepoFiles gitRepo;
        final String fullPath;
        final String gitPath;
        final String filePath;

        public GitRepoFile(final GitRepoFiles repoFiles, final String fullPath, final String gitPath, final String filePath) {
            this.gitRepo = repoFiles;
            this.fullPath = fullPath;
            this.gitPath = gitPath;
            this.filePath = filePath;
        }

        public void matchFileSystem() {
            if (!gitPath.equals(filePath)) {
                assert gitRepo.myRepoRoot != null;
                assert gitRepo.myProject != null;
                matchFileSystem(gitRepo, Collections.singletonList(gitPath), Collections.singletonList(filePath));
            }
        }

        static void matchFileSystem(final GitRepoFiles gitRepo, final List<String> gitPaths, final List<String> filePaths) {
            GitLineHandler rmHandler = new GitLineHandler(gitRepo.myProject, gitRepo.myRepoRoot, GitCommand.RM);
            rmHandler.addParameters("--cached");
            rmHandler.endOptions();
            rmHandler.addParameters(gitPaths);
            try {
                Git.getInstance().runCommand(rmHandler).getOutputOrThrow();
                GitLineHandler addHandler = new GitLineHandler(gitRepo.myProject, gitRepo.myRepoRoot, GitCommand.ADD);
                addHandler.addParameters("--ignore-errors");
                addHandler.endOptions();
                addHandler.addParameters(filePaths);
                Git.getInstance().runCommand(addHandler).getOutputOrThrow();
            } catch (VcsException e) {
                e.printStackTrace();
            }
        }

        public void matchGit() {
            if (!gitPath.equals(filePath)) {
                assert gitRepo.myRepoRoot != null;
                ApplicationManager.getApplication().invokeLater(() -> {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        matchGit(gitRepo, filePath, gitPath, fullPath);
                    });
                });
            }
        }

        static void matchGit(final GitRepoFiles gitRepo, final String filePath, final String gitPath, final String fullPath) {
            VirtualFile file = gitRepo.myRepoRoot.findFileByRelativePath(filePath);

            if (file != null) {
                try {
                    new WriteCommandAction.Simple(gitRepo.myProject) {
                        @Override
                        protected void run() throws Throwable {
                            // TODO: rename the parent directories that do not match also
                            File gitFile = new File(gitPath);
                            try {
                                file.rename(this, gitFile.getName());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }.run();
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
        super(project);
        this.projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);
    }

    /**
     * Invoked when the project corresponding to this component instance is opened.<p> Note that components may be
     * created for even unopened projects and this method can be never invoked for a particular component instance (for
     * example for default project).
     */
    @Override
    public void projectOpened() {
        messageBus = myProject.getMessageBus().connect();

        messageBus.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, () -> {
            vcsRoots.clear();
            List repositories = GitRepositoryManager.getInstance(myProject).getRepositories();
            for (Object repository : repositories) {
                if (repository instanceof GitRepository) {
                    vcsRoots.add((GitRepository) repository);
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
                VirtualFile virtualFile = rootDir.findFileByRelativePath(gitPath);
                if (virtualFile != null) {
                    String fullPath = virtualFile.getPath();
                    String filePath = fullPath.substring(rootPrefix.length());
                    fileVisitor.consume(new GitRepoFile(repoFiles, fullPath, gitPath, filePath));
                }
            }
        }
    }

    @Nullable
    GitRepoFile getGitRepoFile(VirtualFile file) {
        // find the VcsRoot for this file and then get its index entry
        GitRepoFiles repositoryPathMap = null;

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

            // lets create a jGit repository for it
            if (repositoryPathMap == null) {
                repositoryPathMap = NULL_GIT_REPO_FILES;
            }

            gitPathMap.put(fileDir, repositoryPathMap);

            // get the file's git path if not a null repo
            if (repositoryPathMap != NULL_GIT_REPO_FILES) {
                String fileGitPath = file.getPath().toLowerCase().substring(repositoryPathMap.myRepoPrefix.length());
                String gitFilePath = repositoryPathMap.myIndexFiles.get(fileGitPath);
                return new GitRepoFile(repositoryPathMap, file.getPath(), gitFilePath, fileGitPath);
            }
        }

        return null;
    }

    void initializeGitRepoFilesList() {
        GitRepoFiles repositoryPathMap;

        if (vcsGitFilesList.isEmpty()) {
            for (GitRepository root : vcsRoots) {
                VirtualFile rootDir = root.getRoot();
                repositoryPathMap = new GitRepoFiles(myProject, root.getRoot());
                vcsGitFilesList.add(repositoryPathMap);
                repositoryPathMap.loadIndexFiles();
            }

            // sort by longest prefix
            vcsGitFilesList.sort((o1, o2) -> -Comparing.compare(o1.myRepoPrefix.length(), o2.myRepoPrefix.length()));
        }
    }

    /**
     * Invoked when the project corresponding to this component instance is closed.<p> Note that components may be
     * created for even unopened projects and this method can be never invoked for a particular component instance (for
     * example for default project).
     */
    @Override
    public void projectClosed() {
        if (messageBus != null) {
            messageBus.disconnect();
            messageBus = null;
        }
    }

    @Override
    public void disposeComponent() {
        super.disposeComponent();
    }

    /**
     * Unique name of this component. If there is another component with the same name or name is null internal
     * assertion will occur.
     *
     * @return the name of this component
     */
    @NonNls
    @NotNull
    @Override
    public String getComponentName() {
        return "GitFileCaseFixer.ProjectRootsComponent";
    }
}
