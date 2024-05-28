use std::{
    env,
    fs::File,
    panic::Location,
    path::{Path, PathBuf},
    sync::{Mutex, OnceLock, PoisonError},
};

use serde::{de::DeserializeOwned, Serialize};

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("Could not create the expectation file")]
    CreateError(#[source] std::io::Error),
    #[error("Unable to write the actual value")]
    WriteError(#[source] serde_json::Error),
    #[error("Could not open the expectation file. Did you create it via `env UPDATE_EXPECT=1 cargo test` ?")]
    NoExpectFile(#[source] std::io::Error),
    #[error("Unable to read the expected value")]
    ReadError(#[source] serde_json::Error),
    #[error("Multiple threads tried to access the same expectation")]
    MutexError(String),
}

impl<T> From<PoisonError<T>> for Error {
    fn from(err: PoisonError<T>) -> Self {
        Self::MutexError(err.to_string())
    }
}

pub struct Expectations {
    directory: PathBuf,
    template: String,
    index: Mutex<usize>,
}

impl Expectations {
    #[track_caller]
    pub fn new(template: &str) -> Self {
        let directory =
            canonicalize_caller_file(Location::caller().file()).with_file_name("expectations");
        Self {
            directory,
            template: template.to_string(),
            index: Mutex::new(0),
        }
    }

    pub fn next_expectation<V>(&self, v: V) -> Result<V, Error>
    where
        V: DeserializeOwned + Serialize,
    {
        let mut index = self.index.lock()?;
        let filepath = self
            .directory
            .join(format!("{}-{}.json", self.template, index));

        let v = if self.is_recording() {
            let f = File::create(filepath).map_err(Error::CreateError)?;
            serde_json::to_writer_pretty(f, &v).map_err(Error::WriteError)?;
            v
        } else {
            let f = File::open(filepath).map_err(Error::NoExpectFile)?;
            serde_json::from_reader(f).map_err(Error::ReadError)?
        };

        *index += 1;
        Ok(v)
    }

    pub fn is_recording(&self) -> bool {
        env::var_os("UPDATE_EXPECT").is_some()
    }
}

fn canonicalize_caller_file(src: &str) -> &'static PathBuf {
    static WORKSPACE_ROOT: OnceLock<PathBuf> = OnceLock::new();
    WORKSPACE_ROOT.get_or_init(|| {
        // Waiting for https://github.com/rust-lang/cargo/issues/3946
        if let Ok(d) = env::var("CARGO_WORKSPACE_DIR") {
            return Path::new(&d).join(src);
        }

        let manifest_directory = env::var("CARGO_MANIFEST_DIR")
            .expect("Could not find workspace root; no CARGO_MANIFEST_DIR env var");

        Path::new(&manifest_directory)
            .ancestors()
            .map(|p| p.join(src))
            .find(|p| p.exists())
            .unwrap_or_else(|| panic!("Could not find {src:?} from the workspace root"))
    })
}
