package com.example.ghamonitor.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response wrapper for listing jobs of a workflow run.
 */
data class JobsResponse(
    @JsonProperty("jobs")
    val jobs: List<Job> = emptyList()
)
