package build.wallet.emergencyexitkit

import kotlinx.datetime.LocalDate

interface EmergencyExitKitBackupDateProvider {
  fun backupDate(): LocalDate
}
