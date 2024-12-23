package build.wallet.logging.bugsnag.di

import build.wallet.di.AppScope
import co.touchlab.kermit.LogWriter
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface JvmLoggingComponent {
  @Provides
  fun provideAdditionalLogWriters(): List<LogWriter> = emptyList()
}
