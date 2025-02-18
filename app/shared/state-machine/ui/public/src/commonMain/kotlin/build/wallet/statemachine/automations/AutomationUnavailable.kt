package build.wallet.statemachine.automations

/**
 * Thrown when an automated test action action could not be performed.
 */
class AutomationUnavailable(val reason: String) : IllegalStateException(
  "Unable to automate test: $reason"
)
