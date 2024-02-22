package build.wallet.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitkey.app.AppAuthPublicKey
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.RotateAuthKeysService
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.keys.AppKeysGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuthKeyRotationManagerImpl(
  private val authKeyRotationAttemptDao: AuthKeyRotationAttemptDao,
  private val appKeysGenerator: AppKeysGenerator,
  private val rotateAuthKeysService: RotateAuthKeysService,
  private val keyboxDao: KeyboxDao,
) : AuthKeyRotationManager {
  @Composable
  override fun startOrResumeAuthKeyRotation(
    hwFactorProofOfPossession: HwFactorProofOfPossession,
    keyboxToRotate: Keybox,
    rotateActiveKeybox: Boolean,
    hwAuthPublicKey: HwAuthPublicKey,
    hwSignedAccountId: String,
  ): AuthKeyRotationRequestState {
    // Use remember to hold onto the state, now that the flow is separately handled
    val attemptState: Result<AuthKeyRotationAttemptDaoState, Throwable>? by remember {
      authKeyRotationAttemptDao.getAuthKeyRotationAttemptState()
    }.collectAsState(initial = null)

    var rotationState: AuthKeyRotationRequestState by remember {
      mutableStateOf(AuthKeyRotationRequestState.Rotating)
    }

    /**
     * This is a bit of a hack to get around the fact that we can't call suspend functions
     * inside callbacks called from LaunchedEffects. So to trigger the side effect we want,
     * we trigger this state change and launch them from the resulting state change handler.
     */
    var inProgress by remember {
      mutableStateOf(true)
    }

    if (inProgress) {
      attemptState?.map {
        when (it) {
          AuthKeyRotationAttemptDaoState.NoAttemptInProgress -> {
            LaunchedEffect("create keys") {
              createKeys(hwAuthPublicKey = hwAuthPublicKey)
                .onFailure {
                  rotationState = AuthKeyRotationRequestState.FailedRotation { inProgress = false }
                }
            }
          }

          is AuthKeyRotationAttemptDaoState.AuthKeysWritten -> {
            LaunchedEffect("rotate keys on server") {
              rotateKeysOnServer(
                f8eEnvironment = keyboxToRotate.config.f8eEnvironment,
                fullAccountId = keyboxToRotate.fullAccountId,
                newAppAuthPublicKeys = it.appAuthPublicKeys,
                oldAppAuthPublicKey = keyboxToRotate.activeKeyBundle.authKey,
                hwAuthPublicKey = it.hwAuthPublicKey,
                hwSignedAccountId = hwSignedAccountId,
                hwFactorProofOfPossession = hwFactorProofOfPossession
              ).onFailure {
                rotationState = AuthKeyRotationRequestState.FailedRotation { inProgress = false }
              }
            }
          }

          is AuthKeyRotationAttemptDaoState.ServerRotationAttemptComplete -> {
            LaunchedEffect("rotate keys locally ") {
              rotateKeysLocally(
                appAuthPublicKeys = it.appAuthPublicKeys,
                keyboxToRotate = keyboxToRotate,
                shouldAttemptLocalKeyboxRotation = rotateActiveKeybox
              ).onSuccess { rotatedKeybox ->
                rotationState =
                  AuthKeyRotationRequestState.FinishedRotation(rotatedKeybox) {
                    inProgress = false
                  }
              }.onFailure {
                rotationState =
                  AuthKeyRotationRequestState.FailedRotation {
                    inProgress = false
                  }
              }
            }
          }
        }
      }
    } else {
      LaunchedEffect("clear dao attempt") {
        authKeyRotationAttemptDao.clear()
      }
    }

    return rotationState
  }

  override suspend fun getKeyRotationStatus(): Flow<Result<AuthKeyRotationAttemptState, Throwable>> {
    return authKeyRotationAttemptDao.getAuthKeyRotationAttemptState().map { result ->
      result.map {
        when (it) {
          is AuthKeyRotationAttemptDaoState.AuthKeysWritten -> AuthKeyRotationAttemptState.AttemptInProgress
          AuthKeyRotationAttemptDaoState.NoAttemptInProgress -> AuthKeyRotationAttemptState.NoAttemptInProgress
          is AuthKeyRotationAttemptDaoState.ServerRotationAttemptComplete -> AuthKeyRotationAttemptState.AttemptInProgress
        }
      }
    }
  }

  private suspend fun createKeys(
    hwAuthPublicKey: HwAuthPublicKey,
  ): Result<AppAuthPublicKeys, Throwable> =
    binding {
      val appGlobalAuthPublicKey = appKeysGenerator.generateGlobalAuthKey().bind()

      val appAuthPublicKeys =
        appKeysGenerator.generateRecoveryAuthKey().map {
            appRecoveryAuthPublicKey ->
          AppAuthPublicKeys(
            appGlobalAuthPublicKey = appGlobalAuthPublicKey,
            appRecoveryAuthPublicKey = appRecoveryAuthPublicKey
          )
        }.bind()

      authKeyRotationAttemptDao.setAuthKeysWritten(
        appAuthPublicKeys,
        hwAuthPublicKey = hwAuthPublicKey
      ).bind()

      appAuthPublicKeys
    }

  private suspend fun rotateKeysOnServer(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    newAppAuthPublicKeys: AppAuthPublicKeys,
    oldAppAuthPublicKey: AppAuthPublicKey,
    hwAuthPublicKey: HwAuthPublicKey,
    hwSignedAccountId: String,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Throwable> {
    return rotateAuthKeysService.rotateKeyset(
      f8eEnvironment = f8eEnvironment,
      fullAccountId = fullAccountId,
      newAppAuthPublicKeys = newAppAuthPublicKeys,
      oldAppAuthPublicKey = oldAppAuthPublicKey,
      hwAuthPublicKey = hwAuthPublicKey,
      hwSignedAccountId = hwSignedAccountId,
      hwFactorProofOfPossession = hwFactorProofOfPossession
    ).flatMap {
      authKeyRotationAttemptDao.setServerRotationAttemptComplete()
    }
  }

  private suspend fun rotateKeysLocally(
    appAuthPublicKeys: AppAuthPublicKeys,
    keyboxToRotate: Keybox,
    shouldAttemptLocalKeyboxRotation: Boolean,
  ): Result<Keybox, Throwable> =
    binding {
      val keybox: Keybox =
        if (shouldAttemptLocalKeyboxRotation) {
          keyboxDao.rotateKeyboxAuthKeys(keyboxToRotate, appAuthPublicKeys).bind()
        } else {
          val newKeybox =
            keyboxToRotate.copy(
              activeKeyBundle =
                keyboxToRotate.activeKeyBundle.copy(
                  authKey = appAuthPublicKeys.appGlobalAuthPublicKey,
                  recoveryAuthKey = appAuthPublicKeys.appRecoveryAuthPublicKey
                )
            )
          newKeybox
        }

      keybox
    }
}
