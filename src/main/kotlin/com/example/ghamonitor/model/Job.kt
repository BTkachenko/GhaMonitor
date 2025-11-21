package com.example.ghamonitor.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * GitHub job representation (partial).
 */
data class Job(
    @JsonProperty("id")
    val id: Long = 0L,
    @JsonProperty("name")
    val name: String? = null,
    @JsonProperty("status")
    val status: String? = null,
    @JsonProperty("conclusion")
    val conclusion: String? = null,
    @JsonProperty("started_at")
    val startedAt: String? = null,
    @JsonProperty("completed_at")
    val completedAt: String? = null,
    @JsonProperty("steps")
    val steps: List<Step>? = null
)
