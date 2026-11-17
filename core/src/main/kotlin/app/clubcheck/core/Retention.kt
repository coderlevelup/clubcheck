package app.clubcheck.core

enum class StoredKind { ORIGINAL, EXPORT }

data class StoredOriginal(val path: String, val savedAtMs: Long, val kind: StoredKind = StoredKind.ORIGINAL)

/** Original photos older than [keepDays] days. Blurred exports and files dated in the future are never selected. */
fun originalsToPurge(files: List<StoredOriginal>, nowMs: Long, keepDays: Int = 7): List<StoredOriginal> =
    files.filter { it.kind == StoredKind.ORIGINAL && nowMs - it.savedAtMs > keepDays * 24L * 3_600_000L }
