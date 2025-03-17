package build.wallet.logging.di

import build.wallet.di.AppScope
import build.wallet.logging.DatadogLogWriter
import co.touchlab.kermit.LogWriter
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface AndroidLoggingComponent {
  // TODO: consider using multi-bindings - https://github.com/amzn/kotlin-inject-anvil?tab=readme-ov-file#multi-bindings
  @Provides
  fun provideAdditionalLogWriters(datadogLogWriter: DatadogLogWriter): List<LogWriter> =
    listOf(datadogLogWriter)
}
