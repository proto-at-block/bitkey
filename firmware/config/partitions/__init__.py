import os
from pipes import Template
import yaml
import jinja2


class LinkerGenerator:
    MODULE_DIR = os.path.dirname(__file__)
    CONFIG_FILENAME = "partitions.yml"
    TEMPLATE_FILENAME = "memory.jinja.ld"
    _config_dir = None
    _config_file = None
    _partitions = None
    _target = None
    _bootloader = None

    def __init__(self, target: str, partitions: str, bootloader: bool) -> None:
        self._target = target
        self._partitions = partitions
        self._bootloader = bootloader

        self._config_dir = os.path.join(
            self.MODULE_DIR, self._partitions)
        self._config_file = os.path.join(
            self._config_dir, self.CONFIG_FILENAME)

    def _load_config(self):
        with open(self._config_file) as f:
            return yaml.load(f, Loader=yaml.SafeLoader)

    def _get_template(self) -> jinja2.Template:
        template_loader = jinja2.FileSystemLoader(searchpath=self._config_dir)
        template_env = jinja2.Environment(loader=template_loader)
        template_env.trim_blocks = True
        return template_env.get_template(self.TEMPLATE_FILENAME)

    def _parse_size(self, s) -> int:
        if isinstance(s, int):
            return s
        size = 0
        num_map = {'K': 1024, 'M': 1024*1000}
        if s.isdigit():
            size = int(str)
        else:
            if len(s) > 1:
                size = float(s[:-1]) * \
                    num_map.get(s[-1].upper(), 1)
        return int(size)

    def _check_memory_usage(self, name, partitions, actual_size):
        total_used = 0
        for p in partitions:
            total_used += p['size']
        if total_used > actual_size:
            print(f"{name} overflowed by {total_used - actual_size} bytes")
            exit(1)

    def generate(self, output) -> None:
        cfg = self._load_config()

        # Convert sizes to machine readable formats
        for p in cfg['flash']['partitions']:
            if 'sections' in p:
                for s in p['sections']:
                    if 'size' in s:
                        s['size'] = self._parse_size(s['size'])
            if 'size' in p:
                p['size'] = self._parse_size(p['size'])

        flash_size = self._parse_size(cfg['flash']['size'])

        self._check_memory_usage(
            "Flash", cfg['flash']['partitions'], flash_size)

        def get_partition_free_space(p) -> int:
            size = p['size']
            for s in p['sections']:
                if 'size' in s:
                    size -= s['size']
            return size

        program_section = ''
        memories = []
        libraries = []
        origin_cursor = cfg['flash']['origin']
        for p in cfg['flash']['partitions']:
            if 'sections' in p:
                for s in p['sections']:
                    name = f"{p['name']}_{s['name']}"
                    if 'target' in s and s['target'] in self._target:
                        program_section = f"FLASH_{str(name).upper()}"
                    if 'size' in s:
                        size = s['size']
                    else:
                        size = get_partition_free_space(p)
                    m = self.Memory(
                        name, s['permissions'], origin_cursor, size)
                    memories.append(m)
                    origin_cursor += size
            else:
                m = self.Memory(
                    p['name'], p['permissions'], origin_cursor, p['size'])
                memories.append(m)
                origin_cursor += p['size']

                if 'library' in p:
                    libraries.append(self.LibrarySection(
                        p['name'], p['library']))

        ram_size = self._parse_size(cfg['ram']['size'])

        self._check_memory_usage(
            'RAM', cfg['ram']['partitions'], ram_size)

        origin_cursor = cfg['ram']['origin']
        for p in cfg['ram']['partitions']:
            m = self.Memory(
                p['name'], p['permissions'], origin_cursor, p['size'])
            memories.append(m)
            origin_cursor += p['size']

        # Handle SRAM4 if present (for STM32U5)
        if 'sram4' in cfg:
            sram4_size = self._parse_size(cfg['sram4']['size'])
            
            # Parse SRAM4 partition sizes
            for p in cfg['sram4']['partitions']:
                if 'size' in p:
                    p['size'] = self._parse_size(p['size'])
            
            self._check_memory_usage(
                'SRAM4', cfg['sram4']['partitions'], sram4_size)
            
            origin_cursor = cfg['sram4']['origin']
            for p in cfg['sram4']['partitions']:
                m = self.Memory(
                    p['name'], p['permissions'], origin_cursor, p['size'])
                memories.append(m)
                origin_cursor += p['size']

        slot = ''
        if 'APP' in program_section:
            slot = program_section.split('APPLICATION_')[1][0].lower()

        # Load and render the templates
        template = self._get_template()
        output_text = template.render({
            'memory': memories,
            'bootloader': self._bootloader,
            'baseDir': self._config_dir + os.sep,
            'program_section': program_section,
            'slot': slot,
            'libraries': libraries,
        })

        with open(output, "w") as f:
            f.write(output_text)
            f.close()

    class LibrarySection:
        name = None
        library = None

        def __init__(self, name, library, pad=False) -> None:
            self.name = name
            self.library = library

        def __str__(self) -> str:
            section_name = str(self.name).lower()
            memory_name = f"FLASH_{str(self.name).upper()}"
            s = [
                f"    .{section_name}_section : ALIGN(4)",
                f"    {{",
                f"        FILL(0xdd)",
                f"        __{section_name}_section_start__ = .;",
                f"        KEEP(*{self.library}:*(.text*))",
                f"        __{section_name}_section_end__ = .;",
                f"    }} > {memory_name}",
            ]
            return '\n'.join(s)

    class Memory:
        MAX_NAME_LEN = 38  # Must be manually tweaked based on the longest expected name
        name = None
        permissions = None
        origin = None
        size = None

        def __init__(self, name: str, permissions: str, origin: int, size: int) -> None:
            self.name = name
            self.permissions = permissions
            self.origin = origin
            self.size = size

        def _stringify_size(self, size: int) -> str:
            power = 1024
            power_labels = {0: '', 1: 'K', 2: 'M'}
            n = 1
            for _, _ in power_labels.items():
                if size >= pow(power, n):
                    if size % pow(power, n) == 0:
                        return f"{int(size / pow(power, n))}{power_labels[n]}"
                n += 1
            return f"{int(size)}"

        def __str__(self) -> str:
            prefix = ''
            if not self.name.startswith('ram') and not self.name.startswith('sram'):
                prefix = 'FLASH_'
            s = [
                f"{prefix}{str(self.name).upper().ljust(self.MAX_NAME_LEN - len(prefix))}",
                f"({self.permissions})".rjust(5),
                f":",
                f"ORIGIN = {self.origin:#010x},",
                f"LENGTH = {self._stringify_size(self.size)}"
            ]
            return ' '.join(s)
