use database::ddb::DatabaseError;
use thiserror::Error;

pub mod entities;
pub mod repository;
pub mod service;

#[derive(Error, Debug)]
pub enum MempoolIndexerError {
    #[error("Unable to request HTTP data due to error {0}")]
    HttpClientError(#[from] reqwest::Error),
    #[error("Unable to request HTTP data due to error {0}")]
    HttpMiddlewareError(#[from] reqwest_middleware::Error),
    #[error("Unable to deserialize block due to error {0}")]
    DeserializationError(#[from] bdk_utils::bdk::bitcoin::consensus::encode::Error),
    #[error("Database error {0}")]
    DatabaseError(#[from] DatabaseError),
    #[error("Unable to parse block hash as hex {0}")]
    BlockHashParseError(#[from] bdk_utils::bdk::bitcoin::hashes::hex::Error),
    #[error("Parse Address error: {0}")]
    ParseAddress(#[from] bdk_utils::bdk::bitcoin::address::Error),
}
