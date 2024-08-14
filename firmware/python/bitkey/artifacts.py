from semver import VersionInfo
from tasks.lib.paths import ROOT_DIR
from pathlib import Path
from subprocess import run, CalledProcessError

import shlex


def artifact_name(version: VersionInfo):
    return f"firmware-build-{str(version)}.zip"


class _LocalArtifactCache:
    PATH = ROOT_DIR.joinpath(".releases")
    LIMIT = 250 * 1024 * 1024  # 250 MB

    def __init__(self):
        self.PATH.mkdir(exist_ok=True)

    def fetch(self, version: VersionInfo) -> Path:
        self._maybe_purge()
        f = self.PATH.joinpath(artifact_name(version))
        if f.exists():
            return f
        return None

    def _maybe_purge(self):
        total_size = sum(
            f.stat().st_size for f in self.PATH.glob("**/*") if f.is_file()
        )
        if total_size > self.LIMIT:
            for f in self.PATH.glob("**/*"):
                if f.is_file():
                    f.unlink()


class ArtifactStore:
    def fetch(self, version: VersionInfo):
        # Look in the local cache first.
        cache = _LocalArtifactCache()
        if f := cache.fetch(version):
            return f

        # Fetch from Github
        print(f"Fetching release for version {version}...")
        artifact = artifact_name(version)
        cmd = f"gh release download fw-{str(version)} --repo squareup/wallet --pattern {artifact}"
        try:
            _ = run(
                shlex.split(cmd),
                cwd=cache.PATH,
                check=True,
                capture_output=True,
                text=True,
            )
            fetched_file = cache.PATH.joinpath(artifact)
            if fetched_file.exists():
                return fetched_file
            else:
                raise FileNotFoundError(
                    f"Expected file {fetched_file} not found after download."
                )
        except CalledProcessError as e:
            print(f"Error fetching release: {e}")
            print(e.stdout)
            print(e.stderr)
            return None


def main():
    from sys import argv

    assert len(argv) == 2

    version = VersionInfo.parse(argv[1])
    store = ArtifactStore()
    artifact_path = store.fetch(version)
    if artifact_path:
        print(f"Artifact fetched: {artifact_path}")
    else:
        print("Failed to fetch artifact.")


if __name__ == "__main__":
    main()
