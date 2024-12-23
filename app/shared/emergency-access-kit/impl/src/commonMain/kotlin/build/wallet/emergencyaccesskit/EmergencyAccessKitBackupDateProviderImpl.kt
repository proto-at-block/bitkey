package build.wallet.emergencyaccesskit

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.time.TimeZoneProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(AppScope::class)
class EmergencyAccessKitBackupDateProviderImpl(
  private val clock: Clock,
  private val timeZoneProvider: TimeZoneProvider,
) : EmergencyAccessKitBackupDateProvider {
  override fun backupDate(): LocalDate =
    clock
      .now()
      .toLocalDateTime(timeZoneProvider.current())
      .date
}
