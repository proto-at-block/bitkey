package build.wallet.notifications

import app.cash.turbine.test
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.email.Email
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.phonenumber.PhoneNumber
import build.wallet.phonenumber.PhoneNumberValidatorMock
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class NotificationTouchpointDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var phoneNumberValidator: PhoneNumberValidatorMock
  lateinit var dao: NotificationTouchpointDao

  beforeTest {
    phoneNumberValidator = PhoneNumberValidatorMock()
    dao =
      NotificationTouchpointDaoImpl(
        databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory),
        phoneNumberValidator = phoneNumberValidator
      )
  }

  test("Phone number flow") {
    val phone1 =
      PhoneNumber(
        countryDialingCode = 1,
        formattedDisplayValue = "(555) 555-5555",
        formattedE164Value = "+15555555555"
      )
    val phone2 =
      PhoneNumber(
        countryDialingCode = 1,
        formattedDisplayValue = "(222) 222-2222",
        formattedE164Value = "+12222222222"
      )

    dao.phoneNumber().test {
      awaitItem().shouldBeNull()

      phoneNumberValidator.validatePhoneNumberResult = phone1
      dao.storeTouchpoint(PhoneNumberTouchpoint("id-1", phone1))
      awaitItem().shouldNotBeNull().shouldBe(phone1)

      phoneNumberValidator.validatePhoneNumberResult = phone2
      dao.storeTouchpoint(PhoneNumberTouchpoint("id-2", phone2))
      awaitItem().shouldNotBeNull().shouldBe(phone2)

      dao.clear()
      awaitItem().shouldBeNull()
    }
  }

  test("Email flow") {
    val email1 = Email(value = "dwayne@wade.com")
    val email2 = Email(value = "allen@iverson.com")

    dao.email().test {
      awaitItem().shouldBeNull()

      dao.storeTouchpoint(EmailTouchpoint("id-1", email1))
      awaitItem().shouldBe(email1)

      dao.storeTouchpoint(EmailTouchpoint("id-2", email2))
      awaitItem().shouldBe(email2)

      dao.clear()
      awaitItem().shouldBeNull()
    }
  }
})
