package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.logging.logWarn
import kotlinx.coroutines.flow.StateFlow

/**
 * For error results that indicate an ongoing assumed "state" that the user should be aware of.
 * Specifically for the user entering a US phone number, which isn't allowed. They should still
 * see that error when coming to the screen later, and also not get a warning about missing
 * recovery methods.
 */
interface UiErrorHintsProvider {
  suspend fun setErrorHint(
    key: UiErrorHintKey,
    hint: UiErrorHint,
  )

  suspend fun getErrorHint(key: UiErrorHintKey): UiErrorHint

  fun errorHintFlow(key: UiErrorHintKey): StateFlow<UiErrorHint>
}

enum class UiErrorHintKey {
  Phone,
  Email,
}

enum class UiErrorHint(val displayString: String = "") {
  None,
  NotAvailableInYourCountry("Not available in your country"),
  ;

  companion object {
    /**
     * This could happen if enum hints in code change names between releases
     */
    fun valueOfOrNone(v: String): UiErrorHint =
      try {
        UiErrorHint.valueOf(v)
      } catch (e: IllegalArgumentException) {
        logWarn(throwable = e) { "UiErrorHint for string \"$v\" not found" }
        None
      }
  }
}
