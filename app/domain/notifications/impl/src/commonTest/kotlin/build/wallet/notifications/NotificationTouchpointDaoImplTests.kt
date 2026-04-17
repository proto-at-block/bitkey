package build.wallet.notifications

import app.cash.turbine.test
import bitkey.notifications.NotificationTouchpoint.EmailTouchpoint
import bitkey.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.email.Email
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

  test("Phone touchpoint flow returns value and ID") {
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

    dao.phoneTouchpoint().test {
      awaitItem().shouldBeNull()

      phoneNumberValidator.validatePhoneNumberResult = phone1
      dao.storeTouchpoint(PhoneNumberTouchpoint("tp-1", phone1))
      with(awaitItem().shouldNotBeNull()) {
        value.shouldBe(phone1)
        touchpointId.shouldBe("tp-1")
      }

      phoneNumberValidator.validatePhoneNumberResult = phone2
      dao.storeTouchpoint(PhoneNumberTouchpoint("tp-2", phone2))
      with(awaitItem().shouldNotBeNull()) {
        value.shouldBe(phone2)
        touchpointId.shouldBe("tp-2")
      }

      dao.clear()
      awaitItem().shouldBeNull()
    }
  }

  test("Email touchpoint flow returns value and ID") {
    val email1 = Email(value = "dwayne@wade.com")
    val email2 = Email(value = "allen@iverson.com")

    dao.emailTouchpoint().test {
      awaitItem().shouldBeNull()

      dao.storeTouchpoint(EmailTouchpoint("tp-1", email1))
      with(awaitItem().shouldNotBeNull()) {
        value.shouldBe(email1)
        touchpointId.shouldBe("tp-1")
      }

      dao.storeTouchpoint(EmailTouchpoint("tp-2", email2))
      with(awaitItem().shouldNotBeNull()) {
        value.shouldBe(email2)
        touchpointId.shouldBe("tp-2")
      }

      dao.clear()
      awaitItem().shouldBeNull()
    }
  }
})
