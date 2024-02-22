package build.wallet.cloud.backup.v2

import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.TrustedContactFake1
import build.wallet.bitkey.socrec.TrustedContactFake2
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.crypto.CurveType
import build.wallet.encrypt.SealedDataMock
import build.wallet.encrypt.XCiphertext
import build.wallet.encrypt.XCiphertextMock
import build.wallet.recovery.socrec.SocRecCryptoFake
import com.github.michaelbull.result.getOrThrow

val SocRecSealedPkek1 = XCiphertext(value = "cipherText-1.nonce-1")

val SocRecSealedPkek2 = XCiphertext(value = "cipherText-2.nonce-2")

val ProtectedCustomerIdentityKeyMock =
  ProtectedCustomerIdentityKey(
    AppKeyImpl(
      CurveType.SECP256K1,
      SocRecCryptoFake().generateProtectedCustomerIdentityKey().getOrThrow().publicKey,
      null
    )
  )

val FullAccountFieldsMock =
  FullAccountFields(
    hwEncryptionKeyCiphertext = SealedCsekFake,
    socRecEncryptionKeyCiphertextMap =
      mapOf(
        TrustedContactFake1.recoveryRelationshipId to SocRecSealedPkek1,
        TrustedContactFake2.recoveryRelationshipId to SocRecSealedPkek2
      ),
    isFakeHardware = false,
    hwFullAccountKeysCiphertext = SealedDataMock,
    socRecFullAccountKeysCiphertext = XCiphertextMock,
    protectedCustomerIdentityPublicKey = ProtectedCustomerIdentityKeyMock.publicKey
  )
