package build.wallet.analytics.events

/**
 * Optional additional context for the event if it is emitted under different conditions.
 * For example, events emitted in partner flows have contexts corresponding to selected partners.
 * The context name is appended to the event id.
 */
interface EventTrackerContext {
  val name: String
}
