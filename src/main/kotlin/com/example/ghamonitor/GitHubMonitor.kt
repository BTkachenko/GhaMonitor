package com.example.ghamonitor

import com.example.ghamonitor.model.Job
import com.example.ghamonitor.model.JobsResponse
import com.example.ghamonitor.model.Step
import com.example.ghamonitor.model.WorkflowRun
import com.example.ghamonitor.model.WorkflowRunsResponse
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Main monitoring loop: polls GitHub, prints events, updates state.
 */
class GitHubMonitor(
    private val client: GitHubClient,
    private val stateStore: RepositoryStateStore,
    private val state: RepositoryState,
    private val repo: String,
    private val intervalSeconds: Int
) {

    private val runsPerPage = 50
    private val jobsPerPage = 50
    private val maxRunPages = 5

    private val jobStatusCache: MutableMap<Long, String> = ConcurrentHashMap()
    private val stepStatusCache: MutableMap<String, String> = ConcurrentHashMap()

    @Volatile
    private var running = true

    init {
        require(intervalSeconds > 0) { "intervalSeconds must be > 0" }
    }

    /**
     * Request the monitor loop to stop gracefully.
     */
    fun requestStop() {
        running = false
    }

    /**
     * Run catch-up (for subsequent runs) and then continuous polling until stopped.
     */
    fun run() {
        try {
            catchUpIfNeeded()
        } catch (ex: Exception) {
            System.err.println("WARN: Catch-up failed: ${ex.message}")
        }

        while (running) {
            val before = state.lastCompletionInstant()
            try {
                pollOnce()
            } catch (ex: Exception) {
                System.err.println("WARN: Poll failed: ${ex.message}")
            }
            val after = state.lastCompletionInstant()
            if (after.isAfter(before)) {
                try {
                    stateStore.store(state)
                } catch (ex: IOException) {
                    System.err.println("WARN: Failed to store state: ${ex.message}")
                }
            }
            sleepInterval()
        }

        try {
            stateStore.store(state)
        } catch (ex: IOException) {
            System.err.println("WARN: Failed to store state on shutdown: ${ex.message}")
        }
    }

    /**
     * Catch-up logic:
     * - first run for repo: mark initialized, do NOT print historical events
     * - subsequent runs: print all completion events since lastCompletionTime
     */
    private fun catchUpIfNeeded() {
        if (!state.initialized) {
            state.initialized = true
            stateStore.store(state)
            return
        }

        val lastCompletion = state.lastCompletionInstant()
        scanRunsAndEmit(lastCompletion, catchUpMode = true)
        state.setLastCompletionInstant(Instant.now())
        stateStore.store(state)
    }

    /**
     * Single live polling iteration.
     */
    private fun pollOnce() {
        val lastCompletion = state.lastCompletionInstant()
        scanRunsAndEmit(lastCompletion, catchUpMode = false)
    }

    /**
     * Scan workflow runs and emit events according to mode.
     *
     * @param lastCompletion lower bound for completion timestamps.
     * @param catchUpMode when true, only completion events are emitted.
     */
    private fun scanRunsAndEmit(lastCompletion: Instant, catchUpMode: Boolean) {
        var page = 1
        while (page <= maxRunPages && running) {
            val response: WorkflowRunsResponse = client.listWorkflowRuns(page, runsPerPage)
            val runs = response.workflowRuns
            if (runs.isEmpty()) {
                break
            }

            for (run in runs) {
                if (!running) {
                    return
                }
                processRun(run, lastCompletion, catchUpMode)
            }

            if (runs.size < runsPerPage) {
                break
            }
            page += 1
        }
    }

    /**
     * Process a single workflow run: emit run events and process its jobs.
     */
    private fun processRun(run: WorkflowRun, lastCompletion: Instant, catchUpMode: Boolean) {
        val runCreated = parseInstantOrNow(run.createdAt)
        val runUpdated = parseInstantOrNow(run.updatedAt)

        if (!catchUpMode) {
            if (run.status.equals("queued", ignoreCase = true) && runCreated.isAfter(lastCompletion)) {
                println(EventFormatter.runQueued(repo, run, runCreated))
            }

            if (run.status.equals("completed", ignoreCase = true) && runUpdated.isAfter(lastCompletion)) {
                println(EventFormatter.runCompleted(repo, run, runUpdated))
                updateLastCompletion(runUpdated)
            }
        } else {
            if (run.status.equals("completed", ignoreCase = true) && runUpdated.isAfter(lastCompletion)) {
                println(EventFormatter.runCompleted(repo, run, runUpdated))
                updateLastCompletion(runUpdated)
            }
        }

        var page = 1
        while (running) {
            val jobsResponse: JobsResponse = client.listJobsForRun(run.id, page, jobsPerPage)
            val jobs = jobsResponse.jobs
            if (jobs.isEmpty()) {
                break
            }

            for (job in jobs) {
                if (!running) {
                    return
                }
                processJob(run, job, lastCompletion, catchUpMode)
            }

            if (jobs.size < jobsPerPage) {
                break
            }
            page += 1
        }
    }

    /**
     * Process a single job: emit job events and process its steps.
     */
    private fun processJob(
        run: WorkflowRun,
        job: Job,
        lastCompletion: Instant,
        catchUpMode: Boolean
    ) {
        val jobStarted = parseInstantOrNull(job.startedAt)
        val jobCompleted = parseInstantOrNull(job.completedAt)

        if (!catchUpMode) {
            val jobKey = job.id
            val previousStatus = jobStatusCache[jobKey]
            val currentStatus = job.status

            if (jobStarted != null && jobStarted.isAfter(lastCompletion)) {
                val becameInProgress =
                    !previousStatus.equals("in_progress", ignoreCase = true) &&
                            currentStatus.equals("in_progress", ignoreCase = true)
                if (becameInProgress) {
                    println(EventFormatter.jobStarted(repo, run, job, jobStarted))
                }
            }

            if (jobCompleted != null && jobCompleted.isAfter(lastCompletion)) {
                if (currentStatus.equals("completed", ignoreCase = true)) {
                    println(EventFormatter.jobCompleted(repo, run, job, jobStarted, jobCompleted))
                    updateLastCompletion(jobCompleted)
                }
            }

            if (currentStatus != null) {
                jobStatusCache[jobKey] = currentStatus
            }
        } else {
            if (jobCompleted != null && jobCompleted.isAfter(lastCompletion)) {
                println(EventFormatter.jobCompleted(repo, run, job, jobStarted, jobCompleted))
                updateLastCompletion(jobCompleted)
            }
        }

        val steps = job.steps ?: return
        for (step in steps) {
            if (!running) {
                return
            }
            processStep(run, job, step, lastCompletion, catchUpMode)
        }
    }

    /**
     * Process a single step and emit step events.
     */
    private fun processStep(
        run: WorkflowRun,
        job: Job,
        step: Step,
        lastCompletion: Instant,
        catchUpMode: Boolean
    ) {
        val stepKey = "${job.id}#${step.number}"
        val stepStarted = parseInstantOrNull(step.startedAt)
        val stepCompleted = parseInstantOrNull(step.completedAt)
        val currentStatus = step.status
        val previousStatus = stepStatusCache[stepKey]

        if (!catchUpMode) {
            if (stepStarted != null && stepStarted.isAfter(lastCompletion)) {
                val becameInProgress =
                    !previousStatus.equals("in_progress", ignoreCase = true) &&
                            currentStatus.equals("in_progress", ignoreCase = true)
                if (becameInProgress) {
                    println(EventFormatter.stepStarted(repo, run, job, step, stepStarted))
                }
            }

            if (stepCompleted != null && stepCompleted.isAfter(lastCompletion)) {
                val becameCompleted =
                    !previousStatus.equals("completed", ignoreCase = true) &&
                            currentStatus.equals("completed", ignoreCase = true)
                if (becameCompleted) {
                    println(
                        EventFormatter.stepCompleted(
                            repo = repo,
                            run = run,
                            job = job,
                            step = step,
                            started = stepStarted,
                            completed = stepCompleted
                        )
                    )
                    updateLastCompletion(stepCompleted)
                }
            }

            if (currentStatus != null) {
                stepStatusCache[stepKey] = currentStatus
            }
        } else {
            if (stepCompleted != null && stepCompleted.isAfter(lastCompletion)) {
                println(
                    EventFormatter.stepCompleted(
                        repo = repo,
                        run = run,
                        job = job,
                        step = step,
                        started = stepStarted,
                        completed = stepCompleted
                    )
                )
                updateLastCompletion(stepCompleted)
            }
        }
    }

    /**
     * Update lastCompletionTime in state if the new instant is newer.
     */
    private fun updateLastCompletion(instant: Instant) {
        val current = state.lastCompletionInstant()
        if (instant.isAfter(current)) {
            state.setLastCompletionInstant(instant)
        }
    }

    /**
     * Parse timestamp or return null on failure.
     */
    private fun parseInstantOrNull(value: String?): Instant? =
        if (value.isNullOrBlank()) {
            null
        } else {
            try {
                Instant.parse(value)
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Parse timestamp or return now when missing or invalid.
     */
    private fun parseInstantOrNow(value: String?): Instant =
        parseInstantOrNull(value) ?: Instant.now()

    /**
     * Sleep between polling iterations, reacting to interruption.
     */
    private fun sleepInterval() {
        try {
            Thread.sleep(Duration.ofSeconds(intervalSeconds.toLong()).toMillis())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            running = false
        }
    }
}
