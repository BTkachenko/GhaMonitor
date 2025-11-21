package com.example.ghamonitor

/**
 * Parsed CLI configuration.
 */
data class CliConfig(
    val repo: String,
    val token: String,
    val intervalSeconds: Int
) {

    companion object {

        private const val DEFAULT_INTERVAL_SECONDS: Int = 15

        /**
         * Parse CLI arguments into a configuration object.
         *
         * Expected arguments:
         *  --repo owner/repo
         *  --token GITHUB_TOKEN
         *  [--interval seconds]
         *
         * @throws IllegalArgumentException for invalid or missing arguments.
         */
        fun parse(args: Array<String>): CliConfig {
            var repo: String? = null
            var token: String? = null
            var interval: Int? = null

            var index = 0
            while (index < args.size) {
                when (val arg = args[index]) {
                    "--repo" -> {
                        val value = args.getOrNull(index + 1)
                            ?: throw IllegalArgumentException("--repo requires value")
                        repo = value
                        index += 2
                    }

                    "--token" -> {
                        val value = args.getOrNull(index + 1)
                            ?: throw IllegalArgumentException("--token requires value")
                        token = value
                        index += 2
                    }

                    "--interval" -> {
                        val value = args.getOrNull(index + 1)
                            ?: throw IllegalArgumentException("--interval requires value")
                        val parsed = value.toIntOrNull()
                            ?: throw IllegalArgumentException("--interval must be an integer")
                        if (parsed <= 0) {
                            throw IllegalArgumentException("--interval must be > 0")
                        }
                        interval = parsed
                        index += 2
                    }

                    "--help", "-h" -> {
                        printUsage()
                        kotlin.system.exitProcess(0)
                    }

                    else -> {
                        throw IllegalArgumentException("Unknown argument: $arg")
                    }
                }
            }

            val repoValue = repo?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Missing required --repo <owner/repo> argument")

            if (!repoValue.contains('/')) {
                throw IllegalArgumentException("Repository must be in format owner/repo")
            }

            val tokenValue = token?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Missing required --token <GITHUB_TOKEN> argument")

            val effectiveInterval = interval ?: DEFAULT_INTERVAL_SECONDS

            return CliConfig(
                repo = repoValue,
                token = tokenValue,
                intervalSeconds = effectiveInterval
            )
        }

        /**
         * Print CLI usage to stdout.
         */
        fun printUsage() {
            println("Usage:")
            println("  java -jar gha-monitor.jar --repo owner/repo --token GITHUB_TOKEN [--interval seconds]")
            println()
            println("Options:")
            println("  --repo      Repository in the form owner/repo")
            println("  --token     GitHub personal access token with Actions read permissions")
            println("  --interval  Poll interval in seconds (default 15)")
        }
    }
}
