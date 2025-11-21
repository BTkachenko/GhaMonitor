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

    // Temporary output to verify that parsing works.
    // This will be replaced by the monitoring logic in later commits.
    println("GitHub Actions monitor starting...")
    println("  repo     = ${config.repo}")
    println("  interval = ${config.intervalSeconds} seconds")
    println("  token    = *** (length=${config.token.length})")
}
