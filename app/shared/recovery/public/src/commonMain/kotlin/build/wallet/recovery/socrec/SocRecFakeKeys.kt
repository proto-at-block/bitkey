package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import com.github.michaelbull.result.getOrThrow

val TrustedContactIdentityKeyFake =
  SocRecCryptoFake().generateAsymmetricKey(
    ::TrustedContactIdentityKey
  ).getOrThrow()

val ProtectedCustomerIdentityKeyFake =
  SocRecCryptoFake().generateAsymmetricKey(
    ::ProtectedCustomerIdentityKey
  ).getOrThrow()
