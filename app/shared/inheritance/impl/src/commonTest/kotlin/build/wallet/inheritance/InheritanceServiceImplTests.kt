package build.wallet.inheritance

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.bitkey.inheritance.InheritanceMaterialHash
import build.wallet.bitkey.inheritance.InheritanceMaterialPackage
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.inheritance.UploadInheritanceMaterialF8eClientFake
import build.wallet.f8e.relationships.Relationships
import build.wallet.relationships.OutgoingInvitationFake
import build.wallet.relationships.RelationshipsServiceMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope

class InheritanceServiceImplTests : FunSpec({

  val accountService = AccountServiceFake()
  val inheritanceSyncDao = InheritanceSyncDaoFake(
    updateCalls = turbines.create("update calls")
  )
  val inheritanceMaterialRepository = InheritanceMaterialCreatorFake(
    inheritanceMaterial = Ok(InheritanceMaterial(emptyList()))
  )
  val inheritanceF8eClient = UploadInheritanceMaterialF8eClientFake(
    uploadCalls = turbines.create("upload calls")
  )
  val fakeInheritanceMaterial = InheritanceMaterial(
    packages = listOf(
      InheritanceMaterialPackage(
        relationshipId = RelationshipId("fake-relationship-id"),
        sealedDek = "fake-sealed-dek",
        sealedMobileKey = "fake-encrypted-material"
      )
    )
  )
  val newHash = InheritanceMaterialHash(123)
  val upToDateHash = newHash
  val outdatedHash = InheritanceMaterialHash(-1)
  val relationshipsService = RelationshipsServiceMock(turbines::create)
  val appCoroutineScope = TestScope()

  val inheritanceService = InheritanceServiceImpl(
    accountService = accountService,
    relationshipsService = relationshipsService,
    appCoroutineScope = appCoroutineScope,
    inheritanceSyncDao = inheritanceSyncDao,
    inheritanceMaterialF8eClient = inheritanceF8eClient,
    inheritanceMaterialCreator = inheritanceMaterialRepository
  )

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
    inheritanceSyncDao.hashResult = Ok(outdatedHash)
    inheritanceMaterialRepository.inheritanceMaterial = Ok(fakeInheritanceMaterial)
    inheritanceMaterialRepository.inheritanceMaterialHash = Ok(newHash)
    inheritanceF8eClient.uploadResponse = Ok(Unit)
    inheritanceSyncDao.updateHashResult = Ok(Unit)
  }

  test("creating an invitation") {
    val result = inheritanceService.createInheritanceInvitation(
      hardwareProofOfPossession = HwFactorProofOfPossession("signed-token"),
      trustedContactAlias = TrustedContactAlias("trusted-contact-alias")
    )

    relationshipsService.createInvitationCalls.awaitItem()
    result.isOk.shouldBeTrue()
    result.value.shouldBe(OutgoingInvitationFake)
  }

  test("Sync Inheritance data when hash out-of-date") {
    inheritanceService.syncInheritanceMaterial(KeyboxMock).shouldBe(Ok(Unit))

    inheritanceF8eClient.uploadCalls.awaitItem().shouldBe(fakeInheritanceMaterial)
    inheritanceSyncDao.updateCalls.awaitItem().shouldBe(newHash)
  }

  test("Inheritance data not synced when up-to-date") {
    inheritanceSyncDao.hashResult = Ok(upToDateHash)

    inheritanceService.syncInheritanceMaterial(KeyboxMock).shouldBe(Ok(Unit))

    inheritanceF8eClient.uploadCalls.expectNoEvents()
    inheritanceSyncDao.updateCalls.expectNoEvents()
  }

  test("Inheritance data not synced without full account") {
    accountService.setActiveAccount(LiteAccountMock)

    inheritanceService.syncInheritanceMaterial(KeyboxMock).shouldBe(Ok(Unit))

    inheritanceF8eClient.uploadCalls.expectNoEvents()
    inheritanceSyncDao.updateCalls.expectNoEvents()
  }

  test("Failed API Call does not update saved hash") {
    val error = Error("Test API Failure")
    inheritanceF8eClient.uploadResponse = Err(error)

    inheritanceService.syncInheritanceMaterial(KeyboxMock).should {
      it.isErr.shouldBeTrue()
      it.error.cause.shouldBe(error)
    }

    inheritanceF8eClient.uploadCalls.awaitItem().shouldBe(fakeInheritanceMaterial)
    inheritanceSyncDao.updateCalls.expectNoEvents()
  }

  test("Data update failures are propagated") {
    val error = Error("Database Failure")
    inheritanceSyncDao.updateHashResult = Err(error)

    inheritanceService.syncInheritanceMaterial(KeyboxMock).should {
      it.isErr.shouldBeTrue()
      it.error.shouldBe(error)
    }

    inheritanceF8eClient.uploadCalls.awaitItem().shouldBe(fakeInheritanceMaterial)
    inheritanceSyncDao.updateCalls.awaitItem().shouldBe(newHash)
  }

  test("inheritanceRelationships filters by BENEFICIARY role") {
    relationshipsService.relationships.value = Relationships(
      invitations = listOf(InvitationFake, BeneficiaryInvitationFake),
      endorsedTrustedContacts = listOf(EndorsedTrustedContactFake1, EndorsedBeneficiaryFake, EndorsedTrustedContactFake2),
      unendorsedTrustedContacts = listOf(UnendorsedBeneficiaryFake, UnendorsedTrustedContactFake),
      protectedCustomers = immutableListOf(ProtectedCustomerFake, ProtectedBeneficiaryCustomerFake)
    )

    appCoroutineScope.testScheduler.runCurrent()

    inheritanceService.inheritanceRelationships.test {
      awaitItem().shouldBe(
        Relationships(
          invitations = listOf(BeneficiaryInvitationFake),
          endorsedTrustedContacts = listOf(EndorsedBeneficiaryFake),
          unendorsedTrustedContacts = listOf(UnendorsedBeneficiaryFake),
          protectedCustomers = immutableListOf(ProtectedBeneficiaryCustomerFake)
        )
      )
    }
  }
})
