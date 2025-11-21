# GitHub Actions Monitor (Kotlin CLI)

A small Kotlin command-line tool that periodically polls GitHub Actions for a given repository and prints workflow events to `stdout`, one line per event.

The tool reports:

- workflow runs being queued
- workflow runs completing
- jobs starting and completing
- steps starting and completing (with success/failure in `conclusion`)

Each event line contains timestamps (start/completion where available), branch name and commit SHA.

---

## Requirements

- JDK 21+
- Gradle (you can use the provided Gradle wrapper)
- GitHub personal access token with permission to read Actions data for the target repository
    - For private repositories, a classic PAT with `repo` scope is sufficient
    - For public repositories, a read-only token is enough

---

## Build

```bash
./gradlew clean build
```

This compiles the project and runs basic checks.

---

## Usage

Run with the Gradle wrapper:

```bash
./gradlew run --args="--repo owner/repo --token GITHUB_TOKEN [--interval seconds]"
```

Or, after creating a runnable JAR, use:

```bash
java -jar gha-monitor.jar \
  --repo owner/repo \
  --token GITHUB_TOKEN \
  [--interval seconds]
```

Arguments:

- `--repo` – repository in the form `owner/repo`
- `--token` – GitHub personal access token
- `--interval` – polling interval in seconds (default: `15`)

Stop monitoring with `Ctrl+C`. The tool installs a shutdown hook and terminates gracefully, persisting its state before exit.

---

## Output format

Each event is printed as a single line with space-separated `key=value` fields. Example:

```text
2025-11-21T09:59:08.174323844Z event=STEP_COMPLETED repo=owner/repo workflow="CI" run_id=19566805300 run_number=2 branch=main sha=21ea273812eb41b3c65ef247728e5227841ddd11 job_id=56030676842 job="build" step="Checkout" status=completed conclusion=success started_at=2025-11-21T09:59:02Z completed_at=2025-11-21T09:59:03Z
```

Common fields:

- `event` – event type (see below)
- `repo` – `owner/repo`
- `workflow` – workflow name
- `run_id`, `run_number` – GitHub workflow run identifiers
- `branch` – branch name (head ref)
- `sha` – commit SHA
- `job_id`, `job` – job id and job name (for job/step events)
- `step` – step name (for step events)
- `status` – GitHub status (`queued`, `in_progress`, `completed`, etc.)
- `conclusion` – GitHub conclusion (`success`, `failure`, `cancelled`, etc., for completed jobs/steps)
- `started_at` – start timestamp (when provided by the API)
- `completed_at` – completion timestamp (for completed jobs/steps/runs)

Event types:

- `RUN_QUEUED` – workflow run has been queued
- `RUN_COMPLETED` – workflow run finished
- `JOB_STARTED` – job started (`status = in_progress`)
- `JOB_COMPLETED` – job finished (`status = completed`)
- `STEP_STARTED` – step started (`status = in_progress`)
- `STEP_COMPLETED` – step finished (`status = completed` with `conclusion`)

---

## State and resume behavior

The tool keeps a small per-repository state file under:

```text
~/.gha-monitor/<owner>_<repo>.json
```

The state file contains:

- `repo` – repository name (`owner/repo`)
- `lastCompletionTime` – timestamp of the last processed completion event
- `initialized` – whether this repository has been monitored at least once

Behavior:

- On the **first** run for a repository:
    - if there is no state file, the tool creates one with `lastCompletionTime` set to the current time and `initialized = false`
    - `catch-up` mode marks the repository as initialized but does **not** replay historical events
    - the tool only prints events for runs/jobs/steps that happen after startup

- On **subsequent** runs for the same repository:
    - `catch-up` mode scans recent workflow runs and prints all workflow run/job/step **completion** events whose completion timestamps are greater than `lastCompletionTime`
    - after catch-up, the tool switches to live mode and prints new events as they appear
    - `lastCompletionTime` is updated whenever a newer completion event is processed and persisted periodically and on graceful shutdown

This allows the tool to be stopped and restarted while still delivering a continuous stream of completion events without replaying old history on every run.

---