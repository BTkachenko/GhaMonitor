package com.example.ghamonitor

import com.example.ghamonitor.model.JobsResponse
import com.example.ghamonitor.model.WorkflowRunsResponse
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/**
 * GitHub REST API client for workflows and jobs.
 */
class GitHubClient(
    repository: String,
    private val token: String
) {

    private val owner: String
    private val repo: String
    private val httpClient: HttpClient = HttpClient.newHttpClient()

    init {
        require(repository.isNotBlank()) { "repository must not be blank" }
        require(token.isNotBlank()) { "token must not be blank" }

        val parts = repository.split('/', limit = 2)
        require(parts.size == 2) { "repository must be in format owner/repo" }

        owner = parts[0]
        repo = parts[1]
    }

    /**
     * List workflow runs for the repository.
     *
     * @param page page number, starting from 1.
     * @param perPage page size (max 100).
     * @throws IOException when the GitHub API returns an error status.
     * @throws InterruptedException when the HTTP request is interrupted.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun listWorkflowRuns(page: Int, perPage: Int): WorkflowRunsResponse {
        require(page >= 1) { "page must be >= 1" }
        require(perPage in 1..100) { "perPage must be between 1 and 100" }

        val uriString = "${API_BASE}/repos/${encode(owner)}/${encode(repo)}/actions/runs" +
                "?page=$page&per_page=$perPage"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(uriString))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val status = response.statusCode()

        if (status == 401 || status == 403) {
            throw IOException("GitHub API authentication failed (HTTP $status)")
        }
        if (status >= 400) {
            throw IOException("GitHub API error (HTTP $status): ${response.body()}")
        }

        return JsonSupport.mapper.readValue(
            response.body(),
            WorkflowRunsResponse::class.java
        )
    }

    /**
     * List jobs for a given workflow run.
     *
     * @param runId workflow run identifier.
     * @param page page number, starting from 1.
     * @param perPage page size (max 100).
     * @throws IOException when the GitHub API returns an error status.
     * @throws InterruptedException when the HTTP request is interrupted.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun listJobsForRun(runId: Long, page: Int, perPage: Int): JobsResponse {
        require(runId > 0) { "runId must be > 0" }
        require(page >= 1) { "page must be >= 1" }
        require(perPage in 1..100) { "perPage must be between 1 and 100" }

        val uriString = "${API_BASE}/repos/${encode(owner)}/${encode(repo)}/actions/runs/$runId/jobs" +
                "?page=$page&per_page=$perPage"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(uriString))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val status = response.statusCode()

        if (status == 401 || status == 403) {
            throw IOException("GitHub API authentication failed (HTTP $status)")
        }
        if (status >= 400) {
            throw IOException("GitHub API error (HTTP $status): ${response.body()}")
        }

        return JsonSupport.mapper.readValue(
            response.body(),
            JobsResponse::class.java
        )
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        private const val API_BASE: String = "https://api.github.com"
    }
}
