# Transfer Link

Transfer Link allows partner applications to initiate Bitcoin transfer flows that redirect users to the Bitkey app for
confirmation and address generation. Once confirmed, users are seamlessly redirected back to the partner platform.

This system enables partner platforms to:

- Request Bitcoin addresses from Bitkey users
- Establish trusted transfer relationships
- Provide seamless user experience across applications
- Maintain security through tokenized authentication

## How TransferLink Works

The TransferLink flow consists of 6 main steps:

### 1. Partner Deeplink Initiation

- User interacts with a partner app (e.g., Strike)
- Partner app generates a tokenized secret for the transfer request
- Partner deeplinks into Bitkey app with the tokenized secret
- Bitkey app receives the deeplink and extracts the transfer parameters

### 2. User Confirmation

- Bitkey presents the transfer link request to the user
- User confirms they want to establish the transfer link with the partner

### 3. Address Generation & F8e Call

- Bitkey app generates a new Bitcoin address for the transfer
- Bitkey calls F8e with:
    - The generated Bitcoin address
    - The tokenized secret from the partner

### 4. Partner Backend Communication

- F8e validates the token and transfer request
- F8e securely communicates with the partner backend
- F8e sends the Bitcoin address and validated token to partner
- Partner backend processes and validates the transfer link establishment

### 5. Partner Deeplink Response

- F8e receives confirmation from partner backend
- F8e generates a deeplink back to the partner platform
- F8e returns the partner deeplink to Bitkey app

### 6. Return to Partner

- Bitkey app follows the returned deeplink
- User is redirected back to the partner platform

## Manual testing
on Android:
adb shell 'am start -a android.intent.action.VIEW -d "https://bitkey.world/links/app?context=partner_transfer_link&source=<partner>>&event=link_requested&event_id=<tokenized_secret>"'
```