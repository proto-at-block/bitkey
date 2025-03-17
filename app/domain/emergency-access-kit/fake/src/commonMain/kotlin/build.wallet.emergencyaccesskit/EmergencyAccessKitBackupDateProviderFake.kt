package build.wallet.emergencyaccesskit

import kotlinx.datetime.LocalDate

class EmergencyAccessKitBackupDateProviderFake : EmergencyAccessKitBackupDateProvider {
  override fun backupDate(): LocalDate = LocalDate(2024, 9, 30)
}
