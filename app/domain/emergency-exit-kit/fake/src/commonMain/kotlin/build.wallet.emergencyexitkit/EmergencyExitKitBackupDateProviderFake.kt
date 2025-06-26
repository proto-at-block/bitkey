package build.wallet.emergencyexitkit

import kotlinx.datetime.LocalDate

class EmergencyExitKitBackupDateProviderFake : EmergencyExitKitBackupDateProvider {
  override fun backupDate(): LocalDate = LocalDate(2024, 9, 30)
}
