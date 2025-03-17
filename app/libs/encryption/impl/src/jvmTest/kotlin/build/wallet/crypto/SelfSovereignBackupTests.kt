package build.wallet.crypto

import build.wallet.encrypt.HkdfImpl
import build.wallet.encrypt.SymmetricKeyEncryptorImpl
import build.wallet.secureenclave.SecureEnclaveFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import okio.ByteString.Companion.toByteString

class SelfSovereignBackupTests :
  FunSpec({
    val ssb = SelfSovereignBackupImpl(
      SecureEnclaveFake(),
      SymmetricKeyEncryptorImpl(),
      HkdfImpl(),
      skipAuthenticationForKeysInTestOnly = true
    )

    test("generated and exported pubkeys are the same") {
      val keys = ssb.generateLocalWrappingKeys()
      val exportedKeys = ssb.exportLocalWrappingPublicKeys()
      keys.lkaPub.bytes.toByteString() shouldBeEqual exportedKeys.lkaPub.bytes.toByteString()
      keys.lknPub.bytes.toByteString() shouldBeEqual exportedKeys.lknPub.bytes.toByteString()
    }

    test("key rotation generates a new key") {
      val keys = ssb.generateLocalWrappingKeys()
      val keys2 = ssb.rotateLocalWrappingKeyWithoutAuth()

      keys.lkaPub.bytes.toByteString() shouldBeEqual keys2.lkaPub.bytes.toByteString()
      keys.lknPub.bytes.toByteString() shouldNotBeEqual keys2.lknPub.bytes.toByteString()
    }

    // Note: DH can't be meaningfully tested without some kind of real DH implementation,
    // so decryptServerKeyShare is not tested here. See SelfSovereignBackupInstrumentedTest.
  })
