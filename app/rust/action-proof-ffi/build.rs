fn main() {
    println!("cargo:rerun-if-changed=uniffi.toml");
    println!("cargo:rerun-if-changed=src/action-proof.udl");

    let udl = "src/action-proof.udl";
    uniffi::generate_scaffolding(udl)
        .expect("Failed to generate UniFFI scaffolding from action-proof.udl");
}
