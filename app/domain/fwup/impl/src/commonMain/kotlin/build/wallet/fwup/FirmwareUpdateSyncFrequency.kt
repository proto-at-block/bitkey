package build.wallet.fwup

import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@JvmInline
value class FirmwareUpdateSyncFrequency(val value: Duration = 1.hours) {
  @ContributesTo(AppScope::class)
  interface Component {
    @Provides
    fun provideFirmwareUpdateSyncFrequency() = FirmwareUpdateSyncFrequency()
  }
}
