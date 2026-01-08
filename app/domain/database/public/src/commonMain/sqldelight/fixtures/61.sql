-- Test fixture data for fake hardware states.
-- Value 0 (disabled) is a default test state for transaction verification.
-- Keep rowId fixed at 0 to align with DAO queries.
INSERT INTO fakeHardwareStatesEntity(rowId, transactionVerificationEnabled)
VALUES (0, 0);

