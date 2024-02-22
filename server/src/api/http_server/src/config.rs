use std::fmt::Debug;
use std::net::IpAddr;

use figment::providers::{Env, Format, Toml};
use figment::Figment;
use serde::Deserialize;
use thiserror::Error;

const DEFAULT_PROFILE: figment::Profile = figment::Profile::const_new(if cfg!(debug_assertions) {
    "debug"
} else {
    "development"
});

#[derive(Clone, Deserialize)]
pub struct Config {
    pub address: IpAddr,
    pub port: u16,
    pub override_current_time: bool,
    pub wallet_telemetry: wallet_telemetry::Config,
}

#[derive(Error, Debug)]
#[error(transparent)]
pub struct Error(#[from] figment::error::Error);

/// 1. The _profile_ of the server is selected (in ascending priority order)
///     - `development` if compiled in release mode, `debug` if compiled in debug mode
///     - The `ROCKET_PROFILE` environment variable (used in production)
///     - The `override_profile` argument passed to the `new_with_profile` function (used in testing)
/// 2. The configuration is composed from the following locations (in ascending priority order):
///     - `Rocket.toml` or the filename specified by the environment variable `ROCKET_CONFIG`
///     - `ROCKET_` prefixed environment variables
///     - `SERVER_` prefixed environment variables
/// 3. The _profile_ is selected and the appropriate configuration deserialized and returned
impl Config {
    pub fn new(profile: Option<&str>) -> Result<Self, Error> {
        extract(profile)
    }
}

pub fn extract<'a, T>(profile: Option<&str>) -> Result<T, Error>
where
    T: Deserialize<'a>,
{
    // TODO: replace ROCKET_ with SERVER_
    let profile = profile
        .map(|p| p.into())
        .or_else(|| figment::Profile::from_env("ROCKET_PROFILE"))
        .unwrap_or(DEFAULT_PROFILE);
    let config = Figment::new()
        .merge(Toml::file(Env::var_or("ROCKET_CONFIG", "Rocket.toml")).nested())
        .merge(Env::prefixed("ROCKET_").ignore(&["PROFILE"]).global())
        .merge(Env::prefixed("SERVER_").global())
        .select(profile)
        .extract()?;

    Ok(config)
}
