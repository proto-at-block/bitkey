package build.wallet.nfc.interceptors

import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderMock
import build.wallet.bitcoin.wallet.SpendingWalletProvider
import build.wallet.db.DbError
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.Secp256k1PrivateKey
import build.wallet.encrypt.SignatureUtils
import build.wallet.firmware.CoredumpFragment
import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import build.wallet.nfc.BitkeyW1CommandsFake
import build.wallet.nfc.BitkeyW3CommandsFake
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.nfc.FakeHardwareKeyStoreFake
import build.wallet.nfc.FakeHardwareSpendingWalletProvider
import build.wallet.nfc.FakeHardwareStatesDao
import build.wallet.nfc.NfcSessionFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.toByteString

class FirmwareTelemetryInterceptorCoredumpW3CommandsFakeTests : FunSpec({
  test("returns null when BitkeyW3CommandsFake reports zero coredumps") {
    val w1Commands = createBitkeyW1CommandsFake()
    val w3Commands = BitkeyW3CommandsFake(w1Commands)
    val session = NfcSessionFake()

    val result = readFirmwareTelemetryCoredumpForMcu(w3Commands, session, mcuRole = McuRole.CORE)

    result.shouldBe(null)
  }

  test("concatenates fragments in order and advances offsets until complete (BitkeyW3CommandsFake)") {
    val w1Commands = createBitkeyW1CommandsFake()
    val w3Commands = BitkeyW3CommandsFake(w1Commands)
    val session = NfcSessionFake()

    w1Commands.setTelemetryCoredump(
      coredumpCount = 1,
      fragmentsByOffset =
        mapOf(
          0 to
            CoredumpFragment(
              data = listOf(0x0Au, 0x0Bu),
              offset = 2,
              complete = false,
              coredumpsRemaining = 0,
              mcuRole = McuRole.CORE,
              mcuName = McuName.EFR32
            ),
          2 to
            CoredumpFragment(
              data = listOf(0x0Cu, 0x0Du),
              offset = 4,
              complete = true,
              coredumpsRemaining = 0,
              mcuRole = McuRole.UXC,
              mcuName = McuName.STM32U5
            )
        )
    )

    val result = readFirmwareTelemetryCoredumpForMcu(w3Commands, session, mcuRole = McuRole.CORE)

    result.shouldBe(byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D).toByteString())
  }
})

private fun createBitkeyW1CommandsFake(): BitkeyW1CommandsFake {
  val messageSigner =
    object : MessageSigner {
      override fun sign(
        message: ByteString,
        key: Secp256k1PrivateKey,
      ): String = "not-used"
    }

  val signatureUtils =
    object : SignatureUtils {
      override fun encodeSignatureToDer(compactSignature: ByteArray): ByteString = ByteString.EMPTY

      override fun decodeSignatureFromDer(derSignature: ByteString): ByteArray = ByteArray(64)
    }

  val fakeHardwareKeyStore: FakeHardwareKeyStore = FakeHardwareKeyStoreFake()

  val spendingWalletProvider = SpendingWalletProvider { Err(Throwable("Not used in this test")) }
  val descriptorBuilder = BitcoinMultiSigDescriptorBuilderMock()
  val fakeHardwareSpendingWalletProvider =
    FakeHardwareSpendingWalletProvider(
      spendingWalletProvider = spendingWalletProvider,
      descriptorBuilder = descriptorBuilder,
      fakeHardwareKeyStore = fakeHardwareKeyStore
    )

  val fakeHardwareStatesDao =
    object : FakeHardwareStatesDao {
      override suspend fun setTransactionVerificationEnabled(
        enabled: Boolean,
      ): Result<Unit, DbError> = Ok(Unit)

      override suspend fun getTransactionVerificationEnabled(): Result<Boolean?, DbError> = Ok(null)

      override suspend fun clear(): Result<Unit, DbError> = Ok(Unit)
    }

  return BitkeyW1CommandsFake(
    messageSigner = messageSigner,
    signatureUtils = signatureUtils,
    fakeHardwareKeyStore = fakeHardwareKeyStore,
    fakeHardwareSpendingWalletProvider = fakeHardwareSpendingWalletProvider,
    fakeHardwareStatesDao = fakeHardwareStatesDao
  ).also {
    it.clearTelemetryCoredump()
  }
}
