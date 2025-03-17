package build.wallet.statemachine.core

/**
 * Describes the direction of progress for our circular progress spinners
 * Clockwise: Timer starts empty and fills clockwise
 * CounterClockwise: Timer starts full and empties counter-clockwise
 * (see Timer.kt and CircularProgressIndicator.kt/CircularProgressView.swift)
 */
enum class TimerDirection {
  Clockwise,
  CounterClockwise,
}
