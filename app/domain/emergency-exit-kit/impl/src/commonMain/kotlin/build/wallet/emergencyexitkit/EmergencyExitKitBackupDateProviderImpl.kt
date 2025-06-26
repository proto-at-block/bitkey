package build.wallet.emergencyexitkit

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.time.TimeZoneProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(AppScope::class)
class EmergencyExitKitBackupDateProviderImpl(
  private val clock: Clock,
  private val timeZoneProvider: TimeZoneProvider,
) : EmergencyExitKitBackupDateProvider {
  override fun backupDate(): LocalDate =
    clock
      .now()
      .toLocalDateTime(timeZoneProvider.current())
      .date
}
