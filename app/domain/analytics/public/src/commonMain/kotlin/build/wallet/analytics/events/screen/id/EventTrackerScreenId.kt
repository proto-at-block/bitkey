package build.wallet.analytics.events.screen.id

/**
 * A unique ID to use to track various screens in the app.
 *
 * Some screens are reused in different contexts, like NFC screens. Those are disambiguated
 * by using a [EventTrackerScreenIdContext], e.g. [EventTrackerScreenIdNfcContext] for NFC.
 */
interface EventTrackerScreenId {
  val name: String
}
