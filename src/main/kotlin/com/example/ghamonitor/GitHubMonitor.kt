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

    private val runsPerPage: Int = 50
    private val jobsPerPage: Int = 50
    private val maxRunPages: Int = 5

    private val jobStatusCache: MutableMap<Long, String?> = ConcurrentHashMap()
    private val stepStatusCache: MutableMap<String, String?> = ConcurrentHashMap()

    @Volatile
    private var running: Boolean = true

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
            val before: Instant = state.lastCompletionInstant()
            try {
                pollOnce()
            } catch (ex: Exception) {
                System.err.println("WARN: Poll failed: ${ex.message}")
            }
            val after: Instant = state.lastCompletionInstant()
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

        val lastCompletion: Instant = state.lastCompletionInstant()
        scanRunsAndEmit(lastCompletion, catchUpMode = true)
        state.setLastCompletionInstant(Instant.now())
        stateStore.store(state)
    }

    /**
     * Single live polling iteration.
     */
    private fun pollOnce() {
        val lastCompletion: Instant = state.lastCompletionInstant()
        scanRunsAndEmit(lastCompletion, catchUpMode = false)
    }

    /**
     * Scan workflow runs and emit events according to mode.
     *
     * @param lastCompletion lower bound for completion timestamps.
     * @param catchUpMode when true, only completion events are emitted.
     */
    private fun scanRunsAndEmit(lastCompletion: Instant, catchUpMode: Boolean) {
        var page: Int = 1
        while (page <= maxRunPages && running) {
            val response: WorkflowRunsResponse = client.listWorkflowRuns(page, runsPerPage)
            val runs: List<WorkflowRun> = response.workflowRuns
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
        val runCreated: Instant = parseInstantOrNow(run.createdAt)
        val runUpdated: Instant = parseInstantOrNow(run.updatedAt)

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

        var page: Int = 1
        while (running) {
            val jobsResponse: JobsResponse = client.listJobsForRun(run.id, page, jobsPerPage)
            val jobs: List<Job> = jobsResponse.jobs
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
        val jobStarted: Instant? = parseInstantOrNull(job.startedAt)
        val jobCompleted: Instant? = parseInstantOrNull(job.completedAt)

        if (!catchUpMode) {
            val jobKey: Long = job.id
            val previousStatus: String? = jobStatusCache[jobKey]
            val currentStatus: String? = job.status

            if (jobStarted != null && jobStarted.isAfter(lastCompletion)) {
                val becameInProgress: Boolean =
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

            jobStatusCache[jobKey] = currentStatus
        } else {
            if (jobCompleted != null && jobCompleted.isAfter(lastCompletion)) {
                println(EventFormatter.jobCompleted(repo, run, job, jobStarted, jobCompleted))
                updateLastCompletion(jobCompleted)
            }
        }

        val steps: List<Step> = job.steps ?: return
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
        val stepKey: String = "${job.id}#${step.number}"
        val stepStarted: Instant? = parseInstantOrNull(step.startedAt)
        val stepCompleted: Instant? = parseInstantOrNull(step.completedAt)
        val currentStatus: String? = step.status
        val previousStatus: String? = stepStatusCache[stepKey]

        if (!catchUpMode) {
            if (stepStarted != null && stepStarted.isAfter(lastCompletion)) {
                val becameInProgress: Boolean =
                    !previousStatus.equals("in_progress", ignoreCase = true) &&
                            currentStatus.equals("in_progress", ignoreCase = true)
                if (becameInProgress) {
                    println(EventFormatter.stepStarted(repo, run, job, step, stepStarted))
                }
            }

            if (stepCompleted != null && stepCompleted.isAfter(lastCompletion)) {
                val becameCompleted: Boolean =
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

            stepStatusCache[stepKey] = currentStatus
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
        val current: Instant = state.lastCompletionInstant()
        if (instant.isAfter(current)) {
            state.setLastCompletionInstant(instant)
        }
    }

    /**
     * Parse an ISO-8601 timestamp or return null on failure.
     */
    private fun parseInstantOrNull(value: String?): Instant? =
        if (value.isNullOrBlank()) {
            null
        } else {
            try {
                Instant.parse(value)
            } catch (ex: Exception) {
                null
            }
        }

    /**
     * Parse an ISO-8601 timestamp or return now when missing or invalid.
     */
    private fun parseInstantOrNow(value: String?): Instant =
        parseInstantOrNull(value) ?: Instant.now()

    /**
     * Sleep between polling iterations, reacting to interruption.
     */
    private fun sleepInterval() {
        try {
            Thread.sleep(Duration.ofSeconds(intervalSeconds.toLong()).toMillis())
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            running = false
        }
    }
}
