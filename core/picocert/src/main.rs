use clap::{ArgGroup, Parser, Subcommand};
use picocert::{issue, validate_cert_chain, Certificate, CertificateWithPrivateKey, Error};
use std::fs;

#[derive(Parser, Debug)]
#[command(name = "picocert")]
struct Cli {
    #[command(subcommand)]
    cmd: Command,
}

#[derive(Subcommand, Debug)]
enum Command {
    #[command(group = ArgGroup::new("cert_type").required(true).args(&["self_signed", "issuer"]))]
    Issue {
        #[arg(long, group = "cert_type")]
        self_signed: bool,
        #[arg(long, group = "cert_type")]
        issuer: Option<String>,
        #[arg(long)]
        issuer_key: Option<String>,
        #[arg(long)]
        subject: String,
        #[arg(long)]
        validity_in_days: u64,
    },
    ValidateChain {
        #[arg(long, required = true, value_delimiter = ' ', num_args = 1..)]
        cert_chain: Vec<String>, // List of certificate file paths; root comes LAST
    },
}

fn main() {
    let cli = Cli::parse();

    match cli.cmd {
        Command::Issue {
            self_signed,
            issuer,
            issuer_key,
            subject,
            validity_in_days,
        } => {
            let issuer_option = if let (Some(issuer), Some(issuer_key)) = (issuer, issuer_key) {
                let issuer_key_bytes = fs::read(issuer_key).expect("Unable to read issuer key");
                let issuer_cert =
                    Certificate::from_file(&issuer).expect("Unable to read issuer cert");
                Some(CertificateWithPrivateKey {
                    cert: issuer_cert,
                    private_key: issuer_key_bytes,
                })
            } else {
                None
            };

            if self_signed && issuer_option.is_some() {
                eprintln!("Cannot specify both --self_signed and --issuer options.");
                std::process::exit(1);
            }

            let valid_from = picocert::current_time();
            let valid_to = valid_from + validity_in_days * 24 * 60 * 60;
            println!("Valid from: {}\nValid to: {}", valid_from, valid_to);

            match issue(
                issuer_option.as_ref(),
                subject.clone(),
                valid_from,
                valid_to,
            ) {
                Ok(issued) => {
                    let cert = issued.cert;
                    let private_key = issued.private_key;

                    let cert_path = subject.clone() + ".pcrt";
                    let private_key_path = subject.clone() + ".priv.der";

                    fs::write(cert_path.clone(), cert.to_bytes())
                        .expect("Unable to write certificate");
                    fs::write(private_key_path.clone(), private_key)
                        .expect("Unable to write private key");

                    println!(
                        "New certificate issued.\nCert: {}\nPrivate key: {}",
                        cert_path, private_key_path
                    );
                }
                Err(err) => {
                    println!("Failed to issue certificate: {:?}", err);
                }
            }
        }
        Command::ValidateChain { cert_chain } => {
            // Read and parse the certificate chain
            let certificates: Result<Vec<Certificate>, Error> = cert_chain
                .iter()
                .map(|path| Certificate::from_file(path))
                .collect();

            match certificates {
                Ok(certificates) => match validate_cert_chain(&certificates) {
                    Ok(_) => {
                        println!("Certificate chain is valid.");
                    }
                    Err(err) => {
                        println!("Validation failed: {:?}", err);
                    }
                },
                Err(err) => {
                    println!("Failed to read certificates: {:?}", err);
                }
            }
        }
    }
}
