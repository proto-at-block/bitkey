package build.wallet.statemachine.automations

/**
 * Functions used by functional UI tests to simplify automation.
 */
interface AutomaticUiTests {
  /**
   * Proceed to the next primary screen in the application.
   *
   * This is used by functional tests to follow the primary path in a
   * particular flow. It should invoke the screen's equivalent of a primary
   * action or continue to the next screen state in the flow.
   */
  fun automateNextPrimaryScreen()
}
