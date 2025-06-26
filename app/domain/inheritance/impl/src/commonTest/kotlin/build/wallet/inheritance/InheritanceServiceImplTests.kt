package build.wallet.inheritance

import app.cash.turbine.test
import bitkey.relationships.Relationships
import build.wallet.account.AccountServiceFake
import build.wallet.auth.AppAuthKeyMessageSignerMock
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.wallet.SpendingWalletFake
import build.wallet.bitkey.inheritance.*
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.inheritance.*
import build.wallet.isOk
import build.wallet.relationships.OutgoingInvitationFake
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.Instant

class InheritanceServiceImplTests : FunSpec({
  val clock = ClockFake()
  val accountService = AccountServiceFake()
  val inheritanceSyncDao = InheritanceSyncDaoFake(
    updateCalls = turbines.create("update calls")
  )
  val inheritanceMaterialCreator = InheritanceCryptoFake(
    inheritanceMaterial = Ok(InheritanceMaterial(emptyList()))
  )
  val uploadClaimF8eClient = UploadInheritanceMaterialF8eClientFake(
    uploadCalls = turbines.create("upload calls")
  )
  val completeClaimF8eClient = CompleteInheritanceClaimF8eClientFake(
    completeCalls = turbines.create("complete calls")
  )
  val fakeInheritanceMaterial = InheritanceMaterial(
    packages = listOf(
      InheritanceMaterialPackage(
        relationshipId = RelationshipId("fake-relationship-id"),
        sealedDek = XCiphertext("fake-sealed-dek"),
        sealedMobileKey = XCiphertext("fake-encrypted-material"),
        sealedDescriptor = XCiphertext("fake-sealed-descriptor")
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
  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)
  val appCoroutineScope = TestScope()
  val claimsRepository = InheritanceClaimsRepositoryMock(
    updateSingleClaimCalls = turbines.create("update single claim")
  )
  val messageSigner = AppAuthKeyMessageSignerMock()
  val lockClaimF8eClient = LockInheritanceClaimF8eClientFake(
    calls = turbines.create("Lock Claim Calls")
  )
  val fakeWallet = SpendingWalletFake()

  val inheritanceService = InheritanceServiceImpl(
    accountService = accountService,
    relationshipsService = relationshipsService,
    inheritanceSyncDao = inheritanceSyncDao,
    inheritanceMaterialF8eClient = uploadClaimF8eClient,
    inheritanceCrypto = inheritanceMaterialCreator,
    startInheritanceClaimF8eClient = StartInheritanceClaimF8eFake(),
    lockInheritanceClaimF8eClient = lockClaimF8eClient,
    completeInheritanceClaimF8eClient = completeClaimF8eClient,
    cancelInheritanceClaimF8eClient = CancelInheritanceClaimF8eClientFake(),
    transactionFactory = InheritanceTransactionFactoryMock(),
    appAuthKeyMessageSigner = messageSigner,
    inheritanceClaimsRepository = claimsRepository,
    clock = ClockFake()
  )

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
    inheritanceSyncDao.hashData = Ok(outdatedHash.inheritanceMaterialHash)
    inheritanceSyncDao.updateHashResult = Ok(Unit)
    inheritanceMaterialCreator.inheritanceMaterial = Ok(fakeInheritanceMaterial)
    inheritanceMaterialCreator.inheritanceMaterialHash = Ok(newHash)
    uploadClaimF8eClient.uploadResponse = Ok(Unit)
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

    uploadClaimF8eClient.uploadCalls.awaitItem().shouldBe(fakeInheritanceMaterial)
    inheritanceSyncDao.updateCalls.awaitItem().shouldBe(newHash.inheritanceMaterialHash)
  }

  test("Inheritance data not synced when up-to-date") {
    inheritanceSyncDao.hashData = Ok(upToDateHash.inheritanceMaterialHash)

    inheritanceService.syncInheritanceMaterial(KeyboxMock).shouldBe(Ok(Unit))

    uploadClaimF8eClient.uploadCalls.expectNoEvents()
    inheritanceSyncDao.updateCalls.expectNoEvents()
  }

  test("Inheritance data not synced without full account") {
    accountService.setActiveAccount(LiteAccountMock)

    inheritanceService.syncInheritanceMaterial(KeyboxMock).shouldBeErr(
      Error("No active FullAccount present, found LiteAccount.")
    )

    uploadClaimF8eClient.uploadCalls.expectNoEvents()
    inheritanceSyncDao.updateCalls.expectNoEvents()
  }

  test("Failed API Call does not update saved hash") {
    val error = Error("Test API Failure")
    uploadClaimF8eClient.uploadResponse = Err(error)

    inheritanceService.syncInheritanceMaterial(KeyboxMock).should {
      it.isErr.shouldBeTrue()
      it.error.cause.shouldBe(error)
    }

    uploadClaimF8eClient.uploadCalls.awaitItem().shouldBe(fakeInheritanceMaterial)
    inheritanceSyncDao.updateCalls.expectNoEvents()
  }

  test("Data update failures are propagated") {
    val error = Error("Database Failure")
    inheritanceSyncDao.updateHashResult = Err(error)

    inheritanceService.syncInheritanceMaterial(KeyboxMock).should {
      it.isErr.shouldBeTrue()
      it.error.shouldBe(error)
    }

    uploadClaimF8eClient.uploadCalls.awaitItem().shouldBe(fakeInheritanceMaterial)
    inheritanceSyncDao.updateCalls.awaitItem().shouldBe(newHash.inheritanceMaterialHash)
  }

  test("No updates until initial contact hash is saved") {
    inheritanceSyncDao.hashData = Ok(null)
    inheritanceMaterialCreator.inheritanceMaterialHash = Ok(outdatedHash)

    inheritanceService.syncInheritanceMaterial(KeyboxMock).shouldBe(Ok(Unit))

    uploadClaimF8eClient.uploadCalls.expectNoEvents()
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

  test("Load Approved claim after cancel") {
    messageSigner.result = Ok("test")
    val relationship = RelationshipId("test-load-claim-relationship")
    val pendingClaimId = InheritanceClaimId("test-load-claim-id")
    val claims = InheritanceClaims(
      beneficiaryClaims = listOf(
        // Invalid Claim: Canceled
        BeneficiaryCanceledClaimFake.copy(
          relationshipId = relationship
        ),
        // Invalid Claim: Different Relationship
        BeneficiaryLockedClaimBothDescriptorsFake,
        // Expected Claim Result
        BeneficiaryPendingClaimFake.copy(
          claimId = pendingClaimId,
          relationshipId = relationship,
          delayEndTime = Instant.DISTANT_PAST
        )
      ),
      benefactorClaims = listOf(BenefactorPendingClaimFake, BenefactorLockedClaimFake)
    )
    claimsRepository.fetchClaimsResult = Ok(claims)
    lockClaimF8eClient.response = Ok(
      BeneficiaryLockedClaimBothDescriptorsFake.copy(
        claimId = pendingClaimId,
        relationshipId = relationship
      )
    )

    val result = inheritanceService.loadApprovedClaim(
      relationshipId = relationship
    )

    lockClaimF8eClient.calls.awaitItem().shouldBe(pendingClaimId)
    claimsRepository.updateSingleClaimCalls.awaitItem()
      .claimId.shouldBe(pendingClaimId)
    result.shouldBeOk()
  }

  test("Load Approved claim, no active claims") {
    messageSigner.result = Ok("test")
    val relationship = RelationshipId("test-load-claim-relationship")
    val pendingClaimId = InheritanceClaimId("test-load-claim-id")
    val claims = InheritanceClaims(
      beneficiaryClaims = listOf(
        // Invalid Claim: Canceled
        BeneficiaryCanceledClaimFake.copy(
          relationshipId = relationship
        ),
        // Invalid Claim: Different Relationship
        BeneficiaryLockedClaimBothDescriptorsFake,
        // Invalid Claim: Completed
        BeneficiaryCompleteClaimFake.copy(
          claimId = pendingClaimId,
          relationshipId = relationship
        )
      ),
      benefactorClaims = listOf(BenefactorPendingClaimFake, BenefactorLockedClaimFake)
    )
    claimsRepository.fetchClaimsResult = Ok(claims)

    val result = inheritanceService.loadApprovedClaim(
      relationshipId = relationship
    )

    result.isErr.shouldBeTrue()
  }

  // This scenario shouldn't ever happen, but let's still test it
  test("Load Approved claim, multiple claims") {
    messageSigner.result = Ok("test")
    val relationship = RelationshipId("test-load-claim-relationship")
    val pendingClaimId = InheritanceClaimId("test-load-claim-id")
    val claims = InheritanceClaims(
      beneficiaryClaims = listOf(
        // Invalid Claim: Canceled
        BeneficiaryCanceledClaimFake.copy(
          relationshipId = relationship
        ),
        // Invalid Claim: Different Relationship
        BeneficiaryLockedClaimBothDescriptorsFake,
        // Expected Claim Result
        BeneficiaryPendingClaimFake.copy(
          claimId = pendingClaimId,
          relationshipId = relationship,
          delayEndTime = Instant.DISTANT_PAST
        ),
        // Unexpected second claim, ignored.
        BeneficiaryPendingClaimFake.copy(
          claimId = InheritanceClaimId("ignored-claim"),
          relationshipId = relationship,
          delayEndTime = Instant.DISTANT_PAST
        )
      ),
      benefactorClaims = listOf(BenefactorPendingClaimFake, BenefactorLockedClaimFake)
    )
    claimsRepository.fetchClaimsResult = Ok(claims)
    lockClaimF8eClient.response = Ok(
      BeneficiaryLockedClaimBothDescriptorsFake.copy(
        claimId = pendingClaimId,
        relationshipId = relationship
      )
    )

    val result = inheritanceService.loadApprovedClaim(
      relationshipId = relationship
    )

    lockClaimF8eClient.calls.awaitItem().shouldBe(pendingClaimId)
    claimsRepository.updateSingleClaimCalls.awaitItem()
      .claimId.shouldBe(pendingClaimId)
    result.shouldBeOk()
  }

  test("Complete Inheritance Claim") {
    messageSigner.result = Ok("test")
    val relationship = RelationshipId("test-load-claim-relationship")
    val pendingClaimId = InheritanceClaimId("test-load-claim-id")
    val details = InheritanceTransactionDetails(
      claim = BeneficiaryLockedClaimBothDescriptorsFake.copy(
        claimId = pendingClaimId,
        relationshipId = relationship
      ),
      inheritanceWallet = fakeWallet,
      recipientAddress = BitcoinAddress("test-recipient-address"),
      psbt = PsbtMock
    )
    completeClaimF8eClient.response = Ok(
      BeneficiaryCompleteClaimFake.copy(
        claimId = pendingClaimId,
        relationshipId = relationship
      )
    )

    val result = inheritanceService.completeClaimTransfer(
      relationshipId = relationship,
      details = details
    )

    completeClaimF8eClient.completeCalls.awaitItem().shouldBe(pendingClaimId)
    claimsRepository.updateSingleClaimCalls.awaitItem().claimId.shouldBe(pendingClaimId)
    result.isOk()
  }

  test("Complete Empty Inheritance Claim") {
    messageSigner.result = Ok("test")
    val relationship = RelationshipId("test-load-claim-relationship")
    val pendingClaimId = InheritanceClaimId("test-load-claim-id")

    claimsRepository.fetchClaimsResult = InheritanceClaims(
      beneficiaryClaims = listOf(
        BeneficiaryLockedClaimBothDescriptorsFake.copy(
          claimId = pendingClaimId,
          relationshipId = relationship
        )
      ),
      benefactorClaims = emptyList()
    ).let { Ok(it) }
    completeClaimF8eClient.response = Ok(
      BeneficiaryCompleteClaimFake.copy(
        claimId = pendingClaimId,
        relationshipId = relationship
      )
    )

    val result = inheritanceService.completeClaimWithoutTransfer(
      relationshipId = relationship
    )

    completeClaimF8eClient.completeCalls.awaitItem().shouldBe(pendingClaimId)
    claimsRepository.updateSingleClaimCalls.awaitItem().claimId.shouldBe(pendingClaimId)
    result.isOk()
  }
})
