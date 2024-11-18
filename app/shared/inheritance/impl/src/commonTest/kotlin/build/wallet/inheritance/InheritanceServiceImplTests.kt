package build.wallet.inheritance

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.AppSessionManagerFake
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.inheritance.*
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.advanceTimeBy
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.inheritance.RetrieveInheritanceClaimsF8EClientFake
import build.wallet.f8e.inheritance.StartInheritanceClaimF8eFake
import build.wallet.f8e.inheritance.UploadInheritanceMaterialF8eClientFake
import build.wallet.f8e.relationships.Relationships
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.relationships.OutgoingInvitationFake
import build.wallet.relationships.RelationshipsServiceMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.time.Duration.Companion.seconds

class InheritanceServiceImplTests : FunSpec({

  coroutineTestScope = true

  val accountService = AccountServiceFake()
  val inheritanceSyncDao = InheritanceSyncDaoFake(
    updateCalls = turbines.create("update calls")
  )
  val inheritanceMaterialCreator = InheritanceCryptoFake(
    inheritanceMaterial = Ok(InheritanceMaterial(emptyList()))
  )
  val inheritanceF8eClient = UploadInheritanceMaterialF8eClientFake(
    uploadCalls = turbines.create("upload calls")
  )
  val fakeInheritanceMaterial = InheritanceMaterial(
    packages = listOf(
      InheritanceMaterialPackage(
        relationshipId = RelationshipId("fake-relationship-id"),
        sealedDek = XCiphertext("fake-sealed-dek"),
        sealedMobileKey = XCiphertext("fake-encrypted-material")
      )
    )
  )
  val newHash = InheritanceMaterialHashData(
    networkType = BitcoinNetworkType.BITCOIN,
    spendingKey = SpendingKeysetMock.appKey,
    contacts = listOf(EndorsedBeneficiaryFake)
  )
  val upToDateHash = newHash
  val outdatedHash = InheritanceMaterialHashData(
    networkType = BitcoinNetworkType.BITCOIN,
    spendingKey = SpendingKeysetMock.appKey,
    contacts = emptyList()
  )
  val relationshipsService = RelationshipsServiceMock(turbines::create)
  val appCoroutineScope = TestScope()

  val inheritanceClaimsDao = InheritanceClaimsDaoFake()
  val appSessionManager = AppSessionManagerFake()
  val inheritanceClaimsF8eClient = RetrieveInheritanceClaimsF8EClientFake()
  val inheritanceFeatureFlag = InheritanceFeatureFlag(FeatureFlagDaoFake())

  val inheritanceService = InheritanceServiceImpl(
    accountService = accountService,
    relationshipsService = relationshipsService,
    appCoroutineScope = appCoroutineScope,
    inheritanceSyncDao = inheritanceSyncDao,
    inheritanceMaterialF8eClient = inheritanceF8eClient,
    inheritanceCrypto = inheritanceMaterialCreator,
    startInheritanceClaimF8eClient = StartInheritanceClaimF8eFake(),
    retrieveInheritanceClaimsF8EClient = inheritanceClaimsF8eClient,
    inheritanceClaimsDao = inheritanceClaimsDao,
    appSessionManager = appSessionManager,
    inheritanceFeatureFlag = inheritanceFeatureFlag
  )

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
    inheritanceSyncDao.hashData = Ok(outdatedHash.inheritanceMaterialHash)
    inheritanceMaterialCreator.inheritanceMaterial = Ok(fakeInheritanceMaterial)
    inheritanceMaterialCreator.inheritanceMaterialHash = Ok(newHash)
    inheritanceF8eClient.uploadResponse = Ok(Unit)
    inheritanceSyncDao.updateHashResult = Ok(Unit)
    inheritanceClaimsF8eClient.reset()
    inheritanceClaimsDao.clear()

    inheritanceFeatureFlag.setFlagValue(true)
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
    inheritanceSyncDao.updateCalls.awaitItem().shouldBe(newHash.inheritanceMaterialHash)
  }

  test("Inheritance data not synced when up-to-date") {
    inheritanceSyncDao.hashData = Ok(upToDateHash.inheritanceMaterialHash)

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
    inheritanceSyncDao.updateCalls.awaitItem().shouldBe(newHash.inheritanceMaterialHash)
  }

  test("No updates until initial contact hash is saved") {
    inheritanceSyncDao.hashData = Ok(null)
    inheritanceMaterialCreator.inheritanceMaterialHash = Ok(outdatedHash)

    inheritanceService.syncInheritanceMaterial(KeyboxMock).shouldBe(Ok(Unit))

    inheritanceF8eClient.uploadCalls.expectNoEvents()
    inheritanceSyncDao.updateCalls.expectNoEvents()
  }

  test("inheritanceRelationships filters by BENEFICIARY role") {
    relationshipsService.relationships.value = Relationships(
      invitations = listOf(InvitationFake, BeneficiaryInvitationFake),
      endorsedTrustedContacts = listOf(
        EndorsedTrustedContactFake1,
        EndorsedBeneficiaryFake,
        EndorsedTrustedContactFake2
      ),
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

  test("sync inheritance claims") {
    val claims = InheritanceClaims(
      beneficiaryClaims = listOf(BeneficiaryPendingClaimFake),
      benefactorClaims = listOf(BenefactorPendingClaimFake)
    )
    inheritanceClaimsF8eClient.response = Ok(claims)

    appCoroutineScope.launch { inheritanceService.executeWork() }
    appCoroutineScope.runCurrent()

    val updatedClaims = InheritanceClaims(
      beneficiaryClaims = listOf(BeneficiaryLockedClaimFake),
      benefactorClaims = listOf(BenefactorLockedClaimFake)
    )
    inheritanceClaimsF8eClient.response = Ok(updatedClaims)

    appCoroutineScope.runCurrent()

    // We haven't re-synced yet, so there shouldn't be an update.
    inheritanceClaimsDao.pendingBeneficiaryClaims.value.shouldBe(Ok(listOf(BeneficiaryPendingClaimFake)))
    inheritanceClaimsDao.pendingBenefactorClaims.value.shouldBe(Ok(listOf(BenefactorPendingClaimFake)))

    appCoroutineScope.advanceTimeBy(60.seconds)
    appCoroutineScope.runCurrent()

    // Sync after 60 seconds.
    inheritanceClaimsDao.pendingBeneficiaryClaims.value.shouldBe(Ok(emptyList()))
    inheritanceClaimsDao.pendingBenefactorClaims.value.shouldBe(Ok(emptyList()))
  }
})
