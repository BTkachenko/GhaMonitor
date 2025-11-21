package com.example.ghamonitor

/**
 * Application entry point for the GitHub Actions monitor CLI.
 */
fun main(args: Array<String>) {
    val config: CliConfig = try {
        CliConfig.parse(args)
    } catch (ex: IllegalArgumentException) {
        System.err.println("ERROR: ${ex.message}")
        CliConfig.printUsage()
        return
    }

    val stateStore: RepositoryStateStore = try {
        RepositoryStateStore()
    } catch (ex: IllegalStateException) {
        System.err.println("ERROR: ${ex.message}")
        return
    }

    val state: RepositoryState = try {
        stateStore.load(config.repo)
    } catch (ex: Exception) {
        System.err.println("ERROR: Failed to load state: ${ex.message}")
        return
    }

    val client = GitHubClient(config.repo, config.token)
    val monitor = GitHubMonitor(
        client = client,
        stateStore = stateStore,
        state = state,
        repo = config.repo,
        intervalSeconds = config.intervalSeconds
    )

    println("GitHub Actions monitor started")
    println("  repo     = ${config.repo}")
    println("  interval = ${config.intervalSeconds} seconds")
    println("Press Ctrl+C to stop.")

    // Graceful shutdown on Ctrl+C.
    Runtime.getRuntime().addShutdownHook(
        Thread {
            monitor.requestStop()
        }
    )

    try {
        monitor.run()
    } catch (ex: Exception) {
        System.err.println("ERROR: Monitor terminated with error: ${ex.message}")
    }
}
