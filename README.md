# Git File Case Fixer Plugin

**<span style="color:#30A0D8;">Git file case mismatch fixer plugin</span> for JetBrains IDEs**

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
