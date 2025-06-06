-- Insert an active policy:
INSERT INTO txVerificationPolicyEntity(
    thresholdCurrencyAlphaCode,
    thresholdAmountFractionalUnitValue,
    delayEndTime,
    authId,
    cancellationToken,
    completionToken
) VALUES (
    "BTC",
    123,
    null,
    null,
    null,
    null
);

-- Insert a pending policy in the future:
INSERT INTO txVerificationPolicyEntity(
    thresholdCurrencyAlphaCode,
    thresholdAmountFractionalUnitValue,
    delayEndTime,
    authId,
    cancellationToken,
    completionToken
) VALUES (
    "BTC",
    456,
    "2222-10-01T00:00:00Z",
    "fake-auth-id",
    "fake-cancel-token",
    "fake-completion-token"
);
