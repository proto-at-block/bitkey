package build.wallet.emergencyaccesskit

import build.wallet.time.TimeZoneProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

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
