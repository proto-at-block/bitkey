use feature_flags::flag::Flag;

pub const DEFAULT_MAINNET_ELECTRUM_RPC_URI: &str =
    "ssl://bitcoin-mainnet.aed8yee4taeh6tugeiv6.blockstream.info:50002";
pub const DEFAULT_TESTNET_ELECTRUM_RPC_URI: &str =
    "ssl://bitcoin-testnet.aed8yee4taeh6tugeiv6.blockstream.info:50002";
pub const DEFAULT_SIGNET_ELECTRUM_RPC_URI: &str =
    "ssl://bitcoin-signet.aed8yee4taeh6tugeiv6.blockstream.info:50002";

pub const FLAG_MAINNET_ELECTRUM_RPC_URI: Flag<&str> = Flag::new("electrum-rpc-uri-mainnet");
pub const FLAG_TESTNET_ELECTRUM_RPC_URI: Flag<&str> = Flag::new("electrum-rpc-uri-testnet");
pub const FLAG_SIGNET_ELECTRUM_RPC_URI: Flag<&str> = Flag::new("electrum-rpc-uri-signet");
