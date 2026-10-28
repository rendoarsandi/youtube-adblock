"""Restore flags for added sparse entries alongside original resource provenance."""
import struct
from tools import fix_resource_specs as specs

def collect_added_entries(data):
    """Derive added-entry config flags from ResTable_type, keyed by actual ID.

    Layout and CONFIG_* values follow AOSP ResTable_config::diff:
    https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/androidfw/ResourceTypes.cpp
    Existing original type-spec/public flags remain in specs.SPECS.
    """
    for package_pos, kind, header, size in specs.chunks(data, struct.unpack_from("<H", data, 2)[0], len(data)):
        if kind != 0x200:
            continue
        package = struct.unpack_from("<I", data, package_pos + 8)[0]
        for pos, kind, header, size in specs.chunks(data, package_pos + header, package_pos + size):
            if kind != 0x201:
                continue
            type_id, encoding = data[pos + 8:pos + 10]
            count = struct.unpack_from("<I", data, pos + 12)[0]
            config = data[pos + 20:pos + header].ljust(64, b"\0")
            mask = 0
            for offset, length, bits in [(4, 2, 1), (6, 2, 2), (8, 4, 4), (12, 1, 0x80),
                    (13, 1, 8), (14, 2, 0x100), (16, 1, 0x10), (17, 1, 0x40),
                    (20, 4, 0x200), (24, 4, 0x400), (29, 1, 0x1000), (30, 2, 0x2000),
                    (32, 4, 0x200), (36, 12, 4), (53, 8, 4)]:
                if any(config[offset:offset + length]):
                    mask |= bits
            for offset, field_mask, bits in [(18, 15, 0x20), (28, 0xC0, 0x4000),
                    (28, 0x3F, 0x800), (48, 3, 0x8000), (49, 15, 0x10000)]:
                if config[offset] & field_mask:
                    mask |= bits
            if any(config[61:]):
                raise ValueError("Unsupported extended resource configuration")
            if encoding & 1:
                ids = [struct.unpack_from("<H", data, pos + header + i * 4)[0] for i in range(count)]
            elif encoding & 2:
                ids = [i for i in range(count) if struct.unpack_from("<H", data, pos + header + i * 2)[0] != 0xFFFF]
            else:
                ids = [i for i in range(count) if struct.unpack_from("<I", data, pos + header + i * 4)[0] != 0xFFFFFFFF]
            flags = specs.SPECS[(package, type_id)]
            if ids:
                flags.extend([0] * max(0, max(ids) + 1 - len(flags)))
                for entry_id in ids:
                    flags[entry_id] |= mask
