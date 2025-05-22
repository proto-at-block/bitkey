INSERT INTO txVerificationPolicyEntity(
    id,
    effective,
    thresholdCurrencyAlphaCode,
    thresholdAmountFractionalUnitValue,
    delayEndTime,
    cancellationToken,
    completionToken
) VALUES (
    "fake-id",
    "2023-10-01T00:00:00Z",
    "BTC",
    123,
    "2023-10-02T00:00:00Z",
    "fake-cancel-token",
    "fake-completion-token"
);

