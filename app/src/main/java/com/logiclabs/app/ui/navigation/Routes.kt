package com.logiclabs.app.ui.navigation

/**
 * What the bench should load when it opens.
 *
 * The bench screen resolves this exactly once per composition, so a config change (all
 * handled in-manifest) or a recomposition never reloads the board — but arriving from
 * Home twice in a row does, because each arrival is a fresh composition.
 */
sealed interface BenchRequest {
    /** Rebuild whatever the last session was; falls back to the intro lab. */
    data object Continue : BenchRequest

    /** Load a curriculum experiment by id. */
    data class Lab(val labId: String) : BenchRequest

    /** Load a saved project by id. */
    data class Project(val projectId: String) : BenchRequest

    /** An empty powered bench. */
    data object Sandbox : BenchRequest

    companion object {
        /**
         * Decodes nav-arguments back into a request. Unknown or partial pairs degrade
         * to [Continue] rather than throwing: a stale deep link or a hand-edited route
         * should land the user on their bench, not on a crash.
         */
        fun from(raw: String?, id: String?): BenchRequest = when (raw) {
            "lab" -> id?.let { Lab(it) } ?: Continue
            "project" -> id?.let { Project(it) } ?: Continue
            "sandbox" -> Sandbox
            else -> Continue
        }
    }
}

/** Route names and the argument-encoded bench route. */
object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val SETTINGS = "settings"

    private const val BENCH_PATTERN = "bench?request={request}&id={id}"

    const val ARG_REQUEST = "request"
    const val ARG_ID = "id"

    fun bench(request: BenchRequest): String = when (request) {
        BenchRequest.Continue -> "bench?request=continue"
        BenchRequest.Sandbox -> "bench?request=sandbox"
        is BenchRequest.Lab -> "bench?request=lab&id=${request.labId}"
        is BenchRequest.Project -> "bench?request=project&id=${request.projectId}"
    }.let { path ->
        // Assert-free encoding of the pattern: the caller-facing builder always emits
        // the exact shape the composable's navArguments declare.
        check(path.startsWith("bench")) { "route drift: $path" }
        path
    }

    /** The pattern NavHost matches against. Kept private-to-public in one place. */
    val benchRoute: String get() = BENCH_PATTERN
}
