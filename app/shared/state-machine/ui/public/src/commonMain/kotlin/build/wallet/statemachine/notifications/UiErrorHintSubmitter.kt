package build.wallet.statemachine.notifications

/**
 * Records if the user tried to enter a phone number for a country that is not available. Used
 * for recovery channel UI screens.
 */
interface UiErrorHintSubmitter {
  fun phoneNone()

  fun phoneNotAvailable()
}
