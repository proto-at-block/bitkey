package build.wallet.nfc.platform

import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession

/**
 * Callback interface for reporting NFC transfer progress.
 *
 * This is a `fun interface` rather than a function type `(Float) -> Unit` because
 * Kotlin/Native cannot bridge function types to Swift when they are passed as `Any?`
 * through `KotlinSuspendFunction3.invoke`. Using a `fun interface` ensures proper
 * ObjC protocol export that Swift can cast to.
 *
 * Progress values should be between 0.0f (0%) and 1.0f (100%).
 */
fun interface NfcProgressCallback {
  @Suppress("unused") // Called from platform-specific implementations (iOS/Android)
  fun onProgress(progress: Float)
}

/**
 * Maps a [ConfirmationResult] to the next [HardwareInteraction] state after the user
 * has confirmed on the device and the second NFC tap has been performed.
 *
 * Implemented as a `fun interface` (rather than a suspend function type) to ensure
 * proper ObjC/Swift protocol export for KMM interop — avoiding the pattern where
 * Swift async closures had to be stored and later invoked as `KotlinSuspendFunction2`
 * references across the coroutine boundary.
 *
 * The mapping is synchronous: the NFC session machinery (suspend/async) is handled
 * by the state machine, which calls [NfcCommands.getConfirmationResult] directly and
 * then passes the plain [ConfirmationResult] data here for command-specific processing.
 */
fun interface ConfirmationResultMapper {
  @Throws(NfcException::class)
  fun mapResult(result: ConfirmationResult): HardwareInteraction<*>
}

/**
 * Represents the state and flow of hardware interactions in the wallet application.
 *
 * @param R The type of the final result that will be returned when the interaction completes
 */
sealed interface HardwareInteraction<R> {
  /**
   * Interaction has ended and the resulting data was returned.
   */
  data class Completed<R>(
    /**
     * The resulting data from the hardware interaction.
     */
    val result: R,
  ) : HardwareInteraction<R>

  /**
   * Indicates that an interaction requires chunked data transfer during the current
   * NFC session before proceeding. Used for operations like W3 transaction signing
   * where PSBT data must be transferred in chunks with progress updates.
   *
   * The [transferAndFetch] callback handles the transfer and returns the next
   * interaction state (typically [RequiresConfirmation] for operations needing
   * user approval on the device).
   *
   * **Design note**: unlike [RequiresConfirmation], this intentionally keeps a suspend
   * callback rather than opaque handles. The transfer phase is a same-session I/O loop
   * that sends PSBT chunks over NFC with progress updates — it is inherently async and
   * there is no data-only equivalent (there is no firmware handle to retrieve later;
   * the data must be pushed, not pulled). `NfcSessionTransferFunction` on iOS bridges
   * this cleanly for the chunked-transfer path without the deferred-coroutine risk that
   * motivated the [RequiresConfirmation] redesign.
   */
  data class RequiresTransfer<R>(
    /**
     * Callback to perform chunked data transfer during the current NFC session.
     * The progress callback should be invoked with values (0.0–1.0) to update the
     * UI during transfer. Returns the next [HardwareInteraction] state after transfer.
     */
    val transferAndFetch: suspend (
      session: NfcSession,
      commands: NfcCommands,
      onProgress: NfcProgressCallback,
    ) -> HardwareInteraction<R>,
  ) : HardwareInteraction<R>

  /**
   * Indicates that an interaction has started, but requires the user to confirm
   * on the device before the result can be retrieved. The caller must perform
   * another NFC tap after the user confirms.
   *
   * The [handles] are opaque tokens returned by the firmware that identify the pending
   * operation. On the second NFC tap, the state machine calls
   * [NfcCommands.getConfirmationResult] with these handles and passes the result to
   * [mapResult] for command-specific processing. This keeps control flow in Kotlin and
   * avoids storing suspend-function callbacks (which caused bridging issues when Swift
   * async closures were carried across the KMM boundary as `KotlinSuspendFunction2`).
   */
  data class RequiresConfirmation<R>(
    /**
     * Opaque firmware handles identifying the pending confirmation operation.
     * Passed to [NfcCommands.getConfirmationResult] on the second NFC tap.
     */
    val handles: ConfirmationHandles,
    /**
     * Synchronous mapper that converts the [ConfirmationResult] returned by
     * [NfcCommands.getConfirmationResult] into the final [HardwareInteraction] state.
     * Command-specific logic (e.g. PSBT assembly) lives here.
     */
    val mapResult: ConfirmationResultMapper,
  ) : HardwareInteraction<R>

  /**
   * Emulates on-device confirmation for fake hardware implementations.
   *
   * The UI shows a prompt simulating the device's confirmation screen with approve/deny
   * actions and contextual details about the operation being confirmed.
   * After user selection, the confirmation flow continues with a second NFC tap.
   */
  data class ConfirmWithEmulatedPrompt<R>(
    /**
     * Contextual details about the operation being confirmed.
     * Displayed in the emulated prompt sheet (e.g., transaction amount, action type).
     */
    val details: List<EmulatedPromptOption.Detail> = emptyList(),
    /**
     * Action executed when the user approves the operation.
     */
    val approve: EmulatedPromptOption<R>,
    /**
     * Action executed when the user denies the operation.
     */
    val deny: EmulatedPromptOption<R>,
  ) : HardwareInteraction<R>
}

/**
 * Type-safe factory for [ConfirmationResultMapper] that captures the expected result
 * type [R] at the Kotlin callsite, giving a compile-time error if the block returns
 * a [HardwareInteraction] of the wrong generic type.
 *
 * Use this instead of the raw SAM syntax when constructing [HardwareInteraction.RequiresConfirmation]
 * in Kotlin command implementations:
 * ```
 * HardwareInteraction.RequiresConfirmation(
 *   handles = handles,
 *   mapResult = confirmationResultMapper<Psbt> { result ->
 *     when (result) {
 *       is ConfirmationResult.SignTx -> HardwareInteraction.Completed(buildPsbt(result))
 *       ...
 *     }
 *   }
 * )
 * ```
 *
 * iOS Swift callers use [ConfirmationResultMapper] / `NfcConfirmationResultMapper` directly
 * since the ObjC bridge erases generic parameters anyway.
 */
fun <R> confirmationResultMapper(
  block: (ConfirmationResult) -> HardwareInteraction<R>,
): ConfirmationResultMapper = ConfirmationResultMapper { result -> block(result) }

/**
 * Converts a [HardwareInteraction.RequiresConfirmation] into a typed second-tap session
 * function for use with the NFC transactor or state machine continuation lambdas.
 *
 * This is the canonical way to build a second-tap callback in Kotlin:
 * the function calls [NfcCommands.getConfirmationResult] with the opaque handles
 * and then passes the result to the mapper for command-specific processing — all
 * within a Kotlin coroutine, with no suspend-function crossing the KMM bridge.
 */
fun <T> HardwareInteraction.RequiresConfirmation<T>.toSessionFn():
  suspend (NfcSession, NfcCommands) -> HardwareInteraction<T> =
  { session, commands ->
    val mapped = mapResult.mapResult(commands.getConfirmationResult(session, handles))
    // mapResult returns HardwareInteraction<*>; the cast to HardwareInteraction<T> is
    // unchecked but safe by construction — callers use confirmationResultMapper<T> { }
    // which enforces the correct type at the Kotlin call-site. A runtime check here
    // would be a no-op anyway due to JVM/Native type erasure on generic parameters.
    @Suppress("UNCHECKED_CAST")
    mapped as HardwareInteraction<T>
  }
