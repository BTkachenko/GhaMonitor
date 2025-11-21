package com.example.ghamonitor.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * GitHub step representation (partial).
 */
data class Step(
    @JsonProperty("name")
    val name: String? = null,
    @JsonProperty("status")
    val status: String? = null,
    @JsonProperty("conclusion")
    val conclusion: String? = null,
    @JsonProperty("number")
    val number: Int = 0,
    @JsonProperty("started_at")
    val startedAt: String? = null,
    @JsonProperty("completed_at")
    val completedAt: String? = null
)
