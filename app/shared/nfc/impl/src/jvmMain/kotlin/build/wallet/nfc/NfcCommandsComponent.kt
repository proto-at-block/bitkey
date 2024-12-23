package build.wallet.nfc

import build.wallet.di.AppScope
import build.wallet.di.Fake
import build.wallet.di.Impl
import build.wallet.nfc.platform.NfcCommands
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface NfcCommandsComponent {
  /**
   * Bind fake implementation to [NfcCommands].
   * In JVM tests we only use fake implementation of [NfcCommands].
   */
  @Provides
  fun provideNfcCommandsImpl(
    @Fake fake: NfcCommands,
  ): @Impl NfcCommands = fake
}
