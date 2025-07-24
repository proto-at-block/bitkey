# Bitkey Debug REPL

The Bitkey Debug REPL is an interactive command-line tool for debugging and inspecting Bitkey wallet accounts, keysets, descriptors, and recovery operations across different environments.

## Getting Started

### Launch the Debug REPL

```bash
bk debug <environment>
```

**Available environments:**
- `dev` - Development environment
- `staging` - Staging environment  
- `prod` - Production environment

**Example:**
```bash
bk debug dev
bk debug prod
```

### Initial Setup

Upon launching, you'll see:
```
🐛 Bitkey Debug REPL
📍 Environment: dev
🚀 Use 'load-account <account_id>' to begin debugging
💡 Type 'help' for available commands or 'exit' to quit
⌨️ Press TAB for command completion

debug> 
```

## Command Reference

### General Commands

#### `help` / `h`
Display available commands and usage information.

#### `exit` / `quit` / `q`
Exit the debug REPL.

#### `clear`
Clear the current debug state while preserving the environment setting. This unloads any loaded account, descriptors, and cached data.

---

### Account Management

#### `load-account <account_id>`
Load an account by its account ID from DynamoDB.

**Parameters:**
- `account_id` - The unique account identifier (e.g., `urn:wallet-account:01JY2S32M4TVHQX1RXN3MZ749T`)

**Example:**
```
debug> load-account urn:wallet-account:01JY2S32M4TVHQX1RXN3MZ749T
✅ Account urn:wallet-account:01JY2S32M4TVHQX1RXN3MZ749T loaded successfully
```

**Notes:**
- Only one account can be loaded at a time
- If an account is already loaded, use `clear` first
- Requires appropriate AWS credentials for the selected environment

---

### Data Loading Commands

#### `load-descriptors [attachment_id]`
Load wallet descriptors either from the currently loaded account or by decrypting a specific encrypted attachment.

**Usage Modes:**

1. **From Current Account:**
   ```
   debug> load-descriptors
   ✅ Descriptors loaded successfully from account
   ```
   - Requires an account to be loaded first
   - Extracts descriptors from the account's spending keysets

2. **From Encrypted Attachment:**
   ```
   debug> load-descriptors attachment_12345
   ✅ Descriptors loaded successfully from attachment_12345
   ```
   - Decrypts the specified encrypted attachment
   - Uses KMS to decrypt the private key
   - Requires encrypted attachment reader AWS credentials

**Error Handling:**
- ❌ Failed to decode ciphertext/nonce/keys
- ❌ Failed to decrypt attachment
- ❌ Failed to parse descriptors JSON

---

### Configuration Commands

#### `set-gap-limit <number>`
Set the gap limit for address derivation (default: 20).

**Parameters:**
- `number` - Positive integer representing the gap limit

**Examples:**
```
debug> set-gap-limit 50
✅ Gap limit set to 50

debug> set-gap-limit
📝 Usage: set-gap-limit <number>
📊 Current gap limit: 20
```

#### `set-electrum-server <url>`
Set the Electrum server URL for blockchain operations.

**Parameters:**
- `url` - Electrum server URL with protocol (e.g., `ssl://electrum.blockstream.info:50002`)

**Examples:**
```
debug> set-electrum-server ssl://electrum.blockstream.info:50002
✅ Electrum server set to ssl://electrum.blockstream.info:50002

debug> set-electrum-server
📝 Usage: set-electrum-server <url>
🌐 Current Electrum server: ssl://bitkey.mempool.space:50002
💡 Example: set-electrum-server ssl://electrum.blockstream.info:50002
```

---

### Listing Commands

**Note:** All listing commands require an account to be loaded first using `load-account`.

#### `list-keysets`
Display all keysets for the loaded account, with balance information if descriptors are loaded.

**Output:**
- 🔑 Active keyset (current)
- 🗝️ Inactive keysets
- Balance information in BTC (when descriptors are loaded)

**Example:**
```
debug> list-keysets
💰 Keysets:
  🔑 keyset_abc123 (balance: 0.00050000 BTC)
  🗝️ keyset_def456 (balance: 0.00000000 BTC)
```

#### `list-addresses <keyset_id>`
Derive and display addresses for a specific keyset with their balances.

**Parameters:**
- `keyset_id` - The keyset identifier to derive addresses for

**Requirements:**
- Account must be loaded
- Descriptors must be loaded
- Valid keyset ID

**Output:**
- 📥 Receiving addresses (external chain)
- 📤 Change addresses (internal chain)
- Balance for each address in BTC

**Example:**
```
debug> list-addresses keyset_abc123
🏠 Addresses for keyset keyset_abc123 (gap limit: 20):
  📥 Receiving Addresses:
   bc1q... (balance: 0.00050000 BTC)
   bc1q... (balance: 0.00000000 BTC)
   
  📤 Change Addresses:
   bc1q... (balance: 0.00000000 BTC)
   bc1q... (balance: 0.00000000 BTC)
```

#### `list-recoveries`
Query and display delay & notify recovery operations for the loaded account.

**Output:** YAML-formatted recovery data from DynamoDB

#### `list-relationships`
Query and display social recovery relationships for the loaded account.

**Output:** 
- Relationships where the account is the customer/benefactor
- Relationships where the account is the trusted contact/beneficiary

#### `list-challenges`
Query and display social recovery challenges for the loaded account.

**Output:** YAML-formatted challenge data from DynamoDB

#### `list-claims`
Query and display inheritance claims for the loaded account.

**Output:**
- Claims where the account is the benefactor
- Claims where the account is the beneficiary

---

## AWS Integration

### Environment-Specific AWS Profiles

The debug REPL automatically selects AWS profiles based on the environment:

**Development:**
- Read-only: `bitkey-development--read-only`
- Encrypted attachment reader: `bitkey-development--encrypted-attachment-reader`

**Staging:**
- Read-only: `bitkey-staging--read-only`
- Encrypted attachment reader: `bitkey-staging--encrypted-attachment-reader`

**Production:**
- Read-only: `bitkey-production--read-only`
- Encrypted attachment reader: `bitkey-production--encrypted-attachment-reader`

### Dual-Control Credentials

If your AWS profiles use dual-control (requiring approval from another person), you may see:

```
This AWS role has dual-control enabled. To continue, please have someone approve your AWS access request before it expires:

https://registry.sqprod.co/aws_access_requests/201752?defer=yes

State      Expires in   Refresh in
pending    29m49s       1s
```

The REPL will wait for approval before proceeding.

### DynamoDB Tables

The debug REPL queries these DynamoDB tables:
- `fromagerie.accounts` - Account data
- `fromagerie.encrypted_attachment` - Encrypted wallet descriptors
- `fromagerie.account_recovery` - Delay & notify recoveries
- `fromagerie.social_recovery` - Social recovery relationships and challenges
- `fromagerie.inheritance` - Inheritance claims

---

## Workflow Examples

### Basic Account Inspection

```bash
# Start the REPL
bk debug dev

# Load an account
debug> load-account urn:wallet-account:01JY2S32M4TVHQX1RXN3MZ749T

# View keysets (basic info only)
debug> list-keysets

# Load descriptors for balance information
debug> load-descriptors

# View keysets with balances
debug> list-keysets

# Inspect addresses for a specific keyset
debug> list-addresses keyset_abc123
```

### Recovery Investigation

```bash
# Load account
debug> load-account urn:wallet-account:01JY2S32M4TVHQX1RXN3MZ749T

# Check recovery operations
debug> list-recoveries

# Check social recovery relationships
debug> list-relationships

# Check active challenges
debug> list-challenges

# Check inheritance claims
debug> list-claims
```

### Encrypted Descriptor Analysis

```bash
# Load account
debug> load-account urn:wallet-account:01JY2S32M4TVHQX1RXN3MZ749T

# Load encrypted descriptors by attachment ID
debug> load-descriptors attachment_67890

# View keysets with decrypted balance information
debug> list-keysets

# Analyze addresses
debug> list-addresses keyset_xyz789
```

---

## Technical Details

### State Management

The debug REPL maintains in-memory state that includes:
- **Environment:** Target environment (dev/staging/prod)
- **Account:** Currently loaded account data
- **Descriptors:** Wallet descriptors (encrypted or from account)
- **Wallets:** BDK wallet instances for keysets
- **Configuration:** Gap limit, Electrum server URL
- **AWS Configs:** Cached AWS SDK configurations

### Bitcoin Integration

- **Network Detection:** Automatically detects Bitcoin network (mainnet/testnet/signet) from keyset data
- **Descriptor Types:** Supports WSH (P2WSH) multisig descriptors (2-of-3)
- **Address Derivation:** BIP44-compliant address derivation with configurable gap limits
- **Balance Queries:** Real-time balance queries via Electrum servers

### Cryptographic Operations

- **P256 Encryption:** Decrypts encrypted attachments using P256 elliptic curve cryptography
- **KMS Integration:** Uses AWS KMS for private key decryption
- **Base64 Encoding:** Handles various Base64 encoding formats (standard and no-padding)

---

## Error Handling

All errors are prefixed with ❌ and include detailed context:

- **AWS Errors:** Credential resolution, DynamoDB queries, KMS operations
- **Cryptographic Errors:** Decryption, encoding/decoding failures
- **Bitcoin Errors:** Wallet creation, address derivation, balance queries
- **Validation Errors:** Invalid parameters, missing prerequisites

---

## Keyboard Shortcuts

- **Tab:** Command completion
- **Ctrl+C:** Interrupt current operation or exit
- **Ctrl+D:** Exit the REPL
- **Up/Down Arrows:** Command history navigation

---

## Troubleshooting

### Common Issues

1. **"No account loaded"**
   - Solution: Use `load-account <account_id>` first

2. **"Descriptors not loaded"**
   - Solution: Use `load-descriptors` or `load-descriptors <attachment_id>`

3. **AWS credential timeout**
   - Solution: Ensure dual-control approval is completed within the timeout period
   - Check AWS profile configuration

4. **"Keyset ID not found"**
   - Solution: Use `list-keysets` to see available keyset IDs

5. **Electrum connection errors**
   - Solution: Try a different Electrum server with `set-electrum-server`

### Debug Tips

- Use `clear` to reset state between different account inspections
- Increase gap limit with `set-gap-limit` if you expect addresses beyond the default range
- Check command history with up/down arrows for repeated operations
- Exit and restart the REPL if AWS credentials expire

---

## Security Considerations

- The debug REPL handles sensitive cryptographic operations and account data
- All operations are read-only against production systems
- Credentials are cached in memory only and cleared on exit
- No persistent storage of decrypted data
- All AWS operations use least-privilege access patterns