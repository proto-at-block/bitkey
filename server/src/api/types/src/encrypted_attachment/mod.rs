use crate::{
    account::identifiers::AccountId, encrypted_attachment::identifiers::EncryptedAttachmentId,
};
use serde::{Deserialize, Serialize};
use serde_with::{base64::Base64, serde_as};
use time::OffsetDateTime;

pub mod identifiers;

/// Represents an encrypted attachment, which is created when a user opts to include an
/// encrypted wallet descriptor with their debug data submission.
///
/// When a user enables this feature, the application:
/// 1. Creates this `EncryptedAttachment` resource.
/// 2. Encrypts the wallet descriptor and uploads it, storing it in `sealed_attachment`.
/// 3. Includes the `id` of this resource in the debug data submitted with a support ticket.
///
/// This allows authorized personnel to access and decrypt the wallet descriptor for
/// troubleshooting, using the provided keys and identifiers.
#[serde_as]
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct EncryptedAttachment {
    #[serde(rename = "partition_key")]
    pub id: EncryptedAttachmentId,
    pub account_id: AccountId,
    pub kms_key_id: String,
    #[serde_as(as = "Base64")]
    pub private_key_ciphertext: Vec<u8>,
    #[serde_as(as = "Base64")]
    pub public_key: Vec<u8>,
    #[serde_as(as = "Option<Base64>")]
    pub sealed_attachment: Option<Vec<u8>>,
    #[serde(with = "time::serde::rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "time::serde::rfc3339")]
    pub updated_at: OffsetDateTime,
}

#[cfg(test)]
mod tests {
    use super::*;
    use time::OffsetDateTime;

    #[test]
    fn test_encrypted_attachment_serde_round_trip() {
        use external_identifier::ExternalIdentifier;
        use ulid::Ulid;

        let attachment = EncryptedAttachment {
            id: EncryptedAttachmentId::new(Ulid::default()).unwrap(),
            account_id: AccountId::new(Ulid::default()).unwrap(),
            kms_key_id: "test-kms-key-id".to_string(),
            private_key_ciphertext: "encrypted-private-key".as_bytes().to_vec(),
            public_key: "public-key-data".as_bytes().to_vec(),
            sealed_attachment: Some("sealed-attachment-data".as_bytes().to_vec()),
            created_at: OffsetDateTime::from_unix_timestamp(1672531200).unwrap(),
            updated_at: OffsetDateTime::from_unix_timestamp(1672531200).unwrap(),
        };

        // Test JSON serialization round trip
        let json = serde_json::to_string(&attachment).expect("Failed to serialize to JSON");
        let deserialized: EncryptedAttachment =
            serde_json::from_str(&json).expect("Failed to deserialize from JSON");
        assert_eq!(attachment, deserialized);

        // Test that partition_key is properly renamed in JSON
        assert!(json.contains("partition_key"));
        assert!(!json.contains("\"id\""));
    }

    #[test]
    fn test_encrypted_attachment_field_mapping() {
        let json = r#"{
            "partition_key": "urn:wallet-encrypted-attachment:00000000000000000000000000",
            "account_id": "urn:wallet-account:00000000000000000000000000",
            "kms_key_id": "test-kms-key-id",
            "private_key_ciphertext": "ZW5jcnlwdGVkLXByaXZhdGUta2V5",
            "public_key": "cHVibGljLWtleS1kYXRh",
            "sealed_attachment": "c2VhbGVkLWF0dGFjaG1lbnQtZGF0YQ==",
            "created_at": "2023-01-01T00:00:00Z",
            "updated_at": "2023-01-01T00:00:00Z"
        }"#;

        let attachment: EncryptedAttachment =
            serde_json::from_str(json).expect("Failed to deserialize");
        assert_eq!(attachment.kms_key_id, "test-kms-key-id");
        assert_eq!(
            attachment.private_key_ciphertext,
            "encrypted-private-key".as_bytes().to_vec()
        );
        assert_eq!(attachment.public_key, "public-key-data".as_bytes().to_vec());
        assert_eq!(
            attachment.sealed_attachment,
            Some("sealed-attachment-data".as_bytes().to_vec())
        );
    }

    #[test]
    fn test_encrypted_attachment_with_none_sealed_attachment() {
        use external_identifier::ExternalIdentifier;
        use ulid::Ulid;

        let attachment = EncryptedAttachment {
            id: EncryptedAttachmentId::new(Ulid::default()).unwrap(),
            account_id: AccountId::new(Ulid::default()).unwrap(),
            kms_key_id: "test-kms-key-id".to_string(),
            private_key_ciphertext: "encrypted-private-key".as_bytes().to_vec(),
            public_key: "public-key-data".as_bytes().to_vec(),
            sealed_attachment: None,
            created_at: OffsetDateTime::from_unix_timestamp(1672531200).unwrap(),
            updated_at: OffsetDateTime::from_unix_timestamp(1672531200).unwrap(),
        };

        // Test JSON serialization round trip with None sealed_attachment
        let json = serde_json::to_string(&attachment).expect("Failed to serialize to JSON");
        let deserialized: EncryptedAttachment =
            serde_json::from_str(&json).expect("Failed to deserialize from JSON");
        assert_eq!(attachment, deserialized);
        assert_eq!(attachment.sealed_attachment, None);
    }

    #[test]
    fn test_encrypted_attachment_optional_field_deserialization() {
        // Test with sealed_attachment absent (should default to null/None)
        let json = r#"{
            "partition_key": "urn:wallet-encrypted-attachment:00000000000000000000000000",
            "account_id": "urn:wallet-account:00000000000000000000000000",
            "kms_key_id": "test-kms-key-id",
            "private_key_ciphertext": "ZW5jcnlwdGVkLXByaXZhdGUta2V5",
            "public_key": "cHVibGljLWtleS1kYXRh",
            "created_at": "2023-01-01T00:00:00Z",
            "updated_at": "2023-01-01T00:00:00Z"
        }"#;

        let attachment_without_sealed: EncryptedAttachment =
            serde_json::from_str(json).expect("Failed to deserialize");
        assert_eq!(attachment_without_sealed.sealed_attachment, None);
    }
}
