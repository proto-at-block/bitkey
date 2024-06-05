package build.wallet.di

import build.wallet.nfc.NfcTransactor
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachine
import build.wallet.statemachine.root.AppUiStateMachine

/**
 * Object graph that provides dependencies which are still used by platform apps.
 * As more logic is moved into shared state machines, the footprint of this interface will shrink -
 * ultimate goal of this interface is to only provide [AppUiStateMachine] and perhaps few other
 * dependencies.
 *
 * Scoped to the application's lifecycle.
 */
interface ActivityComponent {
  val appUiStateMachine: AppUiStateMachine
  val nfcTransactor: NfcTransactor
  val biometricPromptUiStateMachine: BiometricPromptUiStateMachine
}
