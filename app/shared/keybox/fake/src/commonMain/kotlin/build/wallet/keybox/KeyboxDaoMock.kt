package build.wallet.keybox

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMapBoth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class KeyboxDaoMock(
  turbine: (name: String) -> Turbine<Unit>,
  private val defaultActiveKeybox: Keybox? = null,
  private val defaultOnboardingKeybox: Keybox? = null,
) : KeyboxDao {
  val clearCalls = turbine("clear calls KeyboxDaoMock")
  val rotateAuthKeysCalls = turbine("rotate auth keys calls")

  val activeKeybox = MutableStateFlow<Result<Keybox?, DbError>>(Ok(defaultActiveKeybox))
  val onboardingKeybox =
    MutableStateFlow<Result<Keybox?, DbError>>(Ok(defaultOnboardingKeybox))

  var rotateKeyboxResult: Result<Keybox, DbError> = Ok(KeyboxMock)

  var keyset: SpendingKeyset? = null

  override fun activeKeybox(): Flow<Result<Keybox?, DbError>> = activeKeybox

  override fun onboardingKeybox(): Flow<Result<Keybox?, DbError>> = onboardingKeybox

  override suspend fun getActiveOrOnboardingKeybox(): Result<Keybox?, DbError> {
    val activeKeyboxResult = activeKeybox().first()
    return activeKeyboxResult.flatMapBoth(
      success = { activeKeybox ->
        when (activeKeybox) {
          null -> onboardingKeybox().first()
          else -> activeKeyboxResult
        }
      },
      failure = { activeKeyboxResult }
    )
  }

  override suspend fun activateNewKeyboxAndCompleteOnboarding(
    keybox: Keybox,
  ): Result<Unit, DbError> {
    this.activeKeybox.value = Ok(keybox)
    this.onboardingKeybox.value = Ok(value = null)
    return Ok(Unit)
  }

  override suspend fun rotateKeyboxAuthKeys(
    keyboxToRotate: Keybox,
    appAuthKeys: AppAuthPublicKeys,
  ): Result<Keybox, DbError> {
    rotateAuthKeysCalls += Unit
    return rotateKeyboxResult.also {
      if (it.isOk) {
        activeKeybox.value = Ok(it.value)
      }
    }
  }

  override suspend fun saveKeyboxAndBeginOnboarding(keybox: Keybox): Result<Unit, DbError> {
    this.onboardingKeybox.value = Ok(keybox)
    return Ok(Unit)
  }

  override suspend fun saveKeyboxAsActive(keybox: Keybox): Result<Unit, DbError> {
    this.activeKeybox.value = Ok(keybox)
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, DbError> {
    clearCalls += Unit
    activeKeybox.value = Ok(null)
    return Ok(Unit)
  }

  fun reset() {
    onboardingKeybox.value = Ok(defaultOnboardingKeybox)
    activeKeybox.value = Ok(defaultActiveKeybox)
    keyset = null
    rotateKeyboxResult = Ok(KeyboxMock)
  }
}
