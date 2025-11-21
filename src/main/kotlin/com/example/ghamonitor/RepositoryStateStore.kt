package com.example.ghamonitor

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Persists and loads repository state to/from the user's home directory.
 *
 * Files are stored under: ~/.gha-monitor/<owner>_<repo>.json
 */
class RepositoryStateStore {

    private val baseDir: Path

    init {
        val userHome = System.getProperty("user.home")
        require(!userHome.isNullOrBlank()) { "Cannot determine user home directory" }
        baseDir = Path.of(userHome, STATE_DIR_NAME)
    }

    /**
     * Load state for repository or create a new one with lastCompletionTime = now().
     *
     * @param repo repository name (owner/repo).
     */
    @Throws(IOException::class)
    fun load(repo: String): RepositoryState {
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir)
        }

        val path = stateFile(repo)
        if (!Files.exists(path)) {
            // First time we see this repository: new state, initialized = false.
            return RepositoryState.createNew(repo, Instant.now())
        }

        return JsonSupport.mapper.readValue(
            path.toFile(),
            RepositoryState::class.java
        )
    }

    /**
     * Store updated state for the given repository.
     *
     * @param state repository state.
     */
    @Throws(IOException::class)
    fun store(state: RepositoryState) {
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir)
        }
        val path = stateFile(state.repo)
        JsonSupport.mapper
            .writerWithDefaultPrettyPrinter()
            .writeValue(path.toFile(), state)
    }

    private fun stateFile(repo: String): Path {
        val sanitized = repo.replace('/', '_')
        return baseDir.resolve("$sanitized.json")
    }

    companion object {
        private const val STATE_DIR_NAME: String = ".gha-monitor"
    }
}
