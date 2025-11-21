package com.example.ghamonitor

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Per-repository persisted state.
 */
data class RepositoryState(
    @JsonProperty("repo")
    val repo: String,
    @JsonProperty("lastCompletionTime")
    var lastCompletionTime: String,
    @JsonProperty("initialized")
    var initialized: Boolean
) {

    /**
     * @return last completion time as Instant.
     */
    fun lastCompletionInstant(): Instant =
        try {
            Instant.parse(lastCompletionTime)
        } catch (_: Exception) {
            Instant.EPOCH
        }

    /**
     * Update last completion time.
     *
     * @param instant new timestamp.
     */
    fun setLastCompletionInstant(instant: Instant) {
        lastCompletionTime = instant.toString()
    }

    companion object {

        /**
         * Create new state with lastCompletionTime set to now and initialized=false.
         */
        fun createNew(repo: String, now: Instant): RepositoryState =
            RepositoryState(
                repo = repo,
                lastCompletionTime = now.toString(),
                initialized = false
            )
    }
}
