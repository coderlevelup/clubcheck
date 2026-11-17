package app.clubcheck.core

/**
 * The location rule in one place: no permission, switched off, no fix, or a failing provider all give
 * [CoarseCell.NONE] (a valid check-in without a place). A real fix is snapped at once and never kept.
 */
suspend fun coarseCellFrom(permissionGranted: Boolean, enabled: Boolean, fix: suspend () -> Pair<Double, Double>?): CoarseCell {
    if (!permissionGranted || !enabled) return CoarseCell.NONE
    val position = try { fix() } catch (e: Exception) { null } ?: return CoarseCell.NONE
    return snapToCell(position.first, position.second)
}
