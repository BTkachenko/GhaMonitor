package com.example.ghamonitor.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * GitHub workflow run representation (partial).
 */
data class WorkflowRun(
    @JsonProperty("id")
    val id: Long = 0L,
    @JsonProperty("name")
    val name: String? = null,
    @JsonProperty("run_number")
    val runNumber: Long = 0L,
    @JsonProperty("head_branch")
    val headBranch: String? = null,
    @JsonProperty("head_sha")
    val headSha: String? = null,
    @JsonProperty("status")
    val status: String? = null,
    @JsonProperty("conclusion")
    val conclusion: String? = null,
    @JsonProperty("created_at")
    val createdAt: String? = null,
    @JsonProperty("updated_at")
    val updatedAt: String? = null
)
