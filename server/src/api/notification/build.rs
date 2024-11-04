use prost_build_config::BuildConfig;
use prost_build_config::Builder;
use std::io::Result;

fn main() -> Result<()> {
    let content = include_str!("config/proto_config.yaml");
    let config: BuildConfig = serde_yaml::from_str(content).unwrap();
    let mut builder = Builder::from(config);
    builder.build_protos();
    Ok(())
}
