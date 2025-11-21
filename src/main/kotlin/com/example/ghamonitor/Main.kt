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

    println("GitHub Actions monitor CLI")
    println("  repo     = ${config.repo}")
    println("  interval = ${config.intervalSeconds} seconds")
    println("  token    = *** (length=${config.token.length})")

    val stateStore = try {
        RepositoryStateStore()
    } catch (ex: IllegalStateException) {
        System.err.println("ERROR: ${ex.message}")
        return
    }

    val state = try {
        stateStore.load(config.repo)
    } catch (ex: Exception) {
        System.err.println("ERROR: Failed to load state: ${ex.message}")
        return
    }

    println("State:")
    println("  initialized         = ${state.initialized}")
    println("  lastCompletionTime  = ${state.lastCompletionTime}")

    val client = GitHubClient(config.repo, config.token)

    try {
        val runsResponse = client.listWorkflowRuns(page = 1, perPage = 5)
        println("Successfully fetched ${runsResponse.workflowRuns.size} workflow runs (page 1).")
        // Example: update state to now and store it.
        state.setLastCompletionInstant(java.time.Instant.now())
        state.initialized = true
        stateStore.store(state)
        println("State stored under ~/.gha-monitor.")
    } catch (ex: Exception) {
        System.err.println("ERROR: Failed to query GitHub: ${ex.message}")
    }
}
