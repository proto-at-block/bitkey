-- Migration 86 clears cached firmware that predates device-change invalidation, so these
-- rows represent a cache written after that behavior existed.
INSERT INTO fwupDataEntity(mcuRole, mcuName, version, chunkSize, signatureOffset,
appPropertiesOffset, firmware, signature, fwupMode)
VALUES ('CORE', 'EFR32', 'fake', 1, 1, 1, x'00', x'00', 'Normal');

INSERT INTO mcuFwupStateEntity(mcuRole, currentSequenceId)
VALUES ('CORE', 1);
