INSERT INTO txVerificationPolicyEntity(
    id,
    thresholdCurrencyAlphaCode,
    thresholdAmountFractionalUnitValue
) VALUES(0, 'btc', 10000);

INSERT INTO pendingPrivilegedActionsEntity(
    id,
    type,
    strategy
) VALUES(0, 'LOOSEN_TRANSACTION_VERIFICATION_POLICY', 'OUT_OF_BAND');