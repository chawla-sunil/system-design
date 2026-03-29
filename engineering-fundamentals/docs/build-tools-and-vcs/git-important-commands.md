# 🔀 Git Important Commands — Senior Engineer's Complete Reference

> Everything you need to know about Git for interviews and day-to-day work.  
> From basics to advanced — with **real examples** for every command.

---

## Table of Contents

1. [Git Basics — Config, Init, Clone](#1-git-basics--config-init-clone)
2. [Staging & Committing](#2-staging--committing)
3. [Branching & Switching](#3-branching--switching)
4. [Merging — Fast-Forward, 3-Way, Conflicts](#4-merging--fast-forward-3-way-conflicts)
5. [Rebasing — The Interview Favorite](#5-rebasing--the-interview-favorite)
6. [Interactive Rebase — Rewriting History](#6-interactive-rebase--rewriting-history)
7. [Cherry-Pick — Steal a Commit](#7-cherry-pick--steal-a-commit)
8. [Stashing — Save Work Without Committing](#8-stashing--save-work-without-committing)
9. [Resetting — Undo Things](#9-resetting--undo-things)
10. [Reverting — Safe Undo](#10-reverting--safe-undo)
11. [Log & History — Finding Things](#11-log--history--finding-things)
12. [Diff — What Changed?](#12-diff--what-changed)
13. [Remote Operations — Push, Pull, Fetch](#13-remote-operations--push-pull-fetch)
14. [Tags — Marking Releases](#14-tags--marking-releases)
15. [Bisect — Find the Bug](#15-bisect--find-the-bug)
16. [Reflog — Git's Safety Net](#16-reflog--gits-safety-net)
17. [Blame & Shortlog](#17-blame--shortlog)
18. [Clean — Remove Untracked Files](#18-clean--remove-untracked-files)
19. [Submodules](#19-submodules)
20. [Worktrees — Multiple Working Directories](#20-worktrees--multiple-working-directories)
21. [Aliases — Be Fast](#21-aliases--be-fast)
22. [.gitignore — What to Never Commit](#22-gitignore--what-to-never-commit)
23. [Common Interview Scenarios](#23-common-interview-scenarios)
24. [Quick Reference Table](#24-quick-reference-table)

---

## 1. Git Basics — Config, Init, Clone

### Git Config — Set Up Your Identity

```bash
# Set your name and email (required before first commit)
git config --global user.name "Sunil Chawla"
git config --global user.email "sunil@example.com"

# See all config
git config --list

# Set default branch name to main
git config --global init.defaultBranch main

# Set default editor
git config --global core.editor "vim"

# Config scopes (interview question!)
# --system   → /etc/gitconfig        (all users on machine)
# --global   → ~/.gitconfig           (current user, all repos)
# --local    → .git/config            (current repo only — highest priority)
```

**Interview Q:** *"What happens if the same key is set at multiple levels?"*  
**A:** Local overrides global, global overrides system. Most specific wins.

### Git Init — Create a New Repo

```bash
# Create a new repository
mkdir my-project && cd my-project
git init

# What it does: creates a .git/ directory with:
# .git/
# ├── HEAD           ← points to current branch
# ├── config         ← local config
# ├── objects/       ← stores all blobs, trees, commits
# ├── refs/          ← stores branch and tag pointers
# └── hooks/         ← scripts that run on events
```

### Git Clone — Copy a Remote Repo

```bash
# Clone via HTTPS
git clone https://github.com/user/repo.git

# Clone via SSH
git clone git@github.com:user/repo.git

# Clone into a specific directory
git clone https://github.com/user/repo.git my-folder

# Shallow clone (only latest commit — fast for big repos)
git clone --depth 1 https://github.com/user/repo.git

# Clone a specific branch
git clone -b develop https://github.com/user/repo.git
```

---

## 2. Staging & Committing

### The Three Areas of Git

```
Working Directory  →  Staging Area (Index)  →  Repository (.git)
   (your files)         (git add)                (git commit)
```

**Interview Q:** *"What is the staging area?"*  
**A:** It's a buffer between your working directory and the repository. It lets you **selectively choose** what goes into the next commit.

### Git Add — Stage Changes

```bash
# Stage a specific file
git add README.md

# Stage all changes in current directory
git add .

# Stage all changes in entire repo
git add -A

# Stage only tracked files (won't add new files)
git add -u

# Interactive staging — pick hunks to stage
git add -p
# This shows each change and asks: Stage this hunk? [y,n,q,a,d,s,e,?]
# y = yes, n = no, s = split into smaller hunks, e = edit manually
```

**Pro tip:** `git add -p` is how senior engineers commit. It lets you review every change before staging.

### Git Commit — Save a Snapshot

```bash
# Commit with a message
git commit -m "Add user authentication module"

# Stage tracked files + commit in one step
git commit -am "Fix null pointer in UserService"

# Amend the last commit (change message or add files)
git add forgotten-file.java
git commit --amend -m "Add user authentication module with validation"

# Amend without changing the message
git commit --amend --no-edit

# Empty commit (useful for triggering CI)
git commit --allow-empty -m "Trigger CI build"
```

### Commit Message Best Practices

```
<type>: <short summary in imperative mood>

<optional body — explain WHY, not WHAT>

<optional footer — issue references>
```

**Example:**
```
feat: add rate limiting to API endpoints

Users were hitting the API too frequently causing DB overload.
Added a sliding window rate limiter with configurable thresholds.

Closes #142
```

**Common types:** `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `style`

---

## 3. Branching & Switching

### Why Branches?

Branches are **pointers to commits**. They are lightweight — creating a branch is just creating a 41-byte file.

```bash
# List all local branches
git branch

# List all branches (local + remote)
git branch -a

# List remote branches only
git branch -r

# Create a new branch
git branch feature/user-login

# Create and switch to it
git checkout -b feature/user-login
# OR (modern way, Git 2.23+)
git switch -c feature/user-login

# Switch to an existing branch
git checkout main
# OR
git switch main

# Rename a branch
git branch -m old-name new-name

# Rename current branch
git branch -m new-name

# Delete a branch (safe — won't delete unmerged)
git branch -d feature/user-login

# Force delete (even if unmerged)
git branch -D feature/user-login

# Delete a remote branch
git push origin --delete feature/user-login
```

### Visualizing Branches

```bash
# See branch history graphically
git log --oneline --graph --all

# Example output:
# * 3a1f2b4 (HEAD -> main) Merge feature/auth
# |\
# | * c9e8d7f (feature/auth) Add JWT validation
# | * b2a3c4d Add login endpoint
# |/
# * a1b2c3d Initial commit
```

---

## 4. Merging — Fast-Forward, 3-Way, Conflicts

### Fast-Forward Merge

When the target branch has no new commits, Git just moves the pointer forward.

```bash
# main hasn't changed since feature branched off
git checkout main
git merge feature/login

# Before:
# main:    A → B
# feature: A → B → C → D
#
# After (fast-forward):
# main:    A → B → C → D
```

### 3-Way Merge (Merge Commit)

When both branches have new commits, Git creates a **merge commit** with two parents.

```bash
git checkout main
git merge feature/login

# Before:
# main:    A → B → E
# feature: A → B → C → D
#
# After:
# main:    A → B → E → M (merge commit)
#               \     /
#                C → D
```

### No-Fast-Forward Merge

Force a merge commit even when fast-forward is possible (preserves branch history).

```bash
git merge --no-ff feature/login
```

### Handling Merge Conflicts

```bash
# Merge and hit a conflict
git merge feature/login
# CONFLICT (content): Merge conflict in UserService.java

# See which files have conflicts
git status

# The conflicted file will look like:
# <<<<<<< HEAD
# return userRepository.findById(id);
# =======
# return userRepository.findByIdOrThrow(id);
# >>>>>>> feature/login

# Fix the file manually, then:
git add UserService.java
git commit   # Git auto-generates merge message

# Abort a merge (go back to before you merged)
git merge --abort
```

**Interview Q:** *"How do you resolve a merge conflict?"*  
**A:** Open the conflicted file, look for `<<<<<<<` markers, decide which code to keep (or combine both), remove the markers, `git add` the resolved file, then `git commit`.

---

## 5. Rebasing — The Interview Favorite

### What is Rebase?

Rebase **replays** your commits on top of another branch. It rewrites history to create a **linear** commit history.

```bash
git checkout feature/login
git rebase main

# Before:
# main:    A → B → E
# feature: A → B → C → D
#
# After:
# main:    A → B → E
# feature: A → B → E → C' → D'
#
# C' and D' are NEW commits (different hashes) with the same changes
```

### Merge vs Rebase — When to Use What

| | Merge | Rebase |
|---|-------|--------|
| **History** | Non-linear (preserves branch topology) | Linear (clean, flat) |
| **Commit hashes** | Preserved | Changed (rewritten) |
| **Safe on shared branches?** | ✅ Yes | ❌ No — never rebase shared/public branches |
| **Use when** | Merging feature into main | Updating your feature branch with latest main |

### The Golden Rule of Rebasing

> **Never rebase commits that have been pushed to a public/shared branch.**

Rebase rewrites commit hashes. If others have those commits, you'll create duplicate history and chaos.

```bash
# ✅ Safe: rebase YOUR feature branch onto main
git checkout feature/my-work
git rebase main

# ❌ Dangerous: rebase main onto something
git checkout main
git rebase feature/something   # DON'T DO THIS
```

### Handling Rebase Conflicts

```bash
git rebase main
# CONFLICT! Git stops at the conflicting commit

# Fix the conflict in the file, then:
git add ConflictedFile.java
git rebase --continue

# To skip this commit entirely:
git rebase --skip

# To abort and go back to before rebase:
git rebase --abort
```

---

## 6. Interactive Rebase — Rewriting History

This is the **most powerful Git command** for cleaning up commits before pushing.

```bash
# Rebase last 4 commits interactively
git rebase -i HEAD~4
```

Git opens your editor with:

```
pick a1b2c3d Add user model
pick b2c3d4e Add user repository
pick c3d4e5f Fix typo in user model
pick d4e5f6g Add user service
```

### Available Commands

| Command | Short | What It Does |
|---------|-------|--------------|
| `pick` | `p` | Keep the commit as-is |
| `reword` | `r` | Keep the commit but change the message |
| `edit` | `e` | Stop at this commit to amend it |
| `squash` | `s` | Combine with previous commit (keep both messages) |
| `fixup` | `f` | Combine with previous commit (discard this message) |
| `drop` | `d` | Delete this commit entirely |
| `reorder` | — | Just reorder the lines to reorder commits |

### Example: Squash Messy Commits into One

```bash
git rebase -i HEAD~4

# Change the file to:
pick a1b2c3d Add user model
fixup c3d4e5f Fix typo in user model
pick b2c3d4e Add user repository
pick d4e5f6g Add user service

# Result: "Fix typo" is absorbed into "Add user model"
# Clean history with 3 meaningful commits instead of 4
```

### Example: Reword a Commit Message

```bash
git rebase -i HEAD~3

# Change pick to reword:
reword a1b2c3d Add usr model    ← typo in message
pick b2c3d4e Add user repository
pick c3d4e5f Add user service

# Git will stop and let you edit the message for that commit
```

### Example: Reorder Commits

```bash
git rebase -i HEAD~3

# Just reorder the lines:
pick c3d4e5f Add user service
pick a1b2c3d Add user model
pick b2c3d4e Add user repository
```

**Interview Q:** *"How would you clean up 10 messy commits before merging?"*  
**A:** Use `git rebase -i HEAD~10`, squash/fixup the WIP commits, reword unclear messages, and end up with a clean set of logical commits.

---

## 7. Cherry-Pick — Steal a Commit

Apply a **specific commit** from one branch to another without merging the whole branch.

```bash
# Apply commit abc1234 to current branch
git cherry-pick abc1234

# Cherry-pick multiple commits
git cherry-pick abc1234 def5678

# Cherry-pick a range of commits
git cherry-pick abc1234..xyz9876

# Cherry-pick without committing (just stage the changes)
git cherry-pick --no-commit abc1234

# If there's a conflict during cherry-pick:
git cherry-pick --continue    # after resolving
git cherry-pick --abort        # to cancel
```

### Real-World Use Case

```bash
# Scenario: A hotfix was committed on 'develop' but you need it on 'main' NOW
git checkout main
git cherry-pick 7f3a2b1    # the hotfix commit hash

# Now main has the fix without merging all of develop
```

**Interview Q:** *"When would you use cherry-pick?"*  
**A:** When you need a specific fix from one branch on another without a full merge — e.g., backporting a bugfix to a release branch, or extracting a single feature commit.

---

## 8. Stashing — Save Work Without Committing

Stash temporarily shelves your changes so you can work on something else.

```bash
# Stash all modified tracked files
git stash

# Stash with a descriptive message
git stash push -m "WIP: user validation logic"

# Stash including untracked files
git stash -u

# Stash including untracked + ignored files
git stash -a

# Stash only specific files
git stash push -m "Stash only config" -- config.yml

# List all stashes
git stash list
# stash@{0}: On feature/login: WIP: user validation logic
# stash@{1}: WIP on main: abc1234 Initial commit

# Apply the most recent stash (keeps it in stash list)
git stash apply

# Apply and remove from stash list
git stash pop

# Apply a specific stash
git stash apply stash@{1}

# View what's in a stash
git stash show stash@{0}
git stash show -p stash@{0}    # full diff

# Drop a specific stash
git stash drop stash@{1}

# Clear all stashes
git stash clear
```

### Real-World Use Case

```bash
# You're working on feature/login but need to fix a bug on main urgently

git stash push -m "WIP: login page half done"
git checkout main
git checkout -b hotfix/null-pointer
# ... fix the bug, commit, push ...
git checkout feature/login
git stash pop     # restore your work-in-progress
```

---

## 9. Resetting — Undo Things

`git reset` moves the HEAD (and optionally the staging area and working directory) to a different commit.

### Three Modes of Reset

```bash
# --soft: move HEAD only (staged + working dir unchanged)
git reset --soft HEAD~1
# Use case: undo commit but keep changes staged (ready to re-commit)

# --mixed (default): move HEAD + unstage changes (working dir unchanged)
git reset HEAD~1
# Use case: undo commit and unstage, but keep files modified

# --hard: move HEAD + unstage + discard all changes
git reset --hard HEAD~1
# Use case: completely throw away the last commit and all changes
# ⚠️ DANGEROUS — unrecoverable (unless you know reflog)
```

### Visual Comparison

```
                    HEAD    Staging    Working Dir
--soft HEAD~1       ✅ moved  unchanged  unchanged
--mixed HEAD~1      ✅ moved  ✅ reset   unchanged
--hard HEAD~1       ✅ moved  ✅ reset   ✅ reset
```

### Practical Examples

```bash
# "I committed too early, want to add more changes"
git reset --soft HEAD~1
# Now make more changes, then commit again

# "I accidentally staged a file"
git reset HEAD secret.env
# OR (modern way)
git restore --staged secret.env

# "Undo last 3 commits completely"
git reset --hard HEAD~3

# "Reset to match remote exactly"
git reset --hard origin/main
```

**Interview Q:** *"What's the difference between `reset --soft`, `--mixed`, and `--hard`?"*  
**A:** Soft keeps everything staged, mixed unstages but keeps files, hard discards everything. All three move HEAD.

---

## 10. Reverting — Safe Undo

`git revert` creates a **new commit** that undoes the changes of a previous commit. Safe for shared branches.

```bash
# Revert the last commit
git revert HEAD

# Revert a specific commit
git revert abc1234

# Revert without auto-committing
git revert --no-commit abc1234

# Revert a merge commit (must specify which parent to keep)
git revert -m 1 abc1234
# -m 1 = keep the first parent (usually main)
# -m 2 = keep the second parent (the merged branch)

# Revert multiple commits
git revert abc1234..def5678
```

### Reset vs Revert

| | `reset` | `revert` |
|---|---------|----------|
| **How** | Moves HEAD backward | Creates a new "undo" commit |
| **History** | Rewrites (removes commits) | Preserves (adds new commit) |
| **Safe for shared branches?** | ❌ No | ✅ Yes |
| **Use when** | Local cleanup | Undoing on public/shared branches |

**Interview Q:** *"A bad commit was pushed to main. How do you undo it?"*  
**A:** Use `git revert <commit-hash>`. Never use `git reset` on a shared branch because it rewrites history and breaks other developers' work.

---

## 11. Log & History — Finding Things

```bash
# Basic log
git log

# One-line compact log
git log --oneline

# Graphical log with branches
git log --oneline --graph --all --decorate

# Last 5 commits
git log -5

# Commits by a specific author
git log --author="Sunil"

# Commits containing a keyword in the message
git log --grep="authentication"

# Commits that changed a specific file
git log -- src/UserService.java

# Commits between two dates
git log --after="2025-01-01" --before="2025-06-30"

# Show what changed in each commit
git log -p

# Show stats (files changed, insertions, deletions)
git log --stat

# Find which commit introduced a specific string
git log -S "findUserById"
# This is called the "pickaxe" — finds commits that added/removed that string

# Pretty format (custom output)
git log --pretty=format:"%h - %an, %ar : %s"
# Output: a1b2c3d - Sunil, 2 days ago : Add user service

# Show commits on branch-a that are NOT on branch-b
git log branch-b..branch-a
```

---

## 12. Diff — What Changed?

```bash
# Changes in working directory (unstaged)
git diff

# Changes that are staged (ready to commit)
git diff --staged
# OR
git diff --cached

# Diff between two branches
git diff main..feature/login

# Diff a specific file
git diff -- src/UserService.java

# Diff between two commits
git diff abc1234 def5678

# Show only file names that changed
git diff --name-only main..feature/login

# Show file names + status (Added, Modified, Deleted)
git diff --name-status main..feature/login

# Word-level diff (useful for prose/docs)
git diff --word-diff

# Diff stats summary
git diff --stat main..feature/login
```

---

## 13. Remote Operations — Push, Pull, Fetch

### Understanding Remotes

```bash
# List remotes
git remote -v
# origin  https://github.com/user/repo.git (fetch)
# origin  https://github.com/user/repo.git (push)

# Add a remote
git remote add upstream https://github.com/original/repo.git

# Remove a remote
git remote remove upstream

# Rename a remote
git remote rename origin github
```

### Fetch — Download Without Merging

```bash
# Fetch all branches from origin
git fetch origin

# Fetch a specific branch
git fetch origin main

# Fetch from all remotes
git fetch --all

# Fetch and prune deleted remote branches
git fetch --prune
```

**Interview Q:** *"What's the difference between fetch and pull?"*  
**A:** `fetch` downloads changes but doesn't modify your working directory. `pull` = `fetch` + `merge` (or `rebase` if configured).

### Pull — Fetch + Merge

```bash
# Pull (fetch + merge)
git pull origin main

# Pull with rebase instead of merge (cleaner history)
git pull --rebase origin main

# Set pull to always rebase (recommended)
git config --global pull.rebase true
```

### Push — Upload to Remote

```bash
# Push current branch
git push origin main

# Push and set upstream (first push of a new branch)
git push -u origin feature/login
# After this, you can just do: git push

# Push all branches
git push --all

# Push tags
git push --tags

# Force push (DANGEROUS — overwrites remote history)
git push --force origin feature/login

# Force push with lease (safer — fails if someone else pushed)
git push --force-with-lease origin feature/login
```

**Interview Q:** *"When would you use `--force-with-lease` over `--force`?"*  
**A:** Always. `--force-with-lease` checks that the remote hasn't been updated by someone else. If it has, it fails instead of overwriting their work. Use it after rebase or amend.

---

## 14. Tags — Marking Releases

Tags are **immutable** pointers to specific commits — used for releases.

```bash
# Lightweight tag (just a pointer)
git tag v1.0.0

# Annotated tag (has message, author, date — preferred)
git tag -a v1.0.0 -m "Release version 1.0.0"

# Tag a specific commit
git tag -a v1.0.0 abc1234 -m "Release 1.0.0"

# List tags
git tag
git tag -l "v1.*"    # filter by pattern

# Show tag details
git show v1.0.0

# Push a specific tag
git push origin v1.0.0

# Push all tags
git push origin --tags

# Delete a local tag
git tag -d v1.0.0

# Delete a remote tag
git push origin --delete v1.0.0
```

**Interview Q:** *"Difference between lightweight and annotated tags?"*  
**A:** Lightweight is just a pointer (like a branch that doesn't move). Annotated stores author, date, message, and can be signed — use annotated for releases.

---

## 15. Bisect — Find the Bug

`git bisect` uses **binary search** to find the commit that introduced a bug. It's incredibly efficient.

```bash
# Start bisecting
git bisect start

# Mark current commit as bad (has the bug)
git bisect bad

# Mark a known good commit (didn't have the bug)
git bisect good abc1234

# Git checks out the midpoint commit. Test it, then:
git bisect bad     # if this commit has the bug
# OR
git bisect good    # if this commit is clean

# Repeat until Git finds the exact commit:
# abc1234 is the first bad commit

# Done — go back to your branch
git bisect reset
```

### Automated Bisect (with a test script)

```bash
# Automatically run a test script on each commit
git bisect start
git bisect bad HEAD
git bisect good abc1234
git bisect run mvn test -pl user-service
# OR
git bisect run ./test-script.sh

# Git runs the script on each midpoint:
# Exit code 0 = good, non-zero = bad
```

### How Efficient Is It?

If you have **1000 commits** to search, bisect finds the bad one in **~10 steps** (log₂ 1000 ≈ 10).

**Interview Q:** *"There's a bug that wasn't there a week ago. How do you find which commit caused it?"*  
**A:** Use `git bisect`. Mark the current commit as bad, mark last known good commit, and Git binary-searches through commits. For 1000 commits, it takes only ~10 steps.

---

## 16. Reflog — Git's Safety Net

Reflog records **every time HEAD moves** — even for operations that "destroy" history (reset, rebase, amend).

```bash
# View reflog
git reflog
# a1b2c3d (HEAD -> main) HEAD@{0}: commit: Add feature
# b2c3d4e HEAD@{1}: reset: moving to HEAD~1
# c3d4e5f HEAD@{2}: commit: Secret commit (oops, reset this)

# Recover a "lost" commit after hard reset
git reset --hard HEAD~3    # oh no, lost 3 commits!
git reflog                 # find the commit hash before reset
git reset --hard HEAD@{1}  # go back to before the reset

# Recover a deleted branch
git branch -D feature/experiment    # deleted!
git reflog                          # find the last commit of that branch
git checkout -b feature/experiment HEAD@{5}

# Reflog for a specific branch
git reflog show feature/login
```

### Reflog Expiry

```bash
# Reflog entries expire after 90 days (default)
# Unreachable entries expire after 30 days

# Run garbage collection (reflog entries are cleaned up)
git gc
```

**Interview Q:** *"I did `git reset --hard` and lost my commits. Can I get them back?"*  
**A:** Yes! Use `git reflog` to find the commit hash before the reset, then `git reset --hard <hash>` to recover. Reflog keeps history for ~90 days.

---

## 17. Blame & Shortlog

### Git Blame — Who Wrote This Line?

```bash
# See who last modified each line
git blame src/UserService.java

# Output:
# a1b2c3d (Sunil 2025-03-10 14:23:01 +0530  1) public class UserService {
# b2c3d4e (Alice 2025-03-11 09:10:22 +0530  2)     private final UserRepo repo;

# Blame a specific range of lines
git blame -L 10,20 src/UserService.java

# Ignore whitespace changes
git blame -w src/UserService.java

# Show the commit that MOVED or COPIED lines (-C flag)
git blame -C src/UserService.java
```

### Git Shortlog — Contribution Summary

```bash
# Summary of commits by author
git shortlog -sn
#  42  Sunil
#  18  Alice
#  7   Bob

# Include all branches
git shortlog -sn --all
```

---

## 18. Clean — Remove Untracked Files

```bash
# Dry run — see what WOULD be deleted
git clean -n

# Remove untracked files
git clean -f

# Remove untracked files AND directories
git clean -fd

# Remove ignored files too
git clean -fX

# Remove everything (untracked + ignored)
git clean -fdx

# Interactive mode
git clean -i
```

**⚠️ Warning:** `git clean` permanently deletes files. Always run `-n` (dry run) first!

---

## 19. Submodules

Submodules let you include another Git repo inside your repo.

```bash
# Add a submodule
git submodule add https://github.com/lib/utils.git libs/utils

# Clone a repo with submodules
git clone --recurse-submodules https://github.com/user/repo.git

# If you already cloned without submodules:
git submodule update --init --recursive

# Update submodules to latest
git submodule update --remote

# Remove a submodule
git submodule deinit libs/utils
git rm libs/utils
rm -rf .git/modules/libs/utils
```

---

## 20. Worktrees — Multiple Working Directories

Work on multiple branches simultaneously without stashing.

```bash
# Add a new worktree for a branch
git worktree add ../hotfix-branch hotfix/urgent-fix

# List worktrees
git worktree list

# Remove a worktree
git worktree remove ../hotfix-branch
```

### Real-World Use Case

```bash
# You're coding on feature/login but need to review a PR on feature/auth
git worktree add ../review-auth feature/auth
cd ../review-auth
# Review the code, run tests, etc.
cd ../my-project
git worktree remove ../review-auth
```

---

## 21. Aliases — Be Fast

```bash
# Set up common aliases
git config --global alias.co checkout
git config --global alias.br branch
git config --global alias.ci commit
git config --global alias.st status
git config --global alias.lg "log --oneline --graph --all --decorate"
git config --global alias.last "log -1 HEAD --stat"
git config --global alias.unstage "reset HEAD --"
git config --global alias.undo "reset --soft HEAD~1"
git config --global alias.amend "commit --amend --no-edit"

# Usage
git co main          # checkout main
git lg               # beautiful graph log
git undo             # undo last commit (keep changes staged)
git amend            # amend last commit without changing message
```

---

## 22. .gitignore — What to Never Commit

```gitignore
# Compiled output
target/
build/
*.class
*.jar

# IDE files
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# OS files
.DS_Store
Thumbs.db

# Environment & secrets
.env
*.pem
*.key

# Logs
*.log
logs/

# Dependencies
node_modules/
```

### Useful .gitignore Commands

```bash
# Check why a file is ignored
git check-ignore -v target/classes/Main.class
# .gitignore:1:target/    target/classes/Main.class

# Stop tracking a file that's already committed
git rm --cached secret.env
echo "secret.env" >> .gitignore
git commit -m "Remove secret.env from tracking"

# Global gitignore (for all repos)
git config --global core.excludesFile ~/.gitignore_global
```

---

## 23. Common Interview Scenarios

### Scenario 1: "Undo a Commit That Was Already Pushed"

```bash
# ✅ Safe way (creates a new undo commit)
git revert abc1234
git push

# ❌ Dangerous way (rewrites history)
git reset --hard HEAD~1
git push --force    # NEVER do this on shared branches
```

### Scenario 2: "I Committed on the Wrong Branch"

```bash
# Move the last commit to the correct branch
git log --oneline -1              # note the commit hash
git reset --soft HEAD~1           # undo commit, keep changes staged
git stash                         # stash the changes
git checkout correct-branch
git stash pop                     # apply changes here
git commit -m "Feature on correct branch"
```

### Scenario 3: "Squash All Commits in a PR Before Merging"

```bash
# Option 1: Interactive rebase
git rebase -i main
# Change all but first to 'squash' or 'fixup'

# Option 2: Soft reset
git reset --soft main
git commit -m "Single clean commit for the entire feature"
```

### Scenario 4: "Sync Your Fork with the Original Repo"

```bash
git remote add upstream https://github.com/original/repo.git
git fetch upstream
git checkout main
git merge upstream/main
git push origin main
```

### Scenario 5: "Find Who Introduced a Bug and When"

```bash
# Step 1: Find the commit
git bisect start
git bisect bad          # current version has the bug
git bisect good v1.2.0  # this version was fine
# ... test each midpoint ...

# Step 2: Find who authored it
git show <bad-commit-hash>
# OR
git blame -L 42,42 src/BuggyFile.java
```

### Scenario 6: "Accidentally Deleted a Branch"

```bash
git reflog
# Find the last commit of the deleted branch
git checkout -b recovered-branch HEAD@{3}
```

### Scenario 7: "Remove Sensitive Data from Git History"

```bash
# Remove a file from ALL commits in history
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch secrets.env" \
  --prune-empty --tag-name-filter cat -- --all

# Better alternative: use BFG Repo Cleaner
# https://rtyley.github.io/bfg-repo-cleaner/
bfg --delete-files secrets.env
git reflog expire --expire=now --all
git gc --prune=now --aggressive
```

---

## 24. Quick Reference Table

| Task | Command |
|------|---------|
| **Initialize repo** | `git init` |
| **Clone repo** | `git clone <url>` |
| **Check status** | `git status` |
| **Stage file** | `git add <file>` |
| **Stage all** | `git add .` |
| **Commit** | `git commit -m "msg"` |
| **Create branch** | `git branch <name>` |
| **Switch branch** | `git switch <name>` |
| **Create + switch** | `git switch -c <name>` |
| **Merge** | `git merge <branch>` |
| **Rebase** | `git rebase <branch>` |
| **Interactive rebase** | `git rebase -i HEAD~n` |
| **Cherry-pick** | `git cherry-pick <hash>` |
| **Stash** | `git stash` |
| **Restore stash** | `git stash pop` |
| **Undo commit (keep changes)** | `git reset --soft HEAD~1` |
| **Undo commit (discard all)** | `git reset --hard HEAD~1` |
| **Safe undo (shared branch)** | `git revert <hash>` |
| **View log** | `git log --oneline --graph` |
| **View diff** | `git diff` |
| **Fetch** | `git fetch origin` |
| **Pull** | `git pull origin main` |
| **Pull with rebase** | `git pull --rebase` |
| **Push** | `git push origin <branch>` |
| **Safe force push** | `git push --force-with-lease` |
| **Create tag** | `git tag -a v1.0 -m "msg"` |
| **Find bug commit** | `git bisect start` |
| **Recover lost commit** | `git reflog` |
| **Who wrote this line** | `git blame <file>` |
| **Remove untracked** | `git clean -fd` |
| **Stop tracking file** | `git rm --cached <file>` |

---

## 💡 Pro Tips

1. **Always use `--force-with-lease`** instead of `--force`
2. **Use `git add -p`** to review changes before staging
3. **Set `pull.rebase true`** globally for cleaner history
4. **Use `git stash` liberally** — it's your safety blanket
5. **Write meaningful commit messages** — your future self will thank you
6. **Never commit secrets** — use `.env` files and `.gitignore`
7. **Use `git reflog`** before panicking — almost everything is recoverable
8. **Use `git bisect`** instead of manually checking commits
9. **Squash WIP commits** before merging PRs
10. **Use aliases** — speed matters in daily work

---

> *"Git is like a time machine for your code. Learn it well, and you'll never lose work again."*

