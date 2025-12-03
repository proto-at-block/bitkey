use prost::Message;
#[cfg(not(feature = "mock-time"))]
use std::time::SystemTime;

use crate::{errors::EncodeError, log_buffer::LogBuffer};

const WCA_CLA: u8 = 0x87;
const WCA_INS_VERSION: u8 = 0x74;
const WCA_INS_PROTO: u8 = 0x75;
const WCA_INS_PROTO_CONTINUATION: u8 = 0x77;
const WCA_INS_GET_RESPONSE: u8 = 0x78;

const MAX_WCA_BUFFER_SIZE: usize = 512;
const APDU_OVERHEAD_SIZE: usize = 7; // This could be 5 in some situation ... but why bother?
const MAX_PROTO_SIZE: usize = MAX_WCA_BUFFER_SIZE - APDU_OVERHEAD_SIZE;

pub enum WCA {
    Version,
    Proto(Vec<u8>),
    ProtoContinuation(Vec<u8>),
    GetResponse,
}

impl TryFrom<WCA> for apdu::Command {
    type Error = EncodeError;

    fn try_from(wca: WCA) -> Result<Self, Self::Error> {
        match wca {
            WCA::Version => Ok(apdu::Command::new_header(WCA_CLA, WCA_INS_VERSION, 0, 0)),
            WCA::Proto(fragment) => {
                let size: u16 = fragment.len().try_into()?;
                let p = size.to_be_bytes();
                Ok(apdu::Command::new(
                    WCA_CLA,
                    WCA_INS_PROTO,
                    p[0],
                    p[1],
                    fragment,
                ))
            }
            WCA::ProtoContinuation(fragment) => {
                let size: u16 = fragment.len().try_into()?;
                let p = size.to_be_bytes();
                Ok(apdu::Command::new(
                    WCA_CLA,
                    WCA_INS_PROTO_CONTINUATION,
                    p[0],
                    p[1],
                    fragment,
                ))
            }
            WCA::GetResponse => Ok(apdu::Command::new_header(
                WCA_CLA,
                WCA_INS_GET_RESPONSE,
                0,
                0,
            )),
        }
    }
}

macro_rules! adpu_from_proto {
    ($message:ident) => {
        impl TryFrom<crate::fwpb::$message> for apdu::Command {
            type Error = EncodeError;

            fn try_from(message: crate::fwpb::$message) -> Result<Self, Self::Error> {
                let msg = crate::fwpb::wallet_cmd::Msg::$message(message);
                let cmd = build_cmd(msg);
                WCA::Proto(cmd.encode_to_vec()).try_into()
            }
        }
    };
}

fn get_timestamp() -> u32 {
    #[cfg(feature = "mock-time")]
    {
        1234567890
    }

    #[cfg(not(feature = "mock-time"))]
    {
        match SystemTime::now().duration_since(SystemTime::UNIX_EPOCH) {
            Ok(n) => {
                let seconds_u64 = n.as_secs();
                let seconds_u32 = seconds_u64 as u32;
                if seconds_u64 > u32::MAX as u64 {
                    println!("Warning: Current time is greater than u32::MAX");
                    0
                } else {
                    seconds_u32
                }
            }
            Err(_) => {
                println!("Warning: SystemTime before UNIX EPOCH!");
                0
            }
        }
    }
}

fn build_cmd(msg: crate::fwpb::wallet_cmd::Msg) -> crate::fwpb::WalletCmd {
    let cmd = crate::fwpb::WalletCmd {
        msg: Some(msg),
        timestamp: get_timestamp(),
    };
    LogBuffer::put(format!("{:?}", cmd));
    cmd
}

adpu_from_proto!(DeriveKeyDescriptorAndSignCmd);
adpu_from_proto!(DeriveKeyDescriptorCmd);
adpu_from_proto!(DeviceIdCmd);
adpu_from_proto!(EventsGetCmd);
adpu_from_proto!(FeatureFlagsGetCmd);
adpu_from_proto!(FeatureFlagsSetCmd);
adpu_from_proto!(FwupFinishCmd);
adpu_from_proto!(FwupStartCmd);
adpu_from_proto!(FwupTransferCmd);
adpu_from_proto!(GetFingerprintEnrollmentStatusCmd);
adpu_from_proto!(MetaCmd);
adpu_from_proto!(QueryAuthenticationCmd);
adpu_from_proto!(SealCsekCmd);
adpu_from_proto!(SignTxnCmd);
adpu_from_proto!(StartFingerprintEnrollmentCmd);
adpu_from_proto!(TelemetryIdGetCmd);
adpu_from_proto!(DeviceInfoCmd);
adpu_from_proto!(UnsealCsekCmd);
adpu_from_proto!(WipeStateCmd);
adpu_from_proto!(LockDeviceCmd);
adpu_from_proto!(CertGetCmd);
adpu_from_proto!(DerivePublicKeyCmd);
adpu_from_proto!(DerivePublicKeyAndSignCmd);
adpu_from_proto!(HardwareAttestationCmd);
adpu_from_proto!(SetFingerprintLabelCmd);
adpu_from_proto!(GetEnrolledFingerprintsCmd);
adpu_from_proto!(DeleteFingerprintCmd);
adpu_from_proto!(GetUnlockMethodCmd);
adpu_from_proto!(CancelFingerprintEnrollmentCmd);
adpu_from_proto!(FingerprintResetRequestCmd);
adpu_from_proto!(FingerprintResetFinalizeCmd);
adpu_from_proto!(ProvisionAppAuthPubkeyCmd);

impl TryFrom<crate::fwpb::CoredumpGetCmd> for apdu::Command {
    type Error = EncodeError;

    fn try_from(message: crate::fwpb::CoredumpGetCmd) -> Result<Self, Self::Error> {
        let msg = crate::fwpb::wallet_cmd::Msg::CoredumpGetCmd(message);
        let cmd = build_cmd(msg);
        WCA::Proto(cmd.encode_to_vec()).try_into()
    }
}

impl TryFrom<crate::fwpb::wallet_cmd::Msg> for Vec<apdu::Command> {
    type Error = EncodeError;

    fn try_from(command: crate::fwpb::wallet_cmd::Msg) -> Result<Self, Self::Error> {
        let mut proto_bytes = Vec::new();
        command.encode(&mut proto_bytes);
        let mut fragments = proto_bytes.chunks(MAX_PROTO_SIZE).map(|c| c.to_vec());

        let mut rv = Vec::new();
        let first = fragments.next().ok_or(EncodeError::TruncatedProto)?;
        rv.push(WCA::Proto(first).try_into()?);
        for chunk in fragments {
            rv.push(WCA::ProtoContinuation(chunk).try_into()?);
        }

        Ok(rv)
    }
}

/// Decode an APDU response into a protobuf, and check for errors set on the global status fields.
pub fn decode_and_check(
    response: apdu::Response,
) -> Result<crate::fwpb::WalletRsp, crate::errors::CommandError> {
    let message = crate::fwpb::WalletRsp::decode(std::io::Cursor::new(response.data))?;

    LogBuffer::put(format!("{:?}", message));

    match crate::fwpb::Status::try_from(message.status) {
        Ok(crate::fwpb::Status::Unspecified) => Ok(message), // TODO(W-1211): This should be an error once all devices have firmware that supports this status code.
        Ok(crate::fwpb::Status::Success) => Ok(message),
        Ok(crate::fwpb::Status::InProgress) => Ok(message),
        Ok(crate::fwpb::Status::Error) => Err(crate::errors::CommandError::GeneralCommandError),
        Ok(crate::fwpb::Status::Unauthenticated) => {
            Err(crate::errors::CommandError::Unauthenticated)
        }
        Ok(crate::fwpb::Status::UnknownMessage) => Err(crate::errors::CommandError::UnknownMessage),
        Ok(crate::fwpb::Status::NoSecureChannel) => {
            Err(crate::errors::CommandError::NoSecureChannel)
        }
        Ok(crate::fwpb::Status::KeyDerivationFailed) => {
            Err(crate::errors::CommandError::KeyDerivationFailed)
        }
        Ok(crate::fwpb::Status::SigningFailed) => Err(crate::errors::CommandError::SigningError),
        Ok(crate::fwpb::Status::SecureChannelError) => {
            Err(crate::errors::CommandError::SecureChannelError)
        }
        Ok(crate::fwpb::Status::WrongSecret) => Err(crate::errors::CommandError::WrongSecret),
        Ok(crate::fwpb::Status::StorageErr) => Err(crate::errors::CommandError::StorageErr),
        Ok(crate::fwpb::Status::NoSecretProvisioned) => {
            Err(crate::errors::CommandError::NoSecretProvisioned)
        }
        Ok(crate::fwpb::Status::WaitingOnDelay) => Err(crate::errors::CommandError::WaitingOnDelay),
        Ok(crate::fwpb::Status::FeatureNotSupported) => {
            Err(crate::errors::CommandError::FeatureNotSupported)
        }
        Ok(crate::fwpb::Status::FileNotFound) => Err(crate::errors::CommandError::FileNotFound),
        Ok(crate::fwpb::Status::InvalidState) => Err(crate::errors::CommandError::InvalidState),
        Ok(crate::fwpb::Status::InvalidArgument) => {
            Err(crate::errors::CommandError::InvalidArguments)
        }
        Ok(crate::fwpb::Status::VerificationFailed) => {
            Err(crate::errors::CommandError::SignatureInvalid)
        }
        Ok(crate::fwpb::Status::RequestMismatch) => {
            Err(crate::errors::CommandError::RequestMismatch)
        }
        Ok(crate::fwpb::Status::VersionMismatch) => {
            Err(crate::errors::CommandError::VersionInvalid)
        }
        Err(_) => Ok(message), // TODO(W-1211): Same as above comment.
    }
}
