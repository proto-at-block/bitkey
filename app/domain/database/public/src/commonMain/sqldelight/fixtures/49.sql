INSERT INTO privateWalletMigrationEntity(
    rowId,
    newHardwareKey,
    newAppKey,
    newServerKey,
    keysetLocalId,
    keysetServerId,
    serverIntegritySignature,
    sweepId,
    backupCompleted
) VALUES (
    0,
    "fake-hw-key",
    "fake-app-key",
    "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2Userverdpub/*",
    "fake-local-id",
    "fake-server-id",
    "fake-integrity-sig",
    "fake-sweep-id",
    1
);
