-- Fixture for migration 75: sealedDdk column added to localRecoveryAttemptEntity.
-- The column is nullable; this exercises the populated case on the existing fixture row.
UPDATE localRecoveryAttemptEntity SET sealedDdk = X'deadbeef' WHERE rowId = 1;
