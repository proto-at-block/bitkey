use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{wallet_rsp::Msg, GetConfirmationResultChunkCmd, GetConfirmationResultChunkRsp},
    wca::decode_and_check,
};

use crate::command_interface::command;

/// Data chunk from a confirmed operation that produces large output.
#[derive(Debug, Clone)]
pub struct ChunkData {
    /// The chunk of data bytes.
    pub chunk: Vec<u8>,
    /// True if this is the last chunk.
    pub is_last: bool,
    /// Size (bytes) of data still remaining after this chunk (always non-negative).
    pub remaining_size: u32,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_confirmation_result_chunk(
    response_handle: Vec<u8>,
    confirmation_handle: Vec<u8>,
    chunk_index: u32,
) -> Result<ChunkData, CommandError> {
    let apdu: apdu::Command = GetConfirmationResultChunkCmd {
        response_handle,
        confirmation_handle,
        chunk_index,
    }
    .try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::GetConfirmationResultChunkRsp(GetConfirmationResultChunkRsp {
        chunk,
        is_last,
        remaining_size,
    }) = message
    {
        Ok(ChunkData {
            chunk,
            is_last,
            remaining_size,
        })
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetConfirmationResultChunk = get_confirmation_result_chunk -> ChunkData, response_handle: Vec<u8>, confirmation_handle: Vec<u8>, chunk_index: u32);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{wallet_rsp::Msg, GetConfirmationResultChunkRsp, Status, WalletRsp},
    };

    use super::{ChunkData, GetConfirmationResultChunk};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn get_confirmation_result_chunk_success() -> Result<(), CommandError> {
        let command = GetConfirmationResultChunk::new(
            vec![0x01, 0x02, 0x03, 0x04],
            vec![0x05, 0x06, 0x07, 0x08],
            0, // chunk_index
        );
        command.next(Vec::default())?;

        let chunk_data = vec![0x11, 0x22, 0x33, 0x44];
        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultChunkRsp(
                GetConfirmationResultChunkRsp {
                    chunk: chunk_data.clone(),
                    is_last: false,
                    remaining_size: 452,
                },
            )),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value:
                    ChunkData {
                        chunk,
                        is_last,
                        remaining_size,
                    },
            }) => {
                assert_eq!(chunk, chunk_data);
                assert!(!is_last);
                assert_eq!(remaining_size, 452);
            }
            other => panic!("Expected ChunkData, got {:?}", other),
        }

        Ok(())
    }

    #[test]
    fn get_confirmation_result_chunk_last() -> Result<(), CommandError> {
        let command = GetConfirmationResultChunk::new(
            vec![0x01, 0x02, 0x03, 0x04],
            vec![0x05, 0x06, 0x07, 0x08],
            1, // chunk_index - requesting second (last) chunk
        );
        command.next(Vec::default())?;

        let chunk_data = vec![0x55, 0x66, 0x77, 0x88];
        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultChunkRsp(
                GetConfirmationResultChunkRsp {
                    chunk: chunk_data.clone(),
                    is_last: true,
                    remaining_size: 0,
                },
            )),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value:
                    ChunkData {
                        chunk,
                        is_last,
                        remaining_size,
                    },
            }) => {
                assert_eq!(chunk, chunk_data);
                assert!(is_last);
                assert_eq!(remaining_size, 0);
            }
            other => panic!("Expected ChunkData with is_last=true, got {:?}", other),
        }

        Ok(())
    }
}
