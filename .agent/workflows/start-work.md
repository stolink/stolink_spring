---
description: Start working on an issue by creating a branch and updating project status
---

# Start Work

To start working on an issue, run the `start-work.sh` script.

Usage: `./scripts/start-work.sh <ISSUE_NUM> [BRANCH_SUFFIX]`

Examples:

- `./scripts/start-work.sh` (View available issues)
- `./scripts/start-work.sh 123` (Start work on issue #123)
- `./scripts/start-work.sh 123 fix-login-bug` (Start work on issue #123 with branch suffix)

Steps:

1. Run the script with the issue number.
   `./scripts/start-work.sh <ISSUE_NUM> [BRANCH_SUFFIX]`
