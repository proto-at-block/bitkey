use crate::error::{
    Bip32Error, Bip39Error, CannotConnectError, DescriptorError, DescriptorKeyError, ElectrumError,
    EsploraError, ExtractTxError, PsbtError, PsbtParseError, RequestBuilderError, SignerError,
    TransactionError, TxidParseError,
};

#[test]
fn test_error_bip32() {
    let cases = vec![
        (
            Bip32Error::CannotDeriveFromHardenedKey,
            "cannot derive from a hardened key",
        ),
        (
            Bip32Error::Secp256k1 {
                error_message: "failure".to_string(),
            },
            "secp256k1 error: failure",
        ),
        (
            Bip32Error::InvalidChildNumber { child_number: 123 },
            "invalid child number: 123",
        ),
        (
            Bip32Error::InvalidChildNumberFormat,
            "invalid format for child number",
        ),
        (
            Bip32Error::InvalidDerivationPathFormat,
            "invalid derivation path format",
        ),
        (
            Bip32Error::UnknownVersion {
                version: "0x123".to_string(),
            },
            "unknown version: 0x123",
        ),
        (
            Bip32Error::WrongExtendedKeyLength { length: 512 },
            "wrong extended key length: 512",
        ),
        (
            Bip32Error::Base58 {
                error_message: "error".to_string(),
            },
            "base58 error: error",
        ),
        (
            Bip32Error::Hex {
                error_message: "error".to_string(),
            },
            "hexadecimal conversion error: error",
        ),
        (
            Bip32Error::InvalidPublicKeyHexLength { length: 66 },
            "invalid public key hex length: 66",
        ),
        (
            Bip32Error::UnknownError {
                error_message: "mystery".to_string(),
            },
            "unknown error: mystery",
        ),
    ];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}

#[test]
fn test_error_bip39() {
    let cases = vec![
        (
            Bip39Error::BadWordCount { word_count: 15 },
            "the word count 15 is not supported",
        ),
        (
            Bip39Error::UnknownWord { index: 102 },
            "unknown word at index 102",
        ),
        (
            Bip39Error::BadEntropyBitCount { bit_count: 128 },
            "entropy bit count 128 is invalid",
        ),
        (Bip39Error::InvalidChecksum, "checksum is invalid"),
        (
            Bip39Error::AmbiguousLanguages {
                languages: "English, Spanish".to_string(),
            },
            "ambiguous languages detected: English, Spanish",
        ),
    ];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}

#[test]
fn test_error_cannot_connect() {
    let error = CannotConnectError::Include { height: 42 };

    assert_eq!(format!("{}", error), "cannot include height: 42");
}

#[test]
fn test_error_descriptor() {
    let cases = vec![
        (DescriptorError::InvalidHdKeyPath, "invalid hd key path"),
        (
            DescriptorError::InvalidDescriptorChecksum,
            "the provided descriptor doesn't match its checksum",
        ),
        (
            DescriptorError::HardenedDerivationXpub,
            "the descriptor contains hardened derivation steps on public extended keys",
        ),
        (
            DescriptorError::MultiPath,
            "the descriptor contains multipath keys, which are not supported yet",
        ),
        (
            DescriptorError::Key {
                error_message: "Invalid key format".to_string(),
            },
            "key error: Invalid key format",
        ),
        (
            DescriptorError::Policy {
                error_message: "Policy rule failed".to_string(),
            },
            "policy error: Policy rule failed",
        ),
        (
            DescriptorError::InvalidDescriptorCharacter {
                char: "}".to_string(),
            },
            "invalid descriptor character: }",
        ),
        (
            DescriptorError::Bip32 {
                error_message: "Bip32 error".to_string(),
            },
            "bip32 error: Bip32 error",
        ),
        (
            DescriptorError::Base58 {
                error_message: "Base58 decode error".to_string(),
            },
            "base58 error: Base58 decode error",
        ),
        (
            DescriptorError::Pk {
                error_message: "Public key error".to_string(),
            },
            "key-related error: Public key error",
        ),
        (
            DescriptorError::Miniscript {
                error_message: "Miniscript evaluation error".to_string(),
            },
            "miniscript error: Miniscript evaluation error",
        ),
        (
            DescriptorError::Hex {
                error_message: "Hexadecimal decoding error".to_string(),
            },
            "hex decoding error: Hexadecimal decoding error",
        ),
    ];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}

#[test]
fn test_error_descriptor_key() {
    let cases = vec![
        (
            DescriptorKeyError::Parse {
                error_message: "Failed to parse descriptor key".to_string(),
            },
            "error parsing descriptor key: Failed to parse descriptor key",
        ),
        (DescriptorKeyError::InvalidKeyType, "error invalid key type"),
        (
            DescriptorKeyError::Bip32 {
                error_message: "BIP32 derivation error".to_string(),
            },
            "error bip 32 related: BIP32 derivation error",
        ),
    ];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}

#[test]
fn test_error_electrum_client() {
    let cases = vec![
        (
            ElectrumError::IOError { error_message: "message".to_string(), },
            "message",
        ),
        (
            ElectrumError::Json { error_message: "message".to_string(), },
            "message",
        ),
        (
            ElectrumError::Hex { error_message: "message".to_string(), },
            "message",
        ),
        (
            ElectrumError::Protocol { error_message: "message".to_string(), },
            "electrum server error: message",
        ),
        (
            ElectrumError::Bitcoin {
                error_message: "message".to_string(),
            },
            "message",
        ),
        (
            ElectrumError::AlreadySubscribed,
            "already subscribed to the notifications of an address",
        ),
        (
            ElectrumError::NotSubscribed,
            "not subscribed to the notifications of an address",
        ),
        (
            ElectrumError::InvalidResponse {
                error_message: "message".to_string(),
            },
            "error during the deserialization of a response from the server: message",
        ),
        (
            ElectrumError::Message {
                error_message: "message".to_string(),
            },
            "message",
        ),
        (
            ElectrumError::InvalidDNSNameError {
                domain: "domain".to_string(),
            },
            "invalid domain name domain not matching SSL certificate",
        ),
        (
            ElectrumError::MissingDomain,
            "missing domain while it was explicitly asked to validate it",
        ),
        (
            ElectrumError::AllAttemptsErrored,
            "made one or multiple attempts, all errored",
        ),
        (
            ElectrumError::SharedIOError {
                error_message: "message".to_string(),
            },
            "message",
        ),
        (
            ElectrumError::CouldntLockReader,
            "couldn't take a lock on the reader mutex. This means that there's already another reader thread is running"
        ),
        (
            ElectrumError::Mpsc,
            "broken IPC communication channel: the other thread probably has exited",
        ),
        (
            ElectrumError::CouldNotCreateConnection {
                error_message: "message".to_string(),
            },
            "message",
        )
    ];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}

#[test]
fn test_error_esplora() {
    let cases = vec![
        (
            EsploraError::Minreq {
                error_message: "Network error".to_string(),
            },
            "minreq error: Network error",
        ),
        (
            EsploraError::HttpResponse {
                status: 404,
                error_message: "Not found".to_string(),
            },
            "http error with status code 404 and message Not found",
        ),
        (
            EsploraError::StatusCode {
                error_message: "code 1234567".to_string(),
            },
            "invalid status code, unable to convert to u16: code 1234567",
        ),
        (
            EsploraError::Parsing {
                error_message: "Invalid JSON".to_string(),
            },
            "parsing error: Invalid JSON",
        ),
        (
            EsploraError::BitcoinEncoding {
                error_message: "Bad format".to_string(),
            },
            "bitcoin encoding error: Bad format",
        ),
        (
            EsploraError::HexToArray {
                error_message: "Invalid hex".to_string(),
            },
            "invalid hex data returned: Invalid hex",
        ),
        (
            EsploraError::HexToBytes {
                error_message: "Invalid hex".to_string(),
            },
            "invalid hex data returned: Invalid hex",
        ),
        (EsploraError::TransactionNotFound, "transaction not found"),
        (
            EsploraError::HeaderHeightNotFound { height: 123456 },
            "header height 123456 not found",
        ),
        (EsploraError::HeaderHashNotFound, "header hash not found"),
        (
            EsploraError::RequestAlreadyConsumed,
            "the request has already been consumed",
        ),
    ];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}

#[test]
fn test_error_extract_tx() {
    let cases = vec![
        (
            ExtractTxError::AbsurdFeeRate { fee_rate: 10000 },
            "an absurdly high fee rate of 10000 sat/vbyte",
        ),
        (
            ExtractTxError::MissingInputValue,
            "one of the inputs lacked value information (witness_utxo or non_witness_utxo)",
        ),
        (
            ExtractTxError::SendingTooMuch,
            "transaction would be invalid due to output value being greater than input value",
        ),
        (
            ExtractTxError::OtherExtractTxErr,
            "this error is required because the bdk::bitcoin::psbt::ExtractTxError is non-exhaustive"
        ),
    ];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}

#[test]
fn test_error_inspect() {
    let cases = vec![(
        RequestBuilderError::RequestAlreadyConsumed,
        "the request has already been consumed",
    )];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}

#[test]
fn test_error_psbt() {
    let cases = vec![
        (PsbtError::InvalidMagic, "invalid magic"),
        (
            PsbtError::MissingUtxo,
            "UTXO information is not present in PSBT",
        ),
        (PsbtError::InvalidSeparator, "invalid separator"),
        (
            PsbtError::PsbtUtxoOutOfBounds,
            "output index is out of bounds of non witness script output array",
        ),
        (
            PsbtError::InvalidKey {
                key: "key".to_string(),
            },
            "invalid key: key",
        ),
        (
            PsbtError::InvalidProprietaryKey,
            "non-proprietary key type found when proprietary key was expected",
        ),
        (
            PsbtError::DuplicateKey {
                key: "key".to_string(),
            },
            "duplicate key: key",
        ),
        (
            PsbtError::UnsignedTxHasScriptSigs,
            "the unsigned transaction has script sigs",
        ),
        (
            PsbtError::UnsignedTxHasScriptWitnesses,
            "the unsigned transaction has script witnesses",
        ),
        (
            PsbtError::MustHaveUnsignedTx,
            "partially signed transactions must have an unsigned transaction",
        ),
        (
            PsbtError::NoMorePairs,
            "no more key-value pairs for this psbt map",
        ),
        (
            PsbtError::UnexpectedUnsignedTx,
            "different unsigned transaction",
        ),
        (
            PsbtError::NonStandardSighashType { sighash: 200 },
            "non-standard sighash type: 200",
        ),
        (
            PsbtError::InvalidHash {
                hash: "abcde".to_string(),
            },
            "invalid hash when parsing slice: abcde",
        ),
        (
            PsbtError::InvalidPreimageHashPair,
            "preimage does not match",
        ),
        (
            PsbtError::CombineInconsistentKeySources {
                xpub: "xpub".to_string(),
            },
            "combine conflict: xpub",
        ),
        (
            PsbtError::ConsensusEncoding {
                encoding_error: "encoding error".to_string(),
            },
            "bitcoin consensus encoding error: encoding error",
        ),
        (
            PsbtError::NegativeFee,
            "PSBT has a negative fee which is not allowed",
        ),
        (
            PsbtError::FeeOverflow,
            "integer overflow in fee calculation",
        ),
        (
            PsbtError::InvalidPublicKey {
                error_message: "invalid public key".to_string(),
            },
            "invalid public key invalid public key",
        ),
        (
            PsbtError::InvalidSecp256k1PublicKey {
                secp256k1_error: "invalid secp256k1 public key".to_string(),
            },
            "invalid secp256k1 public key: invalid secp256k1 public key",
        ),
        (PsbtError::InvalidXOnlyPublicKey, "invalid xonly public key"),
        (
            PsbtError::InvalidEcdsaSignature {
                error_message: "invalid ecdsa signature".to_string(),
            },
            "invalid ECDSA signature: invalid ecdsa signature",
        ),
        (
            PsbtError::InvalidTaprootSignature {
                error_message: "invalid taproot signature".to_string(),
            },
            "invalid taproot signature: invalid taproot signature",
        ),
        (PsbtError::InvalidControlBlock, "invalid control block"),
        (PsbtError::InvalidLeafVersion, "invalid leaf version"),
        (PsbtError::Taproot, "taproot error"),
        (
            PsbtError::TapTree {
                error_message: "tap tree error".to_string(),
            },
            "taproot tree error: tap tree error",
        ),
        (PsbtError::XPubKey, "xpub key error"),
        (
            PsbtError::Version {
                error_message: "version error".to_string(),
            },
            "version error: version error",
        ),
        (
            PsbtError::PartialDataConsumption,
            "data not consumed entirely when explicitly deserializing",
        ),
        (
            PsbtError::Io {
                error_message: "io error".to_string(),
            },
            "I/O error: io error",
        ),
        (PsbtError::OtherPsbtErr, "other PSBT error"),
    ];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}

#[test]
fn test_error_psbt_parse() {
    let cases = vec![
        (
            PsbtParseError::PsbtEncoding {
                error_message: "invalid PSBT structure".to_string(),
            },
            "error in internal psbt data structure: invalid PSBT structure",
        ),
        (
            PsbtParseError::Base64Encoding {
                error_message: "base64 decode error".to_string(),
            },
            "error in psbt base64 encoding: base64 decode error",
        ),
    ];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}

#[test]
fn test_signer_errors() {
    let errors = vec![
        (SignerError::MissingKey, "missing key for signing"),
        (SignerError::InvalidKey, "invalid key provided"),
        (SignerError::UserCanceled, "user canceled operation"),
        (
            SignerError::InputIndexOutOfRange,
            "input index out of range",
        ),
        (
            SignerError::MissingNonWitnessUtxo,
            "missing non-witness utxo information",
        ),
        (
            SignerError::InvalidNonWitnessUtxo,
            "invalid non-witness utxo information provided",
        ),
        (SignerError::MissingWitnessUtxo, "missing witness utxo"),
        (SignerError::MissingWitnessScript, "missing witness script"),
        (SignerError::MissingHdKeypath, "missing hd keypath"),
        (
            SignerError::NonStandardSighash,
            "non-standard sighash type used",
        ),
        (SignerError::InvalidSighash, "invalid sighash type provided"),
        (
            SignerError::MiniscriptPsbt {
                error_message: "psbt issue".into(),
            },
            "miniscript psbt error: psbt issue",
        ),
        (
            SignerError::External {
                error_message: "external error".into(),
            },
            "external error: external error",
        ),
    ];

    for (error, message) in errors {
        assert_eq!(error.to_string(), message);
    }
}

#[test]
fn test_error_transaction() {
    let cases = vec![
        (TransactionError::Io, "io error"),
        (
            TransactionError::OversizedVectorAllocation,
            "allocation of oversized vector",
        ),
        (
            TransactionError::InvalidChecksum {
                expected: "deadbeef".to_string(),
                actual: "beadbeef".to_string(),
            },
            "invalid checksum: expected=deadbeef actual=beadbeef",
        ),
        (TransactionError::NonMinimalVarInt, "non-minimal var int"),
        (TransactionError::ParseFailed, "parse failed"),
        (
            TransactionError::UnsupportedSegwitFlag { flag: 1 },
            "unsupported segwit version: 1",
        ),
        (
            TransactionError::OtherTransactionErr,
            "other transaction error",
        ),
    ];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}

#[test]
fn test_error_txid_parse() {
    let cases = vec![(
        TxidParseError::InvalidTxid {
            txid: "123abc".to_string(),
        },
        "invalid txid: 123abc",
    )];

    for (error, expected_message) in cases {
        assert_eq!(error.to_string(), expected_message);
    }
}
