fn main() {
    println!("cargo:rerun-if-changed=uniffi.toml");

    let udl = "src/core.udl";
    uniffi::generate_scaffolding(udl).unwrap();
}
