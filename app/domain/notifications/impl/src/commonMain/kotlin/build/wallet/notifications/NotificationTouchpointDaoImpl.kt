package build.wallet.notifications

import bitkey.notifications.NotificationTouchpoint
import bitkey.notifications.NotificationTouchpoint.EmailTouchpoint
import bitkey.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.email.Email
import build.wallet.logging.logFailure
import build.wallet.phonenumber.PhoneNumber
import build.wallet.phonenumber.PhoneNumberValidator
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapOr
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class NotificationTouchpointDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val phoneNumberValidator: PhoneNumberValidator,
) : NotificationTouchpointDao {
  override suspend fun storeTouchpoint(touchpoint: NotificationTouchpoint): Result<Unit, DbError> {
    return when (touchpoint) {
      is PhoneNumberTouchpoint ->
        setPhoneNumber(
          touchpointId = touchpoint.touchpointId,
          phoneNumber = touchpoint.value
        )
      is EmailTouchpoint ->
        setEmail(
          touchpointId = touchpoint.touchpointId,
          email = touchpoint.value
        )
    }
  }

  override fun phoneTouchpoint(): Flow<PhoneNumberTouchpoint?> {
    return flow {
      databaseProvider.database()
        .phoneNumberTouchpointQueries
        .getAllPhoneNumbers()
        .asFlowOfList()
        .map { result ->
          result
            .logFailure { "Failed to fetch stored phone number touchpoint" }
            .mapOr(null) { entities ->
              entities.firstOrNull()?.let { entity ->
                val phoneNumber = phoneNumberValidator.validatePhoneNumber(
                  number = entity.phoneNumber
                )
                phoneNumber?.let {
                  PhoneNumberTouchpoint(
                    touchpointId = entity.touchpointId,
                    value = it
                  )
                }
              }
            }
        }
        .distinctUntilChanged()
        .collect(::emit)
    }
  }

  override fun emailTouchpoint(): Flow<EmailTouchpoint?> {
    return flow {
      databaseProvider.database()
        .emailTouchpointQueries
        .getAllEmails()
        .asFlowOfList()
        .map { result ->
          result
            .logFailure { "Failed to fetch stored email touchpoint" }
            .mapOr(null) { entities ->
              entities.firstOrNull()?.let { entity ->
                EmailTouchpoint(
                  touchpointId = entity.touchpointId,
                  value = entity.email
                )
              }
            }
        }
        .distinctUntilChanged()
        .collect(::emit)
    }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        phoneNumberTouchpointQueries.clear()
        emailTouchpointQueries.clear()
      }
      .logFailure { "Failed to clear notification touchpoint tables" }
  }

  private suspend fun setPhoneNumber(
    touchpointId: String,
    phoneNumber: PhoneNumber,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        // We only support 1 phone number right now.
        // Clear any stored phone numbers before setting new one.
        phoneNumberTouchpointQueries.clear()
        phoneNumberTouchpointQueries.setPhoneNumber(
          touchpointId = touchpointId,
          phoneNumber = phoneNumber.formattedE164Value
        )
      }
      .logFailure { "Failed to set phone number on account" }
  }

  private suspend fun setEmail(
    touchpointId: String,
    email: Email,
  ): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      emailTouchpointQueries.clear()
      emailTouchpointQueries.setEmail(
        touchpointId = touchpointId,
        email = email
      )
    }.logFailure { "Error clearing + saving email touchpoint to db" }
  }
}
