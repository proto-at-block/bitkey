package build.wallet.nfc.transaction

import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.nfc.HardwareProvisionedAppKeyStatusDaoFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue

class ProvisionAppAuthKeyTransactionProviderImplTests : FunSpec({
  val provider = ProvisionAppAuthKeyTransactionProviderImpl(
    hardwareProvisionedAppKeyStatusDao = HardwareProvisionedAppKeyStatusDaoFake()
  )

  test("transaction requires authentication, locks device, and shows device confirmation") {
    val transaction = provider(
      appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
      onSuccess = {},
      onCancel = {}
    )

    transaction.needsAuthentication.shouldBeTrue()
    transaction.shouldLock.shouldBeTrue()
    transaction.showDeviceConfirmation.shouldBeTrue()
  }
})
