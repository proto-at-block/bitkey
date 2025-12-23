package build.wallet.platform.age

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.google.android.play.agesignals.AgeSignalsException
import com.google.android.play.agesignals.AgeSignalsManager
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.model.AgeSignalsErrorCode.API_NOT_AVAILABLE
import com.google.android.play.agesignals.model.AgeSignalsErrorCode.APP_NOT_OWNED
import com.google.android.play.agesignals.model.AgeSignalsErrorCode.CANNOT_BIND_TO_SERVICE
import com.google.android.play.agesignals.model.AgeSignalsErrorCode.CLIENT_TRANSIENT_ERROR
import com.google.android.play.agesignals.model.AgeSignalsErrorCode.INTERNAL_ERROR
import com.google.android.play.agesignals.model.AgeSignalsErrorCode.NETWORK_ERROR
import com.google.android.play.agesignals.model.AgeSignalsErrorCode.PLAY_SERVICES_NOT_FOUND
import com.google.android.play.agesignals.model.AgeSignalsErrorCode.PLAY_SERVICES_VERSION_OUTDATED
import com.google.android.play.agesignals.model.AgeSignalsErrorCode.PLAY_STORE_NOT_FOUND
import com.google.android.play.agesignals.model.AgeSignalsErrorCode.PLAY_STORE_VERSION_OUTDATED
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus.SUPERVISED
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus.UNKNOWN
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus.VERIFIED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android implementation using Play Age Signals API.
 */
@BitkeyInject(AppScope::class)
class AgeSignalsServiceImpl(
  private val ageSignalsManager: AgeSignalsManager,
) : AgeSignalsService {
  override suspend fun checkAgeSignals(): AgeSignalsResponse {
    return withContext(Dispatchers.IO) {
      try {
        val request = AgeSignalsRequest.builder().build()
        val response = ageSignalsManager.checkAgeSignals(request).await()

        when (response.userStatus()) {
          VERIFIED -> AgeSignalsResponse.Verified
          SUPERVISED -> AgeSignalsResponse.Supervised
          SUPERVISED_APPROVAL_PENDING -> AgeSignalsResponse.Supervised
          SUPERVISED_APPROVAL_DENIED -> AgeSignalsResponse.Supervised
          UNKNOWN -> AgeSignalsResponse.Unknown
          null -> AgeSignalsResponse.NotApplicable
          else -> AgeSignalsResponse.NotApplicable
        }
      } catch (e: AgeSignalsException) {
        AgeSignalsResponse.Error(errorCode = e.errorCodeName(), message = e.message, cause = e)
      } catch (e: CancellationException) {
        throw e
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        AgeSignalsResponse.Error(errorCode = "UNEXPECTED", message = e.message, cause = e)
      }
    }
  }

  private fun AgeSignalsException.errorCodeName(): String =
    when (errorCode) {
      API_NOT_AVAILABLE -> "API_NOT_AVAILABLE"
      PLAY_STORE_NOT_FOUND -> "PLAY_STORE_NOT_FOUND"
      NETWORK_ERROR -> "NETWORK_ERROR"
      PLAY_SERVICES_NOT_FOUND -> "PLAY_SERVICES_NOT_FOUND"
      CANNOT_BIND_TO_SERVICE -> "CANNOT_BIND_TO_SERVICE"
      PLAY_STORE_VERSION_OUTDATED -> "PLAY_STORE_VERSION_OUTDATED"
      PLAY_SERVICES_VERSION_OUTDATED -> "PLAY_SERVICES_VERSION_OUTDATED"
      CLIENT_TRANSIENT_ERROR -> "CLIENT_TRANSIENT_ERROR"
      APP_NOT_OWNED -> "APP_NOT_OWNED"
      INTERNAL_ERROR -> "INTERNAL_ERROR"
      else -> "UNKNOWN_ERROR"
    }
}
