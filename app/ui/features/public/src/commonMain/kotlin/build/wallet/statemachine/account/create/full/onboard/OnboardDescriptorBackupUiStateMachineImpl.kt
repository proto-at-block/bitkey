package build.wallet.statemachine.account.create.full.onboard

import androidx.compose.runtime.*
import bitkey.recovery.DescriptorBackupService
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.SekGenerator
import build.wallet.cloud.backup.csek.Ssek
import build.wallet.cloud.backup.csek.SsekDao
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.onboarding.OnboardingKeyboxSealedSsekDao
import build.wallet.statemachine.cloud.SAVING_BACKUP_MESSAGE
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class OnboardDescriptorBackupUiStateMachineImpl(
  private val descriptorBackupService: DescriptorBackupService,
  private val sekGenerator: SekGenerator,
  private val ssekDao: SsekDao,
  private val onboardingKeyboxSealedSsekDao: OnboardingKeyboxSealedSsekDao,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
) : OnboardDescriptorBackupUiStateMachine {
  @Composable
  override fun model(props: OnboardDescriptorBackupUiProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(
        if (props.sealedSsek != null) {
          State.UploadingBackup(props.sealedSsek)
        } else {
          State.GeneratingSsek
        }
      )
    }

    return when (val currentState = state) {
      is State.GeneratingSsek -> {
        LaunchedEffect("generate-ssek") {
          val generatedSsek = sekGenerator.generate()
          state = State.SealingSsek(generatedSsek)
        }

        LoadingBodyModel(
          id = NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING
        ).asRootScreen()
      }
      is State.SealingSsek -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              commands.sealData(session, currentState.unsealedSsek.key.raw)
            },
            onSuccess = { sealedSsek ->
              ssekDao.set(sealedSsek, currentState.unsealedSsek)
                .onSuccess {
                  onboardingKeyboxSealedSsekDao.set(sealedSsek)
                    .onSuccess {
                      state = State.UploadingBackup(sealedSsek)
                    }
                    .onFailure { storeError ->
                      ssekDao.clear()
                      props.onBackupFailed(storeError)
                    }
                }
                .onFailure { onboardingError ->
                  props.onBackupFailed(onboardingError)
                }
            },
            onCancel = {
              props.onBackupFailed(Error("User cancelled sealing SSEK NFC session"))
            },
            screenPresentationStyle = ScreenPresentationStyle.Root,
            eventTrackerContext = NfcEventTrackerScreenIdContext.SEAL_SSEK
          )
        )
      }
      is State.UploadingBackup -> {
        LaunchedEffect("upload-descriptor-backup") {
          descriptorBackupService.uploadOnboardingDescriptorBackup(
            accountId = props.fullAccount.accountId,
            sealedSsekForEncryption = currentState.sealedSsek,
            appAuthKey = props.fullAccount.keybox.activeAppKeyBundle.authKey,
            keysetsToEncrypt = props.fullAccount.keybox.keysets
          )
            .onSuccess {
              props.onBackupComplete()
            }
            .onFailure {
              props.onBackupFailed(it)
            }
        }

        LoadingBodyModel(
          id = NEW_ACCOUNT_DESCRIPTOR_BACKUP_LOADING,
          message = SAVING_BACKUP_MESSAGE
        ).asRootScreen()
      }
    }
  }

  private sealed interface State {
    data object GeneratingSsek : State

    data class SealingSsek(val unsealedSsek: Ssek) : State

    data class UploadingBackup(val sealedSsek: SealedSsek) : State
  }
}
