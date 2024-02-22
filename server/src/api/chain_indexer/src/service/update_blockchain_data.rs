use super::Service;
use crate::{entities::Block, ChainIndexerError};
use bdk_utils::bdk::bitcoin::Block as BdkBlock;

impl Service {
    pub async fn add_block(&self, block: &BdkBlock) -> Result<(), ChainIndexerError> {
        self.repo
            .persist(&Block::from_bdk_block(block, self.settings.network)?)
            .await?;

        Ok(())
    }
}
