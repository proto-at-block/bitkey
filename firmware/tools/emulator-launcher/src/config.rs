//! Configuration for the emulator launcher.

use std::path::PathBuf;

#[derive(Clone)]
pub struct Config {
    pub firmware_dir: PathBuf,
    pub core_sim_path: PathBuf,
    pub ui_simulate_path: PathBuf,
    pub core_sim_hash_path: PathBuf,
    pub ui_hash_path: PathBuf,
    pub wca_port: u16,
    pub ui_internal_port: u16,
    pub launcher_port: u16,
    pub monitor_interval_secs: u64,
}

impl Config {
    pub fn new(firmware_dir: PathBuf) -> Self {
        Self {
            core_sim_path: firmware_dir.join("build/core-sim/app/core-sim/core-sim-w3"),
            ui_simulate_path: firmware_dir.join("build/core-sim/ui-simulate/ui-simulate"),
            core_sim_hash_path: firmware_dir.join("build/core-sim/.source_hash_core_sim"),
            ui_hash_path: firmware_dir.join("build/core-sim/.source_hash_ui"),
            firmware_dir,
            wca_port: 5000,
            ui_internal_port: 9000,
            launcher_port: 5001,
            monitor_interval_secs: 2,
        }
    }
}
