INSERT INTO eventEntity (
    id,
    event,
    f8eEnvironment
) VALUES (
    '1',
    'event-val',
    'f8eEnvironment-val'
);

INSERT INTO appInstallationEntity (
    rowid,
    id,
    hardwareSerialNumber
) VALUES (
    '1',
    'id-val',
    'hardwareSerialNumber-val'
);

INSERT INTO appKeyBundleEntity (
    id,
    globalAuthKey,
    spendingKey,
    recoveryAuthKey
) VALUES (
    'referenced-appKeyBundleEntity-id-val',
    'globalAuthKey-val',
    'spendingKey-val',
    'recoveryAuthKey-val'
);

INSERT INTO hwKeyBundleEntity (
    id,
    spendingKey,
    authKey
) VALUES (
    'referenced-hwKeyBundleEntity-id-val',
    'spendingKey-val',
    'authKey-val'
);

INSERT INTO spendingKeysetEntity (
    id,
    serverId,
    appKey,
    hardwareKey,
    serverKey
) VALUES (
    'referenced-spendingKeysetEntity-id-val',
    'serverId-val',
    'appKey-val',
    'hardwareKey-val',
    'serverKey-val'
);

INSERT INTO authKeyRotationAttemptEntity (
    rowId,
    destinationAppGlobalAuthKey,
    destinationAppRecoveryAuthKey,
    destinationAppGlobalAuthKeyHwSignature
) VALUES (
    '1',
    'destinationAppGlobalAuthKey-val',
    'destinationAppRecoveryAuthKey-val',
    'destinationAppGlobalAuthKeyHwSignature-val'
);

INSERT INTO bitcoinDisplayPreferenceEntity (
    rowId,
    displayUnit
) VALUES (
    '1',
    'displayUnit-val'
);

INSERT INTO booleanFeatureFlagEntity (
    featureFlagId,
    value
) VALUES (
    'featureFlagId-val',
    '1'
);

INSERT INTO electrumConfigEntity (
    id,
    f8eDefinedElectrumServerUrl,
    isCustomElectrumServerOn,
    customElectrumServerUrl
) VALUES (
    '1',
    'f8eDefinedElectrumServerUrl-val',
    '1',
    'customElectrumServerUrl-val'
);

INSERT INTO emailTouchpointEntity (
    touchpointId,
    email
) VALUES (
    'touchpointId-val',
    'email-val'
);

INSERT INTO exchangeRateEntity (
    id,
    fromCurrency,
    toCurrency,
    rate,
    timeRetrieved
) VALUES (
    '1',
    'fromCurrency-val',
    'toCurrency-val',
    '1.0',
    '1'
);

INSERT INTO fiatCurrencyEntity (
    textCode,
    fractionalDigits,
    displayUnitSymbol,
    displayName,
    displayCountryCode
) VALUES (
    'textCode-val',
    '1',
    'displayUnitSymbol-val',
    'displayName-val',
    'displayCountryCode-val'
);

INSERT INTO fiatCurrencyMobilePayConfigurationEntity (
    textCode,
    minimumLimit,
    maximumLimit,
    snapValues
) VALUES (
    'textCode-val',
    '1',
    '1',
    'snapValues-val'
);

INSERT INTO fiatCurrencyPreferenceEntity (
    rowId,
    currency
) VALUES (
    '1',
    'currency-val'
);

INSERT INTO firmwareCoredumpsEntity (
    id,
    coredump,
    serial,
    swVersion,
    hwVersion,
    swType
) VALUES (
    '1',
    'coredump-val',
    'serial-val',
    'swVersion-val',
    'hwVersion-val',
    'swType-val'
);

INSERT INTO firmwareDeviceInfoEntity (
    rowId,
    version,
    serial,
    swType,
    hwRevision,
    activeSlot,
    batteryCharge,
    vCell,
    timeRetrieved,
    avgCurrentMa,
    batteryCycles,
    secureBootConfig
) VALUES (
    '1',
    'version-val',
    'serial-val',
    'swType-val',
    'hwRevision-val',
    'activeSlot-val',
    '1.0',
    '1',
    '1',
    '1',
    '1',
    'secureBootConfig-val'
);

INSERT INTO firmwareMetadataEntity (
    rowId,
    activeSlot,
    gitId,
    gitBranch,
    version,
    build,
    timestamp,
    hash,
    hwRevision
) VALUES (
    '1',
    'activeSlot-val',
    'gitId-val',
    'gitBranch-val',
    'version-val',
    'build-val',
    '1',
    'hash-val',
    'hwRevision-val'
);

INSERT INTO firmwareTelemetryEntity (
    id,
    serial,
    event
) VALUES (
    '1',
    'serial-val',
    'event-val'
);

INSERT INTO keyboxEntity (
    id,
    account,
    activeSpendingKeysetId,
    activeKeyBundleId,
    inactiveKeysetIds,
    networkType,
    fakeHardware,
    f8eEnvironment,
    isTestAccount,
    isUsingSocRecFakes,
    delayNotifyDuration,
    activeHwKeyBundleId,
    appGlobalAuthKeyHwSignature
) VALUES (
    'referenced-keyboxEntity-id-val',
    'account-val',
    'referenced-spendingKeysetEntity-id-val',
    'referenced-appKeyBundleEntity-id-val',
    'inactiveKeysetIds-val',
    'networkType-val',
    '1',
    'f8eEnvironment-val',
    '1',
    '1',
    'delayNotifyDuration-val',
    'referenced-hwKeyBundleEntity-id-val',
    'appGlobalAuthKeyHwSignature-val'
);

INSERT INTO fullAccountEntity (
    accountId,
    keyboxId
) VALUES (
    'referenced-fullAccountEntity-accountId-val',
    'referenced-keyboxEntity-id-val'
);

INSERT INTO activeFullAccountEntity (
    rowId,
    accountId
) VALUES (
    '1',
    'referenced-fullAccountEntity-accountId-val'
);

INSERT INTO onboardingFullAccountEntity (
    rowId,
    accountId
) VALUES (
    '1',
    'referenced-fullAccountEntity-accountId-val'
);

INSERT INTO fwupDataEntity (
    rowId,
    version,
    chunkSize,
    signatureOffset,
    appPropertiesOffset,
    firmware,
    signature,
    fwupMode
) VALUES (
    '1',
    'version-val',
    '1',
    '1',
    '1',
    'firmware-val',
    'signature-val',
    'fwupMode-val'
);

INSERT INTO gettingStartedTaskEntity (
    id,
    taskId,
    taskState
) VALUES (
    '1',
    'taskId-val',
    'taskState-val'
);

INSERT INTO historicalExchangeRateEntity (
    id,
    fromCurrency,
    toCurrency,
    rate,
    time
) VALUES (
    '1',
    'fromCurrency-val',
    'toCurrency-val',
    '1.0',
    '1'
);

INSERT INTO homeUiBottomSheetEntity (
    rowId,
    sheetId
) VALUES (
    '1',
    'sheetId-val'
);

INSERT INTO lightningPreferenceEntity (
    rowId,
    enabled
) VALUES (
    '1',
    '1'
);

INSERT INTO liteAccountEntity (
    accountId,
    appRecoveryAuthKey,
    bitcoinNetworkType,
    f8eEnvironment,
    isTestAccount,
    isUsingSocRecFakes
) VALUES (
    'referenced-liteAccountEntity-accountId-val',
    'appRecoveryAuthKey-val',
    'bitcoinNetworkType-val',
    'f8eEnvironment-val',
    '1',
    '1'
);

INSERT INTO activeLiteAccountEntity (
    rowId,
    accountId
) VALUES (
    '1',
    'referenced-liteAccountEntity-accountId-val'
);

INSERT INTO onboardingLiteAccountEntity (
    rowId,
    accountId
) VALUES (
    '1',
    'referenced-liteAccountEntity-accountId-val'
);

INSERT INTO networkReachabilityEventEntity (
    rowId,
    connection,
    reachability,
    timestamp
) VALUES (
    '1',
    'connection-val',
    'reachability-val',
    '1'
);

INSERT INTO onboardingKeyboxHwAuthPublicKey (
    rowId,
    hwAuthPublicKey,
    appGlobalAuthKeyHwSignature
) VALUES (
    '1',
    'hwAuthPublicKey-val',
    'appGlobalAuthKeyHwSignature-val'
);

INSERT INTO onboardingStepStateEntity (
    stepId,
    state
) VALUES (
    'stepId-val',
    'state-val'
);

INSERT INTO phoneNumberTouchpointEntity (
    touchpointId,
    phoneNumber
) VALUES (
    'touchpointId-val',
    'phoneNumber-val'
);

INSERT INTO priorityPreferenceEntity (
    rowId,
    priority
) VALUES (
    '1',
    'priority-val'
);

INSERT INTO activeServerRecoveryEntity (
    rowId,
    account,
    startTime,
    endTime,
    lostFactor,
    destinationAppGlobalAuthKey,
    destinationAppRecoveryAuthKey,
    destinationHardwareAuthKey
) VALUES (
    '1',
    'account-val',
    '1',
    '1',
    'lostFactor-val',
    'destinationAppGlobalAuthKey-val',
    'destinationAppRecoveryAuthKey-val',
    'destinationHardwareAuthKey-val'
);

INSERT INTO localRecoveryAttemptEntity (
    rowId,
    account,
    destinationAppGlobalAuthKey,
    destinationAppRecoveryAuthKey,
    destinationHardwareAuthKey,
    destinationAppSpendingKey,
    destinationHardwareSpendingKey,
    appGlobalAuthKeyHwSignature,
    lostFactor,
    hadServerRecovery,
    sealedCsek,
    authKeysRotated,
    serverKeysetId,
    serverSpendingKey,
    backedUpToCloud
) VALUES (
    '1',
    'account-val',
    'destinationAppGlobalAuthKey-val',
    'destinationAppRecoveryAuthKey-val',
    'destinationHardwareAuthKey-val',
    'destinationAppSpendingKey-val',
    'destinationHardwareSpendingKey-val',
    'appGlobalAuthKeyHwSignature-val',
    'lostFactor-val',
    '1',
    'sealedCsek-val',
    '1',
    'serverKeysetId-val',
    'serverSpendingKey-val',
    '1'
);

INSERT INTO recoveryIncompleteEntity (
    rowId,
    incomplete
) VALUES (
    '1',
    '1'
);

INSERT INTO registerWatchAddressEntity (
    id,
    address,
    spendingKeysetId,
    accountId,
    f8eEnvironment
) VALUES (
    '1',
    'address-val',
    'referenced-spendingKeysetEntity-id-val',
    'accountId-val',
    'f8eEnvironment-val'
);

INSERT INTO socRecEnrollmentAuthentication (
recoveryRelationshipId,
protectedCustomerEnrollmentPakeKey,
pakeCode
) VALUES (
    'recoveryRelationshipId-val',
    'protectedCustomerEnrollmentPakeKey-val',
    'pakeCode-val'
);

INSERT INTO socRecKeys (
    rowId,
    purpose,
    key
) VALUES (
    '1',
    'purpose-val',
    'key-val'
);

INSERT INTO socRecStartedChallenge (
    rowId,
    challengeId
) VALUES (
    '1',
    'challengeId-val'
);

INSERT INTO socRecTrustedContactEntity (
    rowId,
    recoveryRelationshipId,
    trustedContactAlias,
    authenticationState,
    certificate
) VALUES (
    '1',
    'recoveryRelationshipId-val',
    'trustedContactAlias-val',
    'AWAITING_VERIFY',
    'certificate-val'
);

INSERT INTO socRecProtectedCustomerEntity (
    rowId,
    recoveryRelationshipId,
    alias
) VALUES (
    '1',
    'recoveryRelationshipId-val',
    'alias-val'
);

INSERT INTO socRecTrustedContactInvitationEntity (
    rowId,
    recoveryRelationshipId,
    trustedContactAlias,
    token,
    tokenBitLength,
    expiresAt
) VALUES (
    '1',
    'recoveryRelationshipId-val',
    'trustedContactAlias-val',
    'token-val',
    '1',
    '1'
);

INSERT INTO socRecUnendorsedTrustedContactEntity (
    rowId,
    recoveryRelationshipId,
    trustedContactAlias,
    enrollmentPakeKey,
    enrollmentKeyConfirmation,
    sealedDelegatedDecryptionKey,
    authenticationState
) VALUES (
    '1',
    'recoveryRelationshipId-val',
    'trustedContactAlias-val',
    'enrollmentPakeKey-val',
    'enrollmentKeyConfirmation-val',
    'sealedDelegatedDecryptionKey-val',
    'authenticationState-val'
);

INSERT INTO socRecStartedChallengeAuthentication (
    rowId,
    relationshipId,
    protectedCustomerRecoveryPakeKey,
    pakeCode
) VALUES (
    '1',
    'relationshipId-val',
    'protectedCustomerRecoveryPakeKey-val',
    'pakeCode-val'
);

INSERT INTO spendingLimitEntity (
    id,
    limitAmountFractionalUnitValue,
    limitAmountCurrencyAlphaCode,
    limitTimeZoneZoneId,
    active
) VALUES (
    '1',
    '1',
    'limitAmountCurrencyAlphaCode-val',
    'limitTimeZoneZoneId-val',
    '1'
);

INSERT INTO templateFullAccountConfigEntity (
    rowId,
    bitcoinNetworkType,
    fakeHardware,
    f8eEnvironment,
    isTestAccount,
    isUsingSocRecFakes,
    delayNotifyDuration
) VALUES (
    '1',
    'bitcoinNetworkType-val',
    '1',
    'f8eEnvironment-val',
    '1',
    '1',
    'delayNotifyDuration-val'
);

INSERT INTO transactionDetailEntity (
    transactionId,
    broadcastTimeInstant,
    estimatedConfirmationTimeInstant
) VALUES (
    '1',
    1731695850370,
    1731696450370
);
