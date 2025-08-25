package build.wallet.statemachine.ui.robots

import bitkey.securitycenter.SecurityActionType
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import io.kotest.matchers.nulls.shouldNotBeNull

fun SecurityHubBodyModel.clickBitkeyDevice() {
  securityActions
    .find { it.type() == SecurityActionType.HARDWARE_DEVICE }
    .shouldNotBeNull()
    .let { hardwareAction ->
      onSecurityActionClick(hardwareAction)
    }
}

fun SecurityHubBodyModel.clickFingerprints() {
  securityActions
    .find { it.type() == SecurityActionType.FINGERPRINTS }
    .shouldNotBeNull()
    .let { hardwareAction ->
      onSecurityActionClick(hardwareAction)
    }
}

fun SecurityHubBodyModel.clickRecoveryContacts() {
  recoveryActions
    .find { it.type() == SecurityActionType.SOCIAL_RECOVERY }
    .shouldNotBeNull()
    .let { hardwareAction ->
      onSecurityActionClick(hardwareAction)
    }
}

fun SecurityHubBodyModel.clickAppCloudBackup() {
  recoveryActions
    .find { it.type() == SecurityActionType.APP_KEY_BACKUP }
    .shouldNotBeNull()
    .let { hardwareAction ->
      onSecurityActionClick(hardwareAction)
    }
}
