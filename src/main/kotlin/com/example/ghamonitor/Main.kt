package com.example.ghamonitor

/**
 * Application entry point for the GitHub Actions monitor CLI.
 */
fun main(args: Array<String>) {
    val config = try {
        CliConfig.parse(args)
    } catch (ex: IllegalArgumentException) {
        System.err.println("ERROR: ${ex.message}")
        CliConfig.printUsage()
        return
    }

    println("GitHub Actions monitor CLI - connectivity check")
    println("  repo     = ${config.repo}")
    println("  interval = ${config.intervalSeconds} seconds")
    println("  token    = *** (length=${config.token.length})")

    val client = GitHubClient(config.repo, config.token)

    try {
        val runsResponse = client.listWorkflowRuns(page = 1, perPage = 10)
        println("Successfully fetched ${runsResponse.workflowRuns.size} workflow runs (page 1).")
    } catch (ex: Exception) {
        System.err.println("ERROR: Failed to query GitHub: ${ex.message}")
    }
}
