use std::{
    fs::File,
    io::{Cursor, Read, Seek},
};

use anyhow::{bail, Result};
use rustify::blocking::clients::reqwest::Client;
use serde::Deserialize;

use wca::{commands::FirmwareSlot, pcsc::PCSCTransactor};
use zip::ZipArchive;

use crate::nfc::{NFCTransactions, Upload};

pub(crate) fn metadata() -> Result<()> {
    println!("{:?}", PCSCTransactor::new()?.metadata()?);
    Ok(())
}

const MANIFEST_FILE: &str = "fwup-manifest.json";
const MEMFAULT_PROJECT_KEY: &str = "cuMF7SryHhQQcs2gcuEaHqDWV0Z43ha4";

#[derive(Debug, Deserialize)]
struct Manifest {
    manifest_version: String,
    fwup_bundle: FwupBundle,
}

#[derive(Debug, Deserialize)]
struct FwupBundle {
    product: Product,
    version: String,
    assets: Assets,
    parameters: Parameters,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "lowercase")]
enum Product {
    W1A,
}

#[derive(Debug, Deserialize)]
struct Assets {
    bootloader: Asset,
    application_a: Asset,
    application_b: Asset,
}

#[derive(Debug, Deserialize)]
struct Asset {
    image: FileReference,
    signature: FileReference,
}

#[derive(Debug, Deserialize)]
struct FileReference {
    name: String,
}

#[derive(Debug, Deserialize)]
struct Parameters {
    wca_chunk_size: usize,
    signature_offset: u32,
    app_properties_offset: u32,
}

fn read_from_zip<R: Read + Seek>(zip: &mut ZipArchive<R>, name: &str) -> Result<Vec<u8>> {
    let mut buf = Vec::new();
    zip.by_name(name)?.read_to_end(&mut buf)?;
    Ok(buf)
}

pub(crate) fn upload_latest(client: &Client) -> Result<()> {
    let transactor = PCSCTransactor::new()?;

    let device_info = transactor.device_info()?;

    let hardware_version = match device_info.hw_revision.split('-').last() {
        Some(v @ ("evt" | "dvt" | "proto")) => v,
        Some(_) | None => bail!("Invalid hardware revision ('{}')", device_info.hw_revision),
    };

    let software_type = match device_info.sw_type.split('-').last() {
        Some(v @ ("dev" | "prod")) => v,
        Some(_) | None => bail!("Invalid software type ('{}')", device_info.sw_type),
    };

    let firmware_url = client
        .http
        .get("https://device.memfault.com/api/v0/releases/latest/url")
        .query(&[
            ("device_serial", device_info.serial.as_str()),
            ("hardware_version", hardware_version),
            ("software_type", software_type),
            // ("current_version", device_info.version.as_str()),   // The server gives a delta update bundle if `current_version` is present
        ])
        .header("Memfault-Project-Key", MEMFAULT_PROJECT_KEY)
        .send()?
        .text()?;

    let firmware = client.http.get(firmware_url).send()?.bytes()?;

    upload(transactor, ZipArchive::new(Cursor::new(&firmware))?)?;

    Ok(())
}

pub(crate) fn upload_bundle(bundle: std::path::PathBuf) -> Result<()> {
    upload(
        PCSCTransactor::new()?,
        ZipArchive::new(File::open(bundle)?)?,
    )
}

fn upload<R: Read + Seek>(mut transactor: PCSCTransactor, mut zip: ZipArchive<R>) -> Result<()> {
    let manifest: Manifest = serde_json::from_reader(zip.by_name(MANIFEST_FILE)?)?;

    let target_slot = match transactor.metadata()?.active_slot {
        FirmwareSlot::A => FirmwareSlot::B,
        FirmwareSlot::B => FirmwareSlot::A,
    };
    let application = match target_slot {
        FirmwareSlot::A => manifest.fwup_bundle.assets.application_a,
        FirmwareSlot::B => manifest.fwup_bundle.assets.application_b,
    };

    let upload = Upload {
        chunk_size: manifest.fwup_bundle.parameters.wca_chunk_size,
        app_properties_offset: manifest.fwup_bundle.parameters.app_properties_offset,
        application: crate::nfc::Asset {
            data: read_from_zip(&mut zip, &application.image.name)?,
            offset: 0,
        },
        signature: crate::nfc::Asset {
            data: read_from_zip(&mut zip, &application.signature.name)?,
            offset: manifest.fwup_bundle.parameters.signature_offset,
        },
    };

    println!(
        "Uploading {version} to slot {target_slot:?}...",
        version = manifest.fwup_bundle.version
    );
    match transactor.upload(&upload)? {
        wca::commands::FwupFinishRspStatus::Unspecified => {
            println!("Upload failed due to an unspecified error. :-(")
        }
        wca::commands::FwupFinishRspStatus::Success => println!("Upload successful!"),
        wca::commands::FwupFinishRspStatus::SignatureInvalid => {
            println!("Upload failed due to an invalid signature. :-(")
        }
        wca::commands::FwupFinishRspStatus::VersionInvalid => {
            println!("Upload failed due to an invalid version. :-(")
        }
        wca::commands::FwupFinishRspStatus::WillApplyPatch => {
            println!("Patch uploaded. Waiting for hardware to apply patch...")
        }
        wca::commands::FwupFinishRspStatus::Unauthenticated => {
            println!("Unauthenticated. Please unlock your hardware.")
        }
        wca::commands::FwupFinishRspStatus::Error => println!("Upload failed due to an error. :-("),
    };

    Ok(())
}
