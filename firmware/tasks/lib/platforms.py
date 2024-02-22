import yaml
import pathlib

from .paths import CONFIG_DIR


class Platforms:
    EXCLUDED_PLATFORMS = ['tasks']

    def __init__(self) -> None:
        cfg = CONFIG_DIR.joinpath("platforms.yaml")
        with open(cfg) as f:
            self.platforms = yaml.safe_load(f)

    @property
    def all(self):
        return self.platforms

    def discover(self, path: pathlib.Path):
        for platform in self.platforms:
            if platform in str(path):
                return self.platforms[platform]
        print(f"Path '{path}' must contain a known platform")
        exit(1)
