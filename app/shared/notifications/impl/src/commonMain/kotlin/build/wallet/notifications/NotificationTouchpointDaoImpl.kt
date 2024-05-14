package build.wallet.notifications

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.email.Email
import build.wallet.logging.logFailure
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.phonenumber.PhoneNumber
import build.wallet.phonenumber.PhoneNumberValidator
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapOr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class NotificationTouchpointDaoImpl(
  databaseProvider: BitkeyDatabaseProvider,
  private val phoneNumberValidator: PhoneNumberValidator,
) : NotificationTouchpointDao {
  private val database = databaseProvider.database()

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

  override fun phoneNumber(): Flow<PhoneNumber?> {
    return database
      .phoneNumberTouchpointQueries
      .getAllPhoneNumbers()
      .asFlowOfList()
      .map { result ->
        result
          .logFailure { "Failed to fetch stored phone number" }
          .mapOr(null) { phoneNumberEntities ->
            phoneNumberEntities.firstOrNull()?.let { phoneNumberEntity ->
              phoneNumberValidator.validatePhoneNumber(
                number = phoneNumberEntity.phoneNumber
              )
            }
          }
      }
      .distinctUntilChanged()
  }

  override fun email(): Flow<Email?> {
    return database.emailTouchpointQueries
      .getAllEmails()
      .asFlowOfList()
      .map { result ->
        result
          .logFailure { "Failed to fetch stored email" }
          .mapOr(null) { entities ->
            entities.firstOrNull()?.email
          }
      }
      .distinctUntilChanged()
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return database
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
    return database
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
    return database.awaitTransaction {
      emailTouchpointQueries.clear()
      emailTouchpointQueries.setEmail(
        touchpointId = touchpointId,
        email = email
      )
    }.logFailure { "Error clearing + saving email touchpoint to db" }
  }
}
