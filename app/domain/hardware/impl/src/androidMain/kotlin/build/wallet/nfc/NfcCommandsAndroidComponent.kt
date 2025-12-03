package build.wallet.nfc

import build.wallet.di.AppScope
import build.wallet.di.Impl
import build.wallet.di.W3
import build.wallet.nfc.platform.NfcCommands
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface NfcCommandsAndroidComponent {
  /**
   * Provide W3 implementation by wrapping the W1 implementation.
   */
  @Provides
  fun provideNfcCommandsW3(
    @Impl w1Impl: NfcCommands,
  ): @W3 NfcCommands = BitkeyW3Commands(w1Impl)
}
