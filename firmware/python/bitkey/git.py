import subprocess
import sys

from semver import VersionInfo


class Git():
    _info = dict()
    _os_git_cmd = 'git'
    _tag_prefix = "fw-"

    def __init__(self) -> None:
        self._info = {
            'identity': self._get_identity(),
            'branch': self._get_branch(),
            'semver_tag': self._get_semver_tag(),
            'head_rev': self._run_git_cmd(["rev-parse", "HEAD"]),
        }

    def info(self) -> dict:
        return self._info

    @property
    def identity(self):
        return self._info['identity']

    @property
    def branch(self):
        return self._info['branch']

    @property
    def semver_tag(self):
        return self._info['semver_tag']

    @property
    def head_rev(self):
        return self._info['head_rev']

    def _run_git_cmd(self, args) -> str:
        result = ''
        try:
            output = subprocess.check_output(
                [self._os_git_cmd] + args, stderr=subprocess.DEVNULL)
            result = output.decode(sys.stdout.encoding).strip()
        except Exception:
            pass
        finally:
            return result

    def _get_identity(self) -> str:
        return self._run_git_cmd(["describe", "--tags", "--dirty", "--long"])

    def _get_branch(self) -> str:
        return self._run_git_cmd(["rev-parse", "--abbrev-ref", "HEAD"])

    def _get_semver_tag(self) -> VersionInfo:
        latest = VersionInfo.parse("0.0.0")

        try:
            # Check if a tag has been checked out directly
            current_tag_cmd = ["describe", "--tags", "--exact-match", "--match",  self._tag_prefix + "*"]
            current_tag = self._run_git_cmd(current_tag_cmd)

            if current_tag != '':
                tag = current_tag.split(self._tag_prefix)[1]
                return VersionInfo.parse(tag)

            # Get all tags before the current commit, sorted descending by refname (most recent first)
            tags_cmd = ["tag", "--list", self._tag_prefix +
                        "*", "--sort=-v:refname", "--no-contains"]
            tags = self._run_git_cmd(tags_cmd).split('\n')

            for tag in tags:
                tag = tag.split(self._tag_prefix)[1]
                ver = VersionInfo.parse(tag)
                latest = ver if ver > latest else latest
        except:
            # Ignore errors and return a version of 0.0.0
            pass

        return latest
