package app.clubcheck.core

/** True only when the hand range and the camera range are more than 3 people apart. */
fun flagged(hand: Range, camera: Range): Boolean {
    val gap = maxOf(camera.lo - hand.hi, hand.lo - camera.hi, 0)
    return gap > 3
}
