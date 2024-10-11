package build.wallet.cloud.backup.v2

import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake1
import build.wallet.bitkey.relationships.EndorsedTrustedContactFake2
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.encrypt.SealedDataMock
import build.wallet.encrypt.XCiphertext
import build.wallet.encrypt.XCiphertextMock

val SocRecSealedPkek1 = XCiphertext(value = "cipherText-1.nonce-1")

val SocRecSealedPkek2 = XCiphertext(value = "cipherText-2.nonce-2")

val FullAccountFieldsMock =
  FullAccountFields(
    sealedHwEncryptionKey = SealedCsekFake,
    socRecSealedDekMap =
      mapOf(
        EndorsedTrustedContactFake1.relationshipId to SocRecSealedPkek1,
        EndorsedTrustedContactFake2.relationshipId to SocRecSealedPkek2
      ),
    isFakeHardware = false,
    hwFullAccountKeysCiphertext = SealedDataMock,
    socRecSealedFullAccountKeys = XCiphertextMock,
    rotationAppRecoveryAuthKeypair = null,
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock
  )
