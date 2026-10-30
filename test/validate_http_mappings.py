#!/usr/bin/env python3
"""
Validate the fixture-bound HTTP reference mappings without Android/Gradle tools.

    python3 test/validate_http_mappings.py
    python3 test/validate_http_mappings.py --reference-dir path/to/maven-jars
    python3 test/validate_http_mappings.py --payload-jars okhttp.jar okio.jar

The optional directory contains the exact reference JARs named in each mapping.
DEX declarations, incoming references, signatures, annotations, and overrides are
checked. This does not execute an APK, prove semantic equivalence, or inspect
reflection, dynamically loaded code, assets/audience_network.dex, or native code.
Alternate-payload checks conservatively include override-only API declarations;
a missing callback never invoked by that payload need not cause a linker error.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import struct
import sys
import zipfile
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
RESOURCE_ROOT = ROOT / "patches/src/main/resources"
TYPES = re.compile(r"\[*L[^;() ]+;|\[*[VZBCSIJFD]")
REFERENCE_TYPES = re.compile(r"L[^;():<> ]+;")
PRIMITIVES = dict(zip(
    ("void", "boolean", "byte", "char", "short", "int", "long", "float", "double"),
    "VZBCSIJFD",
))


def java_type(value: str) -> str:
    dimensions = 0
    while value.endswith("[]"):
        value = value[:-2]
        dimensions += 1
    return "[" * dimensions + PRIMITIVES.get(value, "L" + value.replace(".", "/") + ";")


def method_types(prototype: str) -> list[str]:
    return TYPES.findall(prototype)


def symbol(key: tuple[str, str, str]) -> str:
    owner, name, prototype = key
    return owner + "->" + name + (prototype if prototype.startswith("(") else ":" + prototype)


@dataclass
class Definition:
    type: str
    flags: int
    parents: tuple[str, ...]
    fields: dict[tuple[str, str, str], int] = field(default_factory=dict)
    methods: dict[tuple[str, str, str], int] = field(default_factory=dict)
    references: list[tuple[str, object]] = field(default_factory=list)


class Dex:
    def __init__(self, data: bytes):
        if not data.startswith(b"dex\n"):
            raise ValueError("Not a standard DEX file")
        self.data = data
        self.strings = []
        count, start = self.unpack("II", 56)
        for i in range(count):
            position = self.u32(start + 4 * i)
            _, position = self.uleb(position)
            end = data.index(0, position)
            self.strings.append(data[position:end].decode("utf8", "replace"))
        count, start = self.unpack("II", 64)
        self.types = [self.strings[self.u32(start + 4 * i)] for i in range(count)]
        count, start = self.unpack("II", 72)
        self.prototypes = []
        for i in range(count):
            _, ret, parameters = self.unpack("III", start + i * 12)
            self.prototypes.append("(" + "".join(self.type_list(parameters)) + ")" + self.types[ret])
        count, start = self.unpack("II", 80)
        self.fields = []
        for i in range(count):
            owner, typ, name = self.unpack("HHI", start + i * 8)
            self.fields.append((self.types[owner], self.strings[name], self.types[typ]))
        count, start = self.unpack("II", 88)
        self.methods = []
        for i in range(count):
            owner, prototype, name = self.unpack("HHI", start + i * 8)
            self.methods.append((self.types[owner], self.strings[name], self.prototypes[prototype]))
        self.sections = {}
        position = self.u32(52)
        for i in range(self.u32(position)):
            typ, _, count, offset = self.unpack("HHII", position + 4 + 12 * i)
            self.sections[typ] = (count, offset)

    def unpack(self, fmt, position):
        return struct.unpack_from("<" + fmt, self.data, position)

    def u32(self, position):
        return self.unpack("I", position)[0]

    def uleb(self, position, signed=False):
        value = shift = 0
        while True:
            byte = self.data[position]
            position += 1
            value |= (byte & 127) << shift
            shift += 7
            if not byte & 128:
                if signed and byte & 64:
                    value -= 1 << shift
                return value, position

    def type_list(self, position):
        if not position:
            return ()
        return tuple(self.types[self.unpack("H", position + 4 + 2 * i)[0]]
                     for i in range(self.u32(position)))

    def annotation(self, position, references):
        typ, position = self.uleb(position)
        references.append(("type", self.types[typ]))
        count, position = self.uleb(position)
        for _ in range(count):
            _, position = self.uleb(position)
            position = self.value(position, references)
        return position

    def value(self, position, references):
        tag = self.data[position]
        position += 1
        kind, width = tag & 31, (tag >> 5) + 1
        if kind == 0x1C:
            count, position = self.uleb(position)
            for _ in range(count):
                position = self.value(position, references)
            return position
        if kind == 0x1D:
            return self.annotation(position, references)
        if kind in (0x1E, 0x1F):
            return position
        index = int.from_bytes(self.data[position:position + width], "little")
        if kind == 0x17:
            references.extend(("type", t) for t in REFERENCE_TYPES.findall(self.strings[index]))
        elif kind == 0x18:
            references.append(("type", self.types[index]))
        elif kind in (0x19, 0x1B):
            references.append(("field", self.fields[index]))
        elif kind == 0x1A:
            references.append(("method", self.methods[index]))
        elif kind == 0x15:
            references.extend(("type", t) for t in method_types(self.prototypes[index]))
        elif kind == 0x16:
            self.method_handle(index, references)
        return position + width

    def method_handle(self, index, references):
        _, start = self.sections[0x0008]
        kind, _, member, _ = self.unpack("HHHH", start + index * 8)
        references.append(("field", self.fields[member]) if kind <= 3 else ("method", self.methods[member]))

    def annotation_set(self, position, references):
        if position:
            for i in range(self.u32(position)):
                self.annotation(self.u32(position + 4 + i * 4) + 1, references)

    def annotations(self, position, references):
        if not position:
            return
        annotations, fields, methods, parameters = self.unpack("IIII", position)
        self.annotation_set(annotations, references)
        position += 16
        for _ in range(fields + methods):
            self.annotation_set(self.u32(position + 4), references)
            position += 8
        for _ in range(parameters):
            annotation_list = self.u32(position + 4)
            for i in range(self.u32(annotation_list)):
                self.annotation_set(self.u32(annotation_list + 4 + i * 4), references)
            position += 8

    def instructions(self, position, references):
        if not position:
            return
        tries = self.unpack("H", position + 6)[0]
        size = self.u32(position + 12)
        start = position + 16
        words = self.unpack("H" * size, start)
        cursor = 0
        while cursor < size:
            word = words[cursor]
            opcode = word & 255
            width = 1
            if opcode == 0 and word:
                payload = word >> 8
                if payload == 1:
                    width = 4 + 2 * words[cursor + 1]
                elif payload == 2:
                    width = 2 + 4 * words[cursor + 1]
                elif payload == 3:
                    length = words[cursor + 2] | words[cursor + 3] << 16
                    width = 4 + (words[cursor + 1] * length + 1) // 2
                else:
                    raise ValueError(f"Unknown DEX payload 0x{word:04x}")
            elif opcode in (3, 6, 9, 0x14, 0x17, 0x1B, 0x24, 0x25, 0x26, 0x2A, 0x2B, 0x2C, 0xFC, 0xFD):
                width = 3
            elif opcode in (0xFA, 0xFB):
                width = 4
            elif opcode == 0x18:
                width = 5
            elif opcode in (2, 5, 8, 0x13, 0x15, 0x16, 0x19, 0x1A, 0x1C, 0x1F, 0x20, 0x22, 0x23, 0x29, 0xFE, 0xFF):
                width = 2
            elif 0x2D <= opcode <= 0x3D or 0x44 <= opcode <= 0x6D or 0x90 <= opcode <= 0xAF or 0xD0 <= opcode <= 0xE2:
                width = 2
            elif 0x6E <= opcode <= 0x72 or 0x74 <= opcode <= 0x78:
                width = 3
            if opcode in (0x1C, 0x1F, 0x20, 0x22, 0x23, 0x24, 0x25):
                references.append(("type", self.types[words[cursor + 1]]))
            elif 0x52 <= opcode <= 0x6D:
                references.append(("field", self.fields[words[cursor + 1]]))
            elif 0x6E <= opcode <= 0x72 or 0x74 <= opcode <= 0x78 or opcode in (0xFA, 0xFB):
                references.append(("method", self.methods[words[cursor + 1]]))
                if opcode in (0xFA, 0xFB):
                    references.extend(("type", t) for t in method_types(self.prototypes[words[cursor + 3]]))
            elif opcode == 0xFE:
                self.method_handle(words[cursor + 1], references)
            elif opcode == 0xFF:
                references.extend(("type", t) for t in method_types(self.prototypes[words[cursor + 1]]))
            elif opcode in (0xFC, 0xFD):
                _, table = self.sections[0x0007]
                array = self.u32(table + words[cursor + 1] * 4)
                count, array = self.uleb(array)
                for _ in range(count):
                    array = self.value(array, references)
            cursor += width
        if cursor != size:
            raise ValueError("DEX instruction alignment failed")
        if tries:
            handlers = start + size * 2 + (size % 2) * 2 + tries * 8
            count, handlers = self.uleb(handlers)
            for _ in range(count):
                size, handlers = self.uleb(handlers, signed=True)
                for _ in range(abs(size)):
                    typ, handlers = self.uleb(handlers)
                    references.append(("type", self.types[typ]))
                    _, handlers = self.uleb(handlers)
                if size <= 0:
                    _, handlers = self.uleb(handlers)

    def definitions(self):
        count, start = self.unpack("II", 96)
        for i in range(count):
            typ, flags, parent, interfaces, _, annotations, members, values = self.unpack("IIIIIIII", start + i * 32)
            parents = (() if parent == 0xFFFFFFFF else (self.types[parent],)) + self.type_list(interfaces)
            definition = Definition(self.types[typ], flags, parents)
            definition.references.extend(("type", t) for t in parents)
            self.annotations(annotations, definition.references)
            if values:
                size, values = self.uleb(values)
                for _ in range(size):
                    values = self.value(values, definition.references)
            if members:
                counts = []
                for _ in range(4):
                    size, members = self.uleb(members)
                    counts.append(size)
                for section, size in enumerate(counts):
                    index = 0
                    for _ in range(size):
                        difference, members = self.uleb(members)
                        index += difference
                        access, members = self.uleb(members)
                        if section < 2:
                            key = self.fields[index]
                            definition.fields[key] = access
                            definition.references.append(("type", key[2]))
                        else:
                            key = self.methods[index]
                            definition.methods[key] = access
                            definition.references.extend(("type", t) for t in method_types(key[2]))
                            code, members = self.uleb(members)
                            self.instructions(code, definition.references)
            yield definition


def apk_definitions(path):
    definitions = {}
    with zipfile.ZipFile(path) as apk:
        for name in apk.namelist():
            if re.fullmatch(r"classes\d*\.dex", name):
                for definition in Dex(apk.read(name)).definitions():
                    if definition.type in definitions:
                        raise ValueError("Duplicate APK class " + definition.type)
                    definitions[definition.type] = definition
    return definitions


def jar_definitions(paths):
    definitions = {}
    for path in paths:
        with zipfile.ZipFile(path) as jar:
            for name in jar.namelist():
                if not name.endswith(".class") or name.startswith("META-INF/"):
                    continue
                data = jar.read(name)
                if data[:4] != b"\xca\xfe\xba\xbe":
                    raise ValueError("Invalid class file " + name)
                position = 10
                constant_pool = [None]
                count = struct.unpack_from(">H", data, 8)[0]
                while len(constant_pool) < count:
                    tag = data[position]
                    position += 1
                    if tag == 1:
                        size = struct.unpack_from(">H", data, position)[0]
                        position += 2
                        constant_pool.append(data[position:position + size].decode("utf8", "replace"))
                        position += size
                    elif tag in (7, 8, 16, 19, 20):
                        constant_pool.append(struct.unpack_from(">H", data, position)[0])
                        position += 2
                    elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                        constant_pool.append(None)
                        position += 4
                    elif tag in (5, 6):
                        constant_pool.extend((None, None))
                        position += 8
                    elif tag == 15:
                        constant_pool.append(None)
                        position += 3
                    else:
                        raise ValueError(f"Unknown class constant tag {tag}")
                def u16():
                    nonlocal position
                    value = struct.unpack_from(">H", data, position)[0]
                    position += 2
                    return value
                def class_type(index):
                    return "L" + constant_pool[constant_pool[index]] + ";" if index else None
                flags, owner, parent = u16(), u16(), u16()
                parents = (() if not parent else (class_type(parent),)) + tuple(class_type(u16()) for _ in range(u16()))
                definition = Definition(class_type(owner), flags, parents)
                for table in (definition.fields, definition.methods):
                    for _ in range(u16()):
                        flags, name, prototype = u16(), u16(), u16()
                        table[(definition.type, constant_pool[name], constant_pool[prototype])] = flags
                        for _ in range(u16()):
                            position += 2
                            size = struct.unpack_from(">I", data, position)[0]
                            position += 4 + size
                definitions[definition.type] = definition
    return definitions


class Mapping:
    def __init__(self, path):
        self.path = path
        self.metadata = {}
        self.classes = {}
        members = []
        current = None
        for number, line in enumerate(path.read_text().splitlines(), 1):
            if not line.strip():
                continue
            if line.startswith("#"):
                key, separator, value = line[1:].strip().partition(":")
                if separator:
                    self.metadata.setdefault(key, []).append(value.strip())
                continue
            if not line[0].isspace():
                match = re.fullmatch(r"(\S+) -> (\S+):", line)
                if not match:
                    raise ValueError(f"{path}:{number}: invalid class mapping")
                canonical, observed = map(java_type, match.groups())
                if observed in self.classes or canonical in self.classes.values():
                    raise ValueError(f"{path}:{number}: duplicate/merged class mapping")
                self.classes[observed] = canonical
                current = observed, canonical
            else:
                if current is None:
                    raise ValueError(f"{path}:{number}: orphan member")
                members.append((current, line.strip(), number))
        reverse = {canonical: observed for observed, canonical in self.classes.items()}
        def observed_type(value):
            return REFERENCE_TYPES.sub(lambda match: reverse.get(match[0], match[0]), value)
        self.methods = {}
        self.fields = {}
        for (observed, canonical), line, number in members:
            declaration, separator, actual_name = line.partition(" -> ")
            if not separator:
                raise ValueError(f"{path}:{number}: invalid member mapping")
            match = re.fullmatch(r"(\S+) ([^ (]+)\(([^)]*)\)", declaration)
            if match:
                ret, name, parameters = match.groups()
                prototype = "(" + "".join(java_type(p.strip()) for p in parameters.split(",") if p.strip()) + ")" + java_type(ret)
                if name.startswith("<") or actual_name.startswith("<"):
                    if name != actual_name or name not in ("<init>", "<clinit>") or ret != "void":
                        raise ValueError(f"{path}:{number}: invalid constructor mapping")
                key = observed, actual_name, observed_type(prototype)
                target = canonical, name, prototype
                table = self.methods
            else:
                ret, name = declaration.split()
                typ = java_type(ret)
                key, target = (observed, actual_name, observed_type(typ)), (canonical, name, typ)
                table = self.fields
            if key in table and table[key] != target:
                raise ValueError(f"{path}:{number}: ambiguous member mapping")
            table[key] = target

    def native(self, typ):
        if typ in self.metadata.get("caller-class", ()) or any(
            typ.startswith(prefix) for prefix in self.metadata.get("caller-prefix", ())
        ):
            return False
        return typ in self.metadata.get("native-helper", ()) or any(
            typ.startswith(prefix) for prefix in self.metadata.get("native-prefix", ())
        )

    def type(self, typ):
        return REFERENCE_TYPES.sub(lambda match: self.classes.get(match[0], match[0]), typ)


def ancestors(owner, definitions, seen=None):
    seen = set() if seen is None else seen
    if owner in seen:
        return
    seen.add(owner)
    yield owner
    definition = definitions.get(owner)
    if definition:
        for parent in definition.parents:
            yield from ancestors(parent, definitions, seen)


def resolve(key, definitions, field=False):
    owner, name, prototype = key
    for parent in ancestors(owner, definitions):
        definition = definitions.get(parent)
        candidate = parent, name, prototype
        if definition and candidate in (definition.fields if field else definition.methods):
            return candidate
    return None


def validate(mapping, apk, references=None):
    definitions = apk_definitions(apk)
    problems = set()
    counts = Counter()
    for typ, canonical in mapping.classes.items():
        if typ not in definitions:
            problems.add("Missing observed class " + typ)
        if references is not None and canonical not in references:
            problems.add("Missing canonical class " + canonical)
        elif references is not None and typ in definitions:
            if bool(definitions[typ].flags & 0x200) != bool(references[canonical].flags & 0x200):
                problems.add("Class/interface mismatch " + typ)
    for is_field, table in ((False, mapping.methods), (True, mapping.fields)):
        for source, target in table.items():
            resolved = resolve(source, definitions, is_field)
            if resolved is None:
                problems.add("Missing observed member " + symbol(source))
            if references is not None:
                canonical = resolve(target, references, is_field)
                if canonical is None:
                    problems.add("Missing canonical member " + symbol(target))
                elif resolved:
                    source_table = definitions[resolved[0]].fields if is_field else definitions[resolved[0]].methods
                    target_table = references[canonical[0]].fields if is_field else references[canonical[0]].methods
                    if bool(source_table[resolved] & 8) != bool(target_table[canonical] & 8):
                        problems.add("Static/instance mismatch " + symbol(source))
    def check_type(typ):
        for element in REFERENCE_TYPES.findall(typ):
            if mapping.native(element) and element not in mapping.classes:
                problems.add("Unmapped incoming native type " + element)
    def check_member(kind, key):
        owner, name, prototype = key
        check_type(prototype)
        is_field = kind == "field"
        table = mapping.fields if is_field else mapping.methods
        mapped = [table[(parent, name, prototype)] for parent in ancestors(owner, definitions)
                  if (parent, name, prototype) in table]
        if mapped:
            counts[kind + " references"] += 1
            if not is_field and len({target[1] for target in mapped}) > 1:
                problems.add("Conflicting override mapping " + symbol(key))
            check_type(owner)
            return
        resolved = resolve(key, definitions, is_field)
        if mapping.native(owner) or resolved and mapping.native(resolved[0]):
            problems.add("Unmapped incoming native " + kind + " " + symbol(key))
    for definition in definitions.values():
        if mapping.native(definition.type):
            continue
        for kind, value in definition.references:
            if kind == "type":
                check_type(value)
            else:
                check_member(kind, value)
        for key, flags in definition.methods.items():
            if flags & (8 | 2) or key[1].startswith("<"):
                continue
            for parent in ancestors(definition.type, definitions):
                if not mapping.native(parent):
                    continue
                candidate = parent, key[1], key[2]
                inherited = definitions.get(parent)
                if inherited and candidate in inherited.methods and not inherited.methods[candidate] & (8 | 2):
                    check_member("method", candidate)
                    counts["overrides"] += 1
    return definitions, counts, sorted(problems)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apk-dir", type=Path, default=ROOT / "test/apks")
    parser.add_argument("--reference-dir", type=Path)
    parser.add_argument("--payload-jars", type=Path, nargs="+",
                        help="Also check the mapped API against an alternate complete payload")
    parser.add_argument("--clients", nargs="+")
    args = parser.parse_args(argv)
    by_hash = {}
    for path in args.apk_dir.glob("*.apk"):
        with path.open("rb") as stream:
            by_hash[hashlib.file_digest(stream, "sha256").hexdigest()] = path
    mappings = sorted(
        path for path in RESOURCE_ROOT.rglob("mapping.txt")
        if any(line.startswith("# native-prefix: ") for line in path.read_text().splitlines())
    )
    def profile_id(path):
        return "-".join(path.relative_to(RESOURCE_ROOT).parent.parts)
    if args.clients:
        mappings = [path for path in mappings if profile_id(path) in args.clients]
    if not mappings:
        parser.error("No mapping resources selected")
    failed = False
    payload = jar_definitions(args.payload_jars) if args.payload_jars else None
    for path in mappings:
        mapping = Mapping(path)
        digest, = mapping.metadata["APK SHA-256"]
        apk = by_hash.get(digest)
        if apk is None:
            print(f"FAIL {profile_id(path)}: fixture {digest} not found")
            failed = True
            continue
        reference = None
        if args.reference_dir:
            jars = []
            for artifact in mapping.metadata["Reference artifact"]:
                coordinate, expected = artifact.split(" SHA-256 ")
                _, name, version = coordinate.split(":")
                jar = args.reference_dir / f"{name}-{version}.jar"
                if not jar.exists() or hashlib.sha256(jar.read_bytes()).hexdigest() != expected:
                    raise ValueError("Missing or mismatched canonical reference JAR " + str(jar))
                jars.append(jar)
            reference = jar_definitions(jars)
        _, counts, problems = validate(mapping, apk, reference)
        print(f"{'FAIL' if problems else 'PASS'} {profile_id(path)}: "
              f"{len(mapping.classes)} classes, {len(mapping.methods)} methods, {len(mapping.fields)} fields; "
              f"{dict(counts)}; {len(problems)} gaps")
        for problem in problems:
            print("  " + problem)
        failed |= bool(problems)
        if payload is not None:
            _, _, payload_problems = validate(mapping, apk, payload)
            print(f"{'FAIL' if payload_problems else 'PASS'} {profile_id(path)} alternate payload: "
                  f"{len(payload_problems)} gaps")
            for problem in payload_problems:
                print("  " + problem)
            failed |= bool(payload_problems)
    if not args.reference_dir:
        print("Canonical JAR/API checks skipped; use --reference-dir with the pinned reference JARs.")
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
