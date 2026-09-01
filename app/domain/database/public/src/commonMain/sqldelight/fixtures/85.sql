INSERT INTO walletMetadataTransactionNoteEntity(accountId, transactionId, note, createdAt, updatedAt)
VALUES (
  'fixture-account-id',
  'fixture-transaction-id',
  'fixture transaction note',
  '2026-01-01T00:00:00Z',
  '2026-01-01T00:00:00Z'
);

INSERT INTO walletMetadataTransactionNoteTombstoneEntity(accountId, transactionId, deletedAt)
VALUES (
  'fixture-account-id',
  'fixture-deleted-transaction-id',
  '2026-01-01T00:00:00Z'
);
