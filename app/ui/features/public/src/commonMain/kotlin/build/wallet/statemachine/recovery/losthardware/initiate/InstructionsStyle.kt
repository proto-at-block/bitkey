package build.wallet.statemachine.recovery.losthardware.initiate

/**
 * Indicates the type of navigation presentation to use on the instructions
 * screen.
 */
sealed interface InstructionsStyle {
  /**
   * Displays a navigation interaction for a recovery attempt that was started
   * from within an existing functional application.
   */
  object Independent : InstructionsStyle

  /**
   * Displays a navigation interaction appropriate for continuing a recovery
   * attempt, such as social recovery.
   */
  object ContinuingRecovery : InstructionsStyle

  /**
   * Displays a navigation interaction appropriate for resuming a previously
   * started, but incomplete recovery.
   */
  object ResumedRecoveryAttempt : InstructionsStyle
}
