# Bitkey Database Structure: Accounts, Keyboxes, and Keysets

## Overview

The Bitkey wallet uses a hierarchical data model to manage different types of accounts and their associated
cryptographic keys. This document explains the relationships between Accounts, Keyboxes, and Keysets in the database.

## Key Concepts

### Account Types

The Bitkey wallet supports three types of accounts:

1. **Full Account** - Complete wallet with hardware device, app, and server components
2. **Lite Account** - App-only account without hardware device. Serves as recovery contact for a full account.
3. **Software Account** - Software-based wallet with spending capabilities [note this is not released]

### Core Entities

- **Account**: Top-level entity representing a user's wallet
- **Keybox**: Container for cryptographic keys associated with an account
- **Keyset**: Set of public keys (app, hardware, server) used for spending
- **Key Bundle**: Collection of keys serving specific purposes (app keys, hardware keys)

## Database Schema

### Key Persistence
Public keys are stored in the sqlite database. Private keys are stored in secure storage.

### Full Account Structure

```sql
fullAccountEntity
├── accountId (PRIMARY KEY)
│
└─── keyboxEntity (1:1)
    ├── id
    ├── accountId (FK → fullAccountEntity)
    ├── networkType (Bitcoin network)
    ├── f8eEnvironment
    ├── isTestAccount
    ├── isUsingSocRecFakes
    ├── delayNotifyDuration
    ├── appGlobalAuthKeyHwSignature
    ├── canUseKeyboxKeysets
    │
    ├── spendingKeysetEntity (1:N)
    │   ├── id
    │   ├── keyboxId (FK → keyboxEntity)
    │   ├── serverId (the f8e spending keyset remote id)
    │   ├── appKey (app spending public key)
    │   ├── hardwareKey (hardware spending public key)
    │   ├── serverKey (f8e spending public key)
    │   └── isActive (only one active per keybox)
    │
    ├── appKeyBundleEntity (1:N)
    │   ├── id
    │   ├── keyboxId (FK → keyboxEntity)
    │   ├── globalAuthKey
    │   ├── spendingKey
    │   ├── recoveryAuthKey
    │   └── isActive (only one active per keybox)
    │
    └── hwKeyBundleEntity (1:N)
        ├── id
        ├── keyboxId (FK → keyboxEntity)
        ├── spendingKey
        ├── authKey
        └── isActive (only one active per keybox)
```

### Account States

Each account type can be in one of two states:

1. **Active** - Fully set up and ready for use
2. **Onboarding** - Being created but not yet complete

Separate tables track these states:

- `activeFullAccountEntity` - Currently active full account
- `onboardingFullAccountEntity` - Full account being onboarded
- `activeLiteAccountEntity` - Currently active lite account
- `onboardingLiteAccountEntity` - Lite account being onboarded
- `activeSoftwareAccountEntity` - Currently active software account
- `onboardingSoftwareAccountEntity` - Software account being onboarded

## Entity Relationships

### Full Account → Keybox (1:1)

- Each Full Account has exactly one Keybox
- The Keybox stores configuration and contains all keys
- Deletion cascades: deleting an account deletes its keybox

### Keybox → Spending Keyset (1:N)

- A Keybox can have multiple spending keysets
- Only one keyset can be active at a time (enforced by unique index)
- Each keyset contains three public keys:
    - App spending key
    - Hardware spending key
    - Server (F8e) spending key

### Keybox → App Key Bundle (1:N)

- A Keybox can have multiple app key bundles
- Only one bundle can be active at a time
- Contains app-specific keys:
    - Global authentication key
    - Recovery authentication key
    - Spending key reference

### Keybox → Hardware Key Bundle (1:N)

- A Keybox can have multiple hardware key bundles
- Only one bundle can be active at a time
- Contains hardware-specific keys:
    - Hardware authentication key
    - Hardware spending key

## Key Rotation

The system supports key rotation through the multiple keyset/bundle design:

1. New keysets/bundles are created and stored
2. The new set is marked as active
3. Old sets remain in the database for historical reference. Note that in practice, old keysets are deleted due to cascading deletes, then restored by app code doing a full PUT operation with all keyboxes/keysets. Also note that app versions before 2025.14.0 retained old keyboxes/keysets without a reference to them; dangling keyboxes are now cleaned up upon recovery but some accounts will still have this lingering data.
4. Only the active set is used for transactions

### canUseKeyboxKeysets
keybox.canUseKeyboxKeysets indicates whether the keybox contains ALL active and inactive keysets for the keybox. If the value is false, you must rely
on keysets coming from F8e. If true, the app contains all keysets and can be considered authoritative.

Today, keysets are persisted through the recovery process. However, we used to skip saving inactive keysets during
lost app recovery, so we require this flag to discern partial vs complete data.

## Unique Constraints

- Each account ID must be unique
- Only one active spending keyset per keybox
- Only one active app key bundle per keybox
- Only one active hardware key bundle per keybox

## Views

The database provides convenient views for querying complete account data:

### fullAccountView

Joins all related tables to provide complete Full Account information including:

- Account details
- Keybox configuration
- Active spending keyset
- Active app key bundle
- Active hardware key bundle