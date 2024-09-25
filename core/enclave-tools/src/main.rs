use aws_nitro_enclaves_image_format::utils::eif_reader::EifReader;
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use clap::{Parser, Subcommand};
use enclave_tools::{calculate_pcrs, parse_and_verify_signed_attestation_document};

#[derive(Parser, Debug)]
struct Cli {
    #[command(subcommand)]
    cmd: Command,
}

#[derive(Subcommand, Debug)]
enum Command {
    GetPcr {
        #[arg(long, required = true, help = "PCR index")]
        index: u32,
        #[arg(long, required = true, help = "Path to EIF file")]
        eif: String,
    },
    DecodeAttestation {
        #[arg(long, required = true, help = "Base64 encoded attestation document")]
        attestation: String,
    },
}

fn main() {
    let cli = Cli::parse();

    match cli.cmd {
        Command::GetPcr { index, eif } => {
            let mut eif = EifReader::from_eif(eif.to_string()).expect("Unable to read EIF file");
            let pcrs = calculate_pcrs(&mut eif).expect("Unable to calculate PCRs");
            let pcr = pcrs.get(&format!("PCR{}", index)).expect("PCR not found");
            println!("{}", pcr);
        }
        Command::DecodeAttestation { attestation } => {
            let decoded_doc = BASE64
                .decode(attestation.as_bytes())
                .expect("Invalid Base64");
            let parsed_doc = parse_and_verify_signed_attestation_document(decoded_doc, None)
                .expect("Unable to parse attestation document");
            println!("{}", parsed_doc);
        }
    }
}
