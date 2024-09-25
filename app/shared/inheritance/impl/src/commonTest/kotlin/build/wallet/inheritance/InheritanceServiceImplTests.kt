package build.wallet.inheritance

import build.wallet.account.AccountServiceFake
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.socrec.InvitationFake
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.relationships.RelationshipsF8eClientFake
import build.wallet.recovery.socrec.SocRecCryptoFake
import build.wallet.recovery.socrec.SocRecEnrollmentAuthenticationDaoImpl
import build.wallet.recovery.socrec.SocialRecoveryCodeBuilderFake
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class InheritanceServiceImplTests : FunSpec({

  val databaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory)
  val appKeyDao = AppPrivateKeyDaoFake()
  val authDao = SocRecEnrollmentAuthenticationDaoImpl(appKeyDao, databaseProvider)
  val accountService = AccountServiceFake()

  val inheritanceService = InheritanceServiceImpl(
    socRecCrypto = SocRecCryptoFake(),
    accountService = accountService,
    relationshipsF8eClient = RelationshipsF8eClientFake(),
    socialRecoveryCodeBuilder = SocialRecoveryCodeBuilderFake(),
    socRecEnrollmentAuthenticationDao = authDao
  )

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
  }

  test("creating an invitation") {
    val result = inheritanceService.createInheritanceInvitation(
      hardwareProofOfPossession = HwFactorProofOfPossession("signed-token"),
      trustedContactAlias = TrustedContactAlias("trusted-contact-alias")
    )

    result.isOk.shouldBeTrue()
    result.value.invitation.shouldBe(InvitationFake)
  }
})
