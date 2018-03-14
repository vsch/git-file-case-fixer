# Git File Case Fixer Plugin

**<span style="color:#30A0D8;">Git file case mismatch fixer plugin</span> for JetBrains IDEs**

Download it from the IDE or https://plugins.jetbrains.com/plugin/10533-git-file-case-fixer

Detects and optionally fixes file case mismatch between git and the file system

Adds **Before Commit** check for file case mismatches between git and the file system
with corrective actions:

* Change git file case to match file system
* Change file system case to match git

Options:

* Select scope:
  * All files
  * only Modified files
* Select action to take on mismatch:
  * Ask: show prompt to decide what to do
  * Fix git case: change git to match file system case
  * Fix file case: change file system case to match git

## Screenshots

Before Commit Checking: 

![ScreenShot_CommitDialog.png](assets/images/ScreenShot_CommitDialog.png)

If `fix:` action above is `Ask` and there are files with mismatched case in git:

![ScreenShot_CommitMismatchFound.png](assets/images/ScreenShot_CommitMismatchFound.png)  

Via Review button above or from `Version Control` tool window button
![vcs-tool-button](assets/images/vcs-tool-button.png):

![ScreenShot_ShowMismatchesDialog.png](assets/images/ScreenShot_ShowMismatchesDialog.png)

