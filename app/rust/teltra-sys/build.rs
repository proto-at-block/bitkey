use make_cmd::make;
use std::env;
use std::path::PathBuf;

fn main() {
    let target = std::env::var("TARGET").expect("TARGET not found");

    let manifest_dir = std::path::PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap());
    let toplevel_dir = manifest_dir.join("../../..");
    let firmware_dir = toplevel_dir.join("firmware");
    let teltra_dir = firmware_dir.join("lib/telemetry-translator");

    // Include dirs.
    let teltra_includes_dir = teltra_dir.join("inc");
    let teltra_private_includes_dir = firmware_dir.join("lib/telemetry-translator");
    let bitlog_includes_dir = firmware_dir.join("lib/bitlog/inc");
    let helpers_includes_dir = firmware_dir.join("lib/helpers");
    let memfault_defs_dir = firmware_dir.join("hal/memfault/defs");
    let memfault_includes_dir =
        firmware_dir.join("third-party/memfault-firmware-sdk/components/include");

    let output_dir = teltra_dir.join("build");

    // Build the C lib.

    let makefile = teltra_dir.join("Makefile");

    let mut make_cmd = make();
    make_cmd
        .current_dir(teltra_dir)
        .arg(makefile)
        .arg("clean")
        .arg("all")
        .env("RUST_TARGET", &target);

    let make_output = make_cmd.output().expect("Failed to run make");

    if !make_output.status.success() {
        eprintln!(
            "Make failed with:\nstdout: {}\nstderr: {}",
            String::from_utf8_lossy(&make_output.stdout),
            String::from_utf8_lossy(&make_output.stderr)
        );
        std::process::exit(1);
    }

    // Bindgen.

    let teltra_header = teltra_includes_dir.join("telemetry_translator.h");

    let bindings = bindgen::Builder::default()
        .clang_args([
            format!("-I{}", helpers_includes_dir.display()),
            format!("-I{}", bitlog_includes_dir.display()),
            format!("-I{}", memfault_includes_dir.display()),
            format!("-I{}", teltra_private_includes_dir.display()),
            format!("-I{}", memfault_defs_dir.display()),
        ])
        .header(
            teltra_header
                .to_str()
                .expect("Invalid UTF-8 in path")
                .to_owned(),
        )
        .parse_callbacks(Box::new(bindgen::CargoCallbacks::new()))
        .generate()
        .expect("Unable to generate bindings");

    let out_path = PathBuf::from(env::var("OUT_DIR").unwrap());
    bindings
        .write_to_file(out_path.join("bindings.rs"))
        .expect("Couldn't write bindings!");

    // Make everything visible to cargo.
    println!("cargo:include={}", teltra_includes_dir.display());
    println!("cargo:rustc-link-search=native={}", output_dir.display());
    println!("cargo:rustc-link-lib=static=teltra");
}
