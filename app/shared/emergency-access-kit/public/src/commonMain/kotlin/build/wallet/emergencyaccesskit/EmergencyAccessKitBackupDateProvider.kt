package build.wallet.emergencyaccesskit

import kotlinx.datetime.LocalDate

interface EmergencyAccessKitBackupDateProvider {
  fun backupDate(): LocalDate
}
