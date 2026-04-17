package build.wallet.nfc.platform

/**
 * A [ConfirmationHandles] instance with empty handle lists for use in tests.
 *
 * Use this wherever a valid [ConfirmationHandles] is needed but the specific handle
 * bytes are irrelevant to the behaviour under test.
 */
val ConfirmationHandlesFake = ConfirmationHandles(
  responseHandle = emptyList(),
  confirmationHandle = emptyList()
)
