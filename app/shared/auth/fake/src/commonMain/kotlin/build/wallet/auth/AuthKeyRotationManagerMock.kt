package build.wallet.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class AuthKeyRotationManagerMock(
  turbine: (String) -> Turbine<Any>,
) : AuthKeyRotationManager {
  val rotateAuthKeysCalls = turbine("rotate auth keys calls AuthKeyRotationManagerMock")
  val model: MutableStateFlow<AuthKeyRotationRequestState> = MutableStateFlow(AuthKeyRotationRequestState.Rotating)

  val getKeyRotationStatusCalls = turbine("get key rotation status calls AuthKeyRotationManagerMock")
  var getKeyRotationStatusResult = Ok(AuthKeyRotationAttemptState.AttemptInProgress)

  @Composable
  override fun startOrResumeAuthKeyRotation(
    hwFactorProofOfPossession: HwFactorProofOfPossession,
    keyboxToRotate: Keybox,
    rotateActiveKeybox: Boolean,
    hwAuthPublicKey: HwAuthPublicKey,
    hwSignedAccountId: String,
  ): AuthKeyRotationRequestState {
    rotateAuthKeysCalls += Unit
    return model.collectAsState().value
  }

  override suspend fun getKeyRotationStatus(): Flow<Result<AuthKeyRotationAttemptState, Throwable>> {
    getKeyRotationStatusCalls += Unit
    return flowOf(getKeyRotationStatusResult)
  }

  fun reset() {
    model.value = AuthKeyRotationRequestState.Rotating
  }
}
