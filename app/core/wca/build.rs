extern crate prost_build;

fn main() {
    let manifest_dir = std::path::PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap());
    let toplevel_dir = manifest_dir.join("../../..");
    let firmware_dir = toplevel_dir.join("firmware");

    let proto_include_dir = firmware_dir
        .join("third-party/nanopb")
        .join("generator/proto");
    println!("cargo:rerun-if-changed={}", proto_include_dir.display());

    let proto_source_dir = firmware_dir.join("lib/protobuf/protos");
    println!("cargo:rerun-if-changed={}", proto_source_dir.display());

    let protos = ["wallet.proto"];
    prost_build::compile_protos(&protos, &[proto_include_dir, proto_source_dir]).unwrap();
}
