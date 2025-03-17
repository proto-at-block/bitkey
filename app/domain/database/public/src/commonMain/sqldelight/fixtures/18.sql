INSERT INTO pendingBeneficiaryClaimEntity (
    claimId, relationshipId, delayEndTime, delayStartTime, appPubkey, hardwarePubkey
) VALUES (
    "beneficiary-pending-claim-id", "beneficiary-pending-relationship-id", 1, 1, "fake-app-pubkey", "fake-hw-pubkey"
);

INSERT INTO canceledBeneficiaryClaimEntity (
    claimId, relationshipId
) VALUES (
    "beneficiary-canceled-claim-id", "beneficiary-canceled-relationship-id"
);

INSERT INTO lockedBeneficiaryClaimEntity (
    claimId, relationshipId, sealedDek, sealedMobileKey
) VALUES (
     "beneficiary-locked-claim-id", "beneficiary-locked-relationship-id", "sealed-dek", "sealed-mobile-key"
);

INSERT INTO pendingBenefactorClaimEntity (
    claimId, relationshipId, delayEndTime, delayStartTime
) VALUES (
     "benefactor-pending-claim-id", "benefactor-pending-relationship-id", 1, 1
);

INSERT INTO canceledBenefactorClaimEntity (
    claimId, relationshipId
) VALUES (
     "benefactor-canceled-claim-id", "benefactor-canceled-relationship-id"
);

INSERT INTO lockedBenefactorClaimEntity (
    claimId, relationshipId
) VALUES (
   "benefactor-locked-claim-id", "benefactor-locked-relationship-id"
);
