package com.example.ghamonitor.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response wrapper for listing workflow runs.
 */
data class WorkflowRunsResponse(
    @JsonProperty("workflow_runs")
    val workflowRuns: List<WorkflowRun> = emptyList()
)
