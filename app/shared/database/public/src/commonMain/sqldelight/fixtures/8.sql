INSERT INTO softwareAccountEntity(rowId, accountId, bitcoinNetworkType, f8eEnvironment, isTestAccount, isUsingSocRecFakes)
VALUES ('0', 'accountId-val', 'networkType-val', 'f8eEnvironment-val', '1', '1');

INSERT INTO activeSoftwareAccountEntity(rowId, accountId, softwareKeyboxId)
VALUES ('0', 'accountId-val', 'softwareKeyboxId-val');

INSERT INTO onboardingSoftwareAccountEntity(rowId, accountId, appGlobalAuthKey, appRecoveryAuthKey)
VALUES ('0', 'accountId-val', 'appGlobalAuthKey-val', 'appRecovery')
