package com.example.ghamonitor

import com.example.ghamonitor.model.Job
import com.example.ghamonitor.model.Step
import com.example.ghamonitor.model.WorkflowRun
import java.time.Instant

/**
 * Formats GitHub Actions events into single-line log entries.
 */
object EventFormatter {

    /**
     * Format a workflow run queued event.
     */
    fun runQueued(repo: String, run: WorkflowRun, created: Instant): String {
        val observed: Instant = Instant.now()
        return buildString {
            append(observed)
            append(" event=RUN_QUEUED")
            append(" repo=").append(repo)
            append(" workflow=\"").append(safe(run.name)).append('"')
            append(" run_id=").append(run.id)
            append(" run_number=").append(run.runNumber)
            append(" branch=").append(safe(run.headBranch))
            append(" sha=").append(safe(run.headSha))
            append(" started_at=").append(created)
        }
    }

    /**
     * Format a workflow run completed event.
     */
    fun runCompleted(repo: String, run: WorkflowRun, completed: Instant): String {
        val observed: Instant = Instant.now()
        return buildString {
            append(observed)
            append(" event=RUN_COMPLETED")
            append(" repo=").append(repo)
            append(" workflow=\"").append(safe(run.name)).append('"')
            append(" run_id=").append(run.id)
            append(" run_number=").append(run.runNumber)
            append(" branch=").append(safe(run.headBranch))
            append(" sha=").append(safe(run.headSha))
            append(" status=").append(safe(run.status))
            append(" conclusion=").append(safe(run.conclusion))
            append(" completed_at=").append(completed)
        }
    }

    /**
     * Format a job started event.
     */
    fun jobStarted(
        repo: String,
        run: WorkflowRun,
        job: Job,
        started: Instant
    ): String {
        val observed: Instant = Instant.now()
        return buildString {
            append(observed)
            append(" event=JOB_STARTED")
            append(" repo=").append(repo)
            append(" workflow=\"").append(safe(run.name)).append('"')
            append(" run_id=").append(run.id)
            append(" run_number=").append(run.runNumber)
            append(" branch=").append(safe(run.headBranch))
            append(" sha=").append(safe(run.headSha))
            append(" job_id=").append(job.id)
            append(" job=\"").append(safe(job.name)).append('"')
            append(" status=").append(safe(job.status))
            append(" started_at=").append(started)
        }
    }

    /**
     * Format a job completed event.
     */
    fun jobCompleted(
        repo: String,
        run: WorkflowRun,
        job: Job,
        started: Instant?,
        completed: Instant
    ): String {
        val observed: Instant = Instant.now()
        return buildString {
            append(observed)
            append(" event=JOB_COMPLETED")
            append(" repo=").append(repo)
            append(" workflow=\"").append(safe(run.name)).append('"')
            append(" run_id=").append(run.id)
            append(" run_number=").append(run.runNumber)
            append(" branch=").append(safe(run.headBranch))
            append(" sha=").append(safe(run.headSha))
            append(" job_id=").append(job.id)
            append(" job=\"").append(safe(job.name)).append('"')
            append(" status=").append(safe(job.status))
            append(" conclusion=").append(safe(job.conclusion))
            if (started != null) {
                append(" started_at=").append(started)
            }
            append(" completed_at=").append(completed)
        }
    }

    /**
     * Format a step started event.
     */
    fun stepStarted(
        repo: String,
        run: WorkflowRun,
        job: Job,
        step: Step,
        started: Instant
    ): String {
        val observed: Instant = Instant.now()
        return buildString {
            append(observed)
            append(" event=STEP_STARTED")
            append(" repo=").append(repo)
            append(" workflow=\"").append(safe(run.name)).append('"')
            append(" run_id=").append(run.id)
            append(" run_number=").append(run.runNumber)
            append(" branch=").append(safe(run.headBranch))
            append(" sha=").append(safe(run.headSha))
            append(" job_id=").append(job.id)
            append(" job=\"").append(safe(job.name)).append('"')
            append(" step=\"").append(safe(step.name)).append('"')
            append(" status=").append(safe(step.status))
            append(" started_at=").append(started)
        }
    }

    /**
     * Format a step completed event.
     */
    fun stepCompleted(
        repo: String,
        run: WorkflowRun,
        job: Job,
        step: Step,
        started: Instant?,
        completed: Instant
    ): String {
        val observed: Instant = Instant.now()
        return buildString {
            append(observed)
            append(" event=STEP_COMPLETED")
            append(" repo=").append(repo)
            append(" workflow=\"").append(safe(run.name)).append('"')
            append(" run_id=").append(run.id)
            append(" run_number=").append(run.runNumber)
            append(" branch=").append(safe(run.headBranch))
            append(" sha=").append(safe(run.headSha))
            append(" job_id=").append(job.id)
            append(" job=\"").append(safe(job.name)).append('"')
            append(" step=\"").append(safe(step.name)).append('"')
            append(" status=").append(safe(step.status))
            append(" conclusion=").append(safe(step.conclusion))
            if (started != null) {
                append(" started_at=").append(started)
            }
            append(" completed_at=").append(completed)
        }
    }

    /**
     * Make nullable strings safe for output.
     */
    private fun safe(value: String?): String =
        value?.replace('"', '\'') ?: "null"
}
