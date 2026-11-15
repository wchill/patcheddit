#!/usr/bin/env python3
"""Transfer Boost's partial ProGuard map using normalized DEX fingerprints.

APKs are parsed, never installed or executed. See CONTRIBUTING.md for usage.
"""

from __future__ import annotations

import argparse
import collections
import functools
import gzip
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE_SCHEMA = 1
KINDS = ("classes", "methods", "fields")
TYPE = re.compile(r"L[^;():<> ]+;")
RESOURCE = re.compile(r"^\s*resource (0x[0-9a-f]+) (\S+)", re.MULTILINE)
PACKAGE = re.compile(r"^package: name='([^']+)' .*versionName='([^']*)'", re.MULTILINE)

sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / "test"))
from validate_http_mappings import Mapping, java_type, symbol


class MappingError(ValueError):
    pass


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def file_digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def split_member(value):
    owner, member = value.split("->", 1)
    separator = member.find("(") if "(" in member else member.find(":")
    if separator < 0:
        raise MappingError(f"Invalid DEX member: {value}")
    return owner, member[:separator], member[separator:]


def mapping_symbols(mapping):
    result = {
        "classes": {canonical: observed for observed, canonical in mapping.classes.items()},
        "methods": {symbol(canonical): symbol(observed) for observed, canonical in mapping.methods.items()},
        "fields": {symbol(canonical): symbol(observed) for observed, canonical in mapping.fields.items()},
    }
    for kind in KINDS:
        if len(result[kind]) != len(getattr(mapping, kind)):
            raise MappingError(f"{mapping.path}: conflicting canonical {kind} cannot be reversed")
    if any("." in split_member(value)[1] for value in result["methods"]):
        raise MappingError(f"{mapping.path}: inlined/qualified method mappings are unsupported")
    return result


def run_command(command):
    result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        raise MappingError(f"Command failed ({result.returncode}): {command[0]}\n{result.stdout.strip()}")
    return result.stdout


def find_aapt2(explicit=None):
    if explicit is not None:
        if not explicit.is_file() or not os.access(explicit, os.X_OK):
            raise MappingError(f"aapt2 is not executable: {explicit}")
        return explicit.resolve()
    executable = shutil.which("aapt2")
    if executable:
        return Path(executable)
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(variable)
        if not sdk:
            continue
        directory = Path(sdk) / "build-tools"
        if not directory.is_dir():
            continue
        versions = sorted(directory.iterdir(), key=lambda path: tuple(
            int(part) for part in re.findall(r"\d+", path.name)), reverse=True)
        for version in versions:
            candidate = version / ("aapt2.exe" if os.name == "nt" else "aapt2")
            if candidate.is_file() and os.access(candidate, os.X_OK):
                return candidate
    raise MappingError("aapt2 was not found; set ANDROID_HOME or pass --aapt2")


def extractor_digest():
    return digest({
        str(path.relative_to(ROOT)): file_digest(path) for path in (
            ROOT / "scripts/BoostDexFingerprintDump.java",
            ROOT / "gradle/libs.versions.toml",
        )
    })


class Inventory:
    def __init__(self, classes, metadata):
        self.metadata = metadata
        self.classes = {}
        self.methods = {}
        self.fields = {}
        self.resources = {int(key): value for key, value in metadata.get("resources", {}).items()}
        for cls in classes:
            if cls["type"] in self.classes:
                raise MappingError(f"Duplicate DEX class: {cls['type']}")
            self.classes[cls["type"]] = cls
            for kind in ("methods", "fields"):
                table = getattr(self, kind)
                for item in cls[kind]:
                    if item["id"] in table:
                        raise MappingError(f"Duplicate DEX member: {item['id']}")
                    table[item["id"]] = item
                    if kind == "methods" and "code" in item:
                        if len(item["offsets"]) != len(item["code"]) + 1:
                            raise MappingError(f"Incomplete instruction offsets: {item['id']}")
        if not self.classes:
            raise MappingError("Fingerprint inventory contains no classes")

    @classmethod
    def read(cls, path, expected):
        with gzip.open(path, "rt", encoding="utf-8") as source:
            header = source.readline()
            if not header:
                raise MappingError(f"Empty fingerprint cache: {path}")
            metadata = json.loads(header)
            if any(metadata.get(key) != value for key, value in expected.items()):
                raise MappingError(f"Fingerprint cache metadata does not match its APK/schema: {path}")
            return cls(map(json.loads, source), metadata)

    @classmethod
    def load(cls, apk, cache_dir, aapt2):
        checksum = file_digest(apk)
        expected = {
            "schema": CACHE_SCHEMA, "apk_sha256": checksum, "extractor_sha256": extractor_digest(),
        }
        cache_dir.mkdir(parents=True, exist_ok=True)
        cached = cache_dir / f"{checksum}-v{CACHE_SCHEMA}-{expected['extractor_sha256'][:12]}.jsonl.gz"
        if cached.exists():
            print(f"Using cached DEX inventory: {apk.name}", file=sys.stderr)
            return cls.read(cached, expected)
        print(f"Extracting DEX inventory: {apk.name}", file=sys.stderr)
        badging = run_command([str(aapt2), "dump", "badging", str(apk)])
        package = PACKAGE.search(badging)
        if package is None or package[1] != "com.rubenmayayo.reddit" or not package[2]:
            raise MappingError(f"Not a Boost for Reddit APK: {apk}")
        resources = {
            int(address, 16): name
            for address, name in RESOURCE.findall(run_command([str(aapt2), "dump", "resources", str(apk)]))
        }
        if not resources:
            raise MappingError(f"APK resource identities could not be extracted: {apk}")
        metadata = {**expected, "package": package[1], "version": package[2], "resources": resources}
        with tempfile.TemporaryDirectory(prefix="boost-dump-", dir=cache_dir) as temporary:
            temporary = Path(temporary)
            header = temporary / "metadata.json"
            header.write_text(json.dumps(metadata), encoding="utf-8")
            output = temporary / "inventory.jsonl.gz"
            wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
            run_command([
                str(wrapper), ":patches:dumpBoostDexFingerprints", "--console=plain", "--quiet",
                f"-PboostFingerprintApk={apk}",
                f"-PboostFingerprintOutput={output}",
                f"-PboostFingerprintMetadata={header}",
            ])
            inventory = cls.read(output, expected)
            if file_digest(apk) != checksum:
                raise MappingError(f"APK changed during fingerprint extraction: {apk}")
            os.replace(output, cached)
            return inventory


def verify_mapping(mapping, inventory):
    checksums = mapping.metadata.get("APK SHA-256", [])
    if checksums and checksums != [inventory.metadata["apk_sha256"]]:
        raise MappingError(f"Mapping APK checksum does not match: {mapping.path}")
    required = mapping_symbols(mapping)
    for kind in KINDS:
        missing = set(required[kind].values()) - getattr(inventory, kind).keys()
        if missing:
            raise MappingError(f"{mapping.path}: missing {kind} in APK: {', '.join(sorted(missing)[:8])}")
    return required


class Canonical:
    def __init__(self, inventory, classes, methods, fields, side):
        self.inventory = inventory
        self.side = side
        self.classes = dict(classes) if side == "old" else {target: source for source, target in classes.items()}
        self.members = {
            "method": dict(methods) if side == "old" else {target: source for source, target in methods.items()},
            "field": dict(fields) if side == "old" else {target: source for source, target in fields.items()},
        }

    @staticmethod
    def clear_caches():
        for name in ("types", "reference_base", "code", "method_key", "class_key", "features"):
            getattr(Canonical, name).cache_clear()

    @functools.lru_cache(maxsize=None)
    def types(self, text, owner=""):
        def replace(match):
            typ = match[0]
            if typ == owner:
                return "LSELF;"
            if typ in self.classes:
                return "LBOUND:" + (typ if self.side == "old" else self.classes[typ]) + ";"
            return "LUNKNOWN;" if typ in self.inventory.classes else typ
        return TYPE.sub(replace, text or "")

    def prototype(self, method, owner=""):
        return self.types("(" + "".join(method["parameters"]) + ")" + method["returns"], owner)

    def literal(self, value):
        return ("resource", self.inventory.resources[value]) if value in self.inventory.resources else value

    @functools.lru_cache(maxsize=None)
    def reference_base(self, kind, address, owner):
        if kind in ("method", "field"):
            defining, name, signature = split_member(address)
            if defining not in self.inventory.classes:
                return (kind, self.types(defining, owner), name, self.types(signature, owner)), False
            special = name if name in ("<init>", "<clinit>") else "?"
            base = kind, self.types(defining, owner), special, self.types(signature, owner)
            if address in self.members[kind]:
                identity = address if self.side == "old" else self.members[kind][address]
                return (*base, "BOUND:" + identity), False
            return base, True
        if kind in ("type", "proto"):
            return (kind, self.types(address, owner)), False
        if kind == "string":
            return (kind, address), False
        raise MappingError(f"Unsupported fingerprint reference kind: {kind}")

    @functools.lru_cache(maxsize=None)
    def code(self, method_id):
        method = self.inventory.methods[method_id]
        if "code" not in method:
            return None
        owner = split_member(method_id)[0]
        offsets = method["offsets"]
        positions = {address: position for position, address in enumerate(offsets)}

        def position(address):
            if address not in positions:
                raise MappingError(f"{method_id}: target {address} is not an instruction boundary")
            return positions[address]

        switches = collections.defaultdict(list)
        for index, instruction in enumerate(method["code"]):
            if instruction["op"] in ("PACKED_SWITCH", "SPARSE_SWITCH"):
                switches[offsets[index] + instruction["offset"]].append(index)
        incoming = (0 if method["flags"] & 8 else 1) + sum(
            2 if typ in ("J", "D") else 1 for typ in method["parameters"])
        parameter_start = method["registers"] - incoming
        if parameter_start < 0:
            raise MappingError(f"Insufficient parameter registers: {method_id}")
        registers = {parameter_start + index: "p" + str(index) for index in range(incoming)}
        locals_count = 0
        identities = collections.defaultdict(dict)

        def register(number):
            nonlocal locals_count
            if not 0 <= number < method["registers"]:
                raise MappingError(f"{method_id}: invalid register v{number}")
            if number not in registers:
                registers[number] = "v" + str(locals_count)
                locals_count += 1
            return registers[number]

        def reference(value):
            kind, address = value
            base, anonymous = self.reference_base(kind, address, owner)
            if anonymous:
                entries = identities[base]
                if address not in entries:
                    entries[address] = len(entries)
                return (*base, entries[address])
            return base

        result = []
        for index, instruction in enumerate(method["code"]):
            op = instruction["op"]
            regs = instruction.get("regs", [])
            if "range" in instruction:
                first, count = instruction["range"]
                regs = list(range(first, first + count))
            if op.endswith("_2ADDR"):
                op = op.removesuffix("_2ADDR")
                regs = [regs[0], regs[0], regs[1]]
            if op in ("CONST_4", "CONST_16", "CONST", "CONST_HIGH16"):
                op = "CONST"
            elif op in ("CONST_WIDE_16", "CONST_WIDE_32", "CONST_WIDE", "CONST_WIDE_HIGH16"):
                op = "CONST_WIDE"
            elif op in ("GOTO", "GOTO_16", "GOTO_32"):
                op = "GOTO"
            elif op.startswith("MOVE"):
                op = re.sub(r"_(FROM16|16)$", "", op)
            elif op.startswith(("INVOKE_", "FILLED_NEW_ARRAY")):
                op = op.removesuffix("_RANGE")
            elif op == "CONST_STRING_JUMBO":
                op = "CONST_STRING"
            op = re.sub(r"_LIT(8|16)$", "_LIT", op)
            token = {"op": op}
            if regs:
                token["regs"] = tuple(map(register, regs))
            if "literal" in instruction:
                token["literal"] = self.literal(instruction["literal"])
            for key in ("ref", "ref2"):
                if key in instruction:
                    token[key] = reference(instruction[key])
            if "offset" in instruction:
                token["target"] = position(offsets[index] + instruction["offset"])
            if "switch" in instruction:
                if offsets[index] not in switches:
                    raise MappingError(f"Unreferenced switch payload: {method_id}")
                branches = [
                    (origin, [(self.literal(key), position(offsets[origin] + offset))
                              for key, offset in instruction["switch"]])
                    for origin in switches[offsets[index]]
                ]
                if len(branches) == 1:
                    token["switch"] = branches[0][1]
                else:
                    token["switches"] = branches
            if "array" in instruction:
                token["array"] = [self.literal(value) for value in instruction["array"]]
                token["width"] = instruction["width"]
            result.append(token)
        tries = [
            (position(start), position(end), [
                (self.types(handler.get("type"), owner), position(handler["address"]))
                for handler in handlers
            ]) for start, end, handlers in method["tries"]
        ]
        return result, tries

    @functools.lru_cache(maxsize=None)
    def method_key(self, method_id, body=True):
        method = self.inventory.methods[method_id]
        owner = split_member(method_id)[0]
        kind = method["name"] if method["name"] in ("<init>", "<clinit>") else "?"
        return (method["flags"], kind, self.prototype(method, owner),
                digest(self.code(method_id)) if body and "code" in method else None)

    @functools.lru_cache(maxsize=None)
    def class_key(self, typ, body=True):
        cls = self.inventory.classes[typ]
        return (
            cls["flags"], self.types(cls.get("parent"), typ),
            tuple(sorted(self.types(value, typ) for value in cls["interfaces"])),
            tuple(sorted((field["flags"], self.types(field["type"], typ)) for field in cls["fields"])),
            tuple(sorted((self.method_key(method["id"], body) for method in cls["methods"]), key=repr)),
        )

    @functools.lru_cache(maxsize=None)
    def features(self, method_id):
        code = self.code(method_id)
        if code is None:
            return collections.Counter()
        instructions, tries = code
        tokens = [digest({key: value for key, value in insn.items() if key not in ("regs", "target", "switch", "switches")})
                  for insn in instructions]
        width = min(3, len(tokens))
        features = collections.Counter(
            ("ngram", tuple(tokens[index:index + width])) for index in range(len(tokens) - width + 1)
        ) if width else collections.Counter()
        leaders = {0, len(tokens)}
        for index, insn in enumerate(instructions):
            if "target" in insn:
                leaders.update((insn["target"], index + 1))
            if "switch" in insn:
                leaders.update(target for _, target in insn["switch"])
            if "switches" in insn:
                leaders.update(target for _, branches in insn["switches"] for _, target in branches)
            if insn["op"].startswith(("RETURN", "THROW")):
                leaders.add(index + 1)
        for start, end, handlers in tries:
            leaders.update((start, end))
            leaders.update(target for _, target in handlers)
        boundaries = sorted(leaders)
        features.update(("block", tuple(tokens[start:end])) for start, end in zip(boundaries, boundaries[1:]) if start < end)
        return features


def unique_pairs(before, after, old_key, new_key):
    left, right = collections.defaultdict(list), collections.defaultdict(list)
    for value in sorted(before):
        left[old_key(value)].append(value)
    for value in sorted(after):
        right[new_key(value)].append(value)
    return [(left[key][0], right[key][0]) for key in sorted(left.keys() & right.keys(), key=repr)
            if len(left[key]) == len(right[key]) == 1]


def similarity(before, after):
    union = sum((before | after).values())
    return sum((before & after).values()) / union if union else 0.0


class Matcher:
    def __init__(self, old, new, known, source_metadata=True):
        self.old, self.new, self.known, self.source_metadata = old, new, known, source_metadata
        self.tables = {kind: {} for kind in KINDS}
        self.reverse = {kind: {} for kind in KINDS}
        self.reasons = {kind: {} for kind in KINDS}
        self.conflicts = []

    def bind(self, kind, before, after, reason):
        table, reverse = self.tables[kind], self.reverse[kind]
        if before in table or after in reverse:
            agrees = table.get(before) == after
            if not agrees:
                conflict = {
                    "kind": kind, "old": before, "proposed": after, "reason": reason,
                    "existing": table.get(before), "target_already_used_by": reverse.get(after),
                }
                if conflict not in self.conflicts:
                    self.conflicts.append(conflict)
            return agrees
        table[before], reverse[after] = after, before
        self.reasons[kind][before] = reason
        return True

    def canonical(self):
        Canonical.clear_caches()
        args = [self.tables[kind] for kind in KINDS]
        return Canonical(self.old, *args, "old"), Canonical(self.new, *args, "new")

    def class_compatible(self, before, after, old, new):
        left, right = self.old.classes[before], self.new.classes[after]
        return (left["flags"] == right["flags"] and
                old.types(left.get("parent"), before) == new.types(right.get("parent"), after) and
                sorted(old.types(value, before) for value in left["interfaces"]) ==
                sorted(new.types(value, after) for value in right["interfaces"]))

    def translated(self, text):
        def replace(match):
            typ = match[0]
            return self.tables["classes"].get(typ, typ if typ not in self.old.classes else "LUNRESOLVED;")
        return TYPE.sub(replace, text)

    def reference_compatible(self, before, after, kind):
        old_items, new_items = getattr(self.old, kind), getattr(self.new, kind)
        if before not in old_items or after not in new_items:
            return False
        left, right = split_member(before), split_member(after)
        return (self.translated(left[0]) == right[0] and self.translated(left[2]) == right[2] and
                old_items[before]["flags"] == new_items[after]["flags"])

    def seed_classes(self, old, new):
        before = self.old.classes.keys() - self.tables["classes"].keys()
        after = self.new.classes.keys() - self.reverse["classes"].keys()
        for source, target in unique_pairs(before, after, old.class_key, new.class_key):
            self.bind("classes", source, target, "unique-class-body")
        for canonical, source in sorted(self.known["classes"].items()):
            if canonical != source or source not in self.new.classes:
                continue
            if self.class_compatible(source, source, old, new) and old.class_key(source, False) == new.class_key(source, False):
                self.bind("classes", source, source, "retained-original-class-and-structure")
        if not self.source_metadata:
            return
        useful = lambda cls: cls.get("source") not in (None, "SourceFile", "R8$$SyntheticClass")
        pairs = unique_pairs(
            [typ for typ in before if useful(self.old.classes[typ])],
            [typ for typ in after if useful(self.new.classes[typ])],
            lambda typ: (self.old.classes[typ]["source"], self.old.classes[typ]["flags"]),
            lambda typ: (self.new.classes[typ]["source"], self.new.classes[typ]["flags"]),
        )
        for source, target in pairs:
            if not self.class_compatible(source, target, old, new):
                continue
            left, right = self.old.classes[source], self.new.classes[target]
            left_keys = {old.method_key(method["id"]) for method in left["methods"] if len(method.get("code", [])) > 5}
            right_keys = {new.method_key(method["id"]) for method in right["methods"] if len(method.get("code", [])) > 5}
            shared, total = len(left_keys & right_keys), min(len(left_keys), len(right_keys))
            if old.class_key(source, False) == new.class_key(target, False) or shared and shared / total >= 0.5:
                self.bind("classes", source, target, "source-and-binary-structure")

    def exact_methods(self, old, new):
        for source, target in sorted(self.tables["classes"].items()):
            pairs = unique_pairs(
                [method["id"] for method in self.old.classes[source]["methods"]
                 if "code" in method and method["id"] not in self.tables["methods"]],
                [method["id"] for method in self.new.classes[target]["methods"]
                 if "code" in method and method["id"] not in self.reverse["methods"]],
                old.method_key, new.method_key,
            )
            for before, after in pairs:
                self.bind("methods", before, after, "unique-method-body-in-owner")

    def retained_api(self, old, new):
        votes, proposals = collections.defaultdict(set), []

        def type_votes(before, after):
            left, right = TYPE.findall(before), TYPE.findall(after)
            if len(left) != len(right):
                raise MappingError("Retained API changes its type structure")
            for source, target in zip(left, right):
                if source in self.old.classes and target in self.new.classes:
                    votes[source].add(target)

        for canonical, source in sorted(self.known["methods"].items()):
            owner, name, signature = split_member(source)
            if name != split_member(canonical)[1] or source in self.tables["methods"]:
                continue
            target_owner = self.tables["classes"].get(owner)
            if target_owner is None:
                continue
            method = self.old.methods[source]
            if "code" not in method and not method["flags"] & 0x400:
                continue
            candidates = [
                target["id"] for target in self.new.classes[target_owner]["methods"]
                if target["name"] == name and old.method_key(source) == new.method_key(target["id"])
            ]
            if len(candidates) == 1:
                target = candidates[0]
                type_votes(signature, split_member(target)[2])
                proposals.append(("methods", source, target, "retained-original-api-and-body"))
        for canonical, source in sorted(self.known["fields"].items()):
            owner, name, signature = split_member(source)
            field = self.old.fields[source]
            if name != split_member(canonical)[1] or source in self.tables["fields"]:
                continue
            target_owner = self.tables["classes"].get(owner)
            if target_owner is None or field["flags"] & 0x18 != 0x18 or "initial" not in field:
                continue
            candidates = [
                target["id"] for target in self.new.classes[target_owner]["fields"]
                if target["name"] == name and target["flags"] == field["flags"]
                and target.get("initial") == field["initial"]
                and old.types(field["type"], owner) == new.types(target["type"], target_owner)
            ]
            if len(candidates) == 1:
                target = candidates[0]
                type_votes(signature, split_member(target)[2])
                proposals.append(("fields", source, target, "retained-original-constant-and-value"))
        reverse_votes = collections.defaultdict(set)
        for source, targets in votes.items():
            for target in targets:
                reverse_votes[target].add(source)
        for source, targets in sorted(votes.items()):
            if len(targets) == 1:
                target = next(iter(targets))
                if len(reverse_votes[target]) == 1 and self.class_compatible(source, target, old, new):
                    self.bind("classes", source, target, "retained-original-api-type")
        for kind, source, target, reason in proposals:
            if self.reference_compatible(source, target, kind):
                self.bind(kind, source, target, reason)

    def propagate(self, old, new):
        votes = {kind: collections.defaultdict(set) for kind in KINDS}
        for source, target in sorted(self.tables["methods"].items()):
            if old.code(source) is None or old.code(source) != new.code(target):
                continue
            before, after = self.old.methods[source], self.new.methods[target]
            for left, right in zip(before["code"], after["code"]):
                for key in ("ref", "ref2"):
                    if key not in left or key not in right:
                        continue
                    kind, address = left[key]
                    next_kind, next_address = right[key]
                    if kind != next_kind or kind not in ("method", "field", "type", "proto"):
                        continue
                    types_before, types_after = TYPE.findall(address), TYPE.findall(next_address)
                    if len(types_before) != len(types_after):
                        continue
                    for first, second in zip(types_before, types_after):
                        if first in self.old.classes and second in self.new.classes:
                            votes["classes"][first].add(second)
                    if kind in ("method", "field"):
                        votes[kind + "s"][address].add(next_address)
        for kind, by_source in votes.items():
            by_target = collections.defaultdict(set)
            for source, targets in by_source.items():
                for target in targets:
                    by_target[target].add(source)
            for source, targets in sorted(by_source.items()):
                if len(targets) != 1:
                    continue
                target = next(iter(targets))
                if len(by_target[target]) != 1:
                    continue
                compatible = self.class_compatible(source, target, old, new) if kind == "classes" else \
                    self.reference_compatible(source, target, kind)
                if compatible:
                    self.bind(kind, source, target, "aligned-reference")

    def run(self):
        iteration = 0
        try:
            while True:
                iteration += 1
                before_count = sum(map(len, self.tables.values()))
                old, new = self.canonical()
                self.seed_classes(old, new)
                old, new = self.canonical()
                self.exact_methods(old, new)
                self.retained_api(old, new)
                old, new = self.canonical()
                self.propagate(old, new)
                counts = {kind: len(table) for kind, table in self.tables.items()}
                print(f"Round {iteration}: {counts}", file=sys.stderr)
                if sum(counts.values()) == before_count:
                    break
        finally:
            Canonical.clear_caches()

    def candidates(self, kind, source, old, new):
        if kind == "classes":
            cls = self.old.classes[source]
            matches = []
            for target, candidate in self.new.classes.items():
                same_source = self.source_metadata and cls.get("source") not in (None, "SourceFile", "R8$$SyntheticClass") and \
                    cls["source"] == candidate.get("source")
                same_shape = old.class_key(source, False) == new.class_key(target, False)
                if same_source or same_shape:
                    left = collections.Counter(old.method_key(item["id"]) for item in cls["methods"])
                    right = collections.Counter(new.method_key(item["id"]) for item in candidate["methods"])
                    matches.append((target, similarity(left, right)))
        else:
            owner = split_member(source)[0]
            target_owner = self.tables["classes"].get(owner)
            if target_owner is None:
                return []
            matches = []
            for candidate in self.new.classes[target_owner][kind]:
                target = candidate["id"]
                if kind == "methods":
                    if old.method_key(source, False) == new.method_key(target, False):
                        matches.append((target, similarity(old.features(source), new.features(target))))
                else:
                    field = self.old.fields[source]
                    if field["flags"] == candidate["flags"] and \
                            old.types(field["type"], owner) == new.types(candidate["type"], target_owner):
                        score = 1.0 if "initial" in field and field["initial"] == candidate.get("initial") else 0.0
                        matches.append((target, score))
        return [
            {"target": target, "similarity": round(score, 4),
             "already_assigned_to": self.reverse[kind].get(target)}
            for target, score in sorted(matches, key=lambda item: (-item[1], item[0]))[:3]
        ]

    def report(self):
        old, new = self.canonical()
        report = {
            "schema": CACHE_SCHEMA, "old_apk": self.old.metadata, "new_apk": self.new.metadata,
            "source_metadata": self.source_metadata, "symbols": {}, "conflicts": self.conflicts,
            "changed_method_bodies": [],
        }
        try:
            for kind in KINDS:
                entries = []
                for canonical, source in sorted(self.known[kind].items()):
                    target = self.tables[kind].get(source)
                    item = {"canonical": canonical, "old": source, "new": target,
                            "flags": getattr(self.old, kind)[source]["flags"]}
                    if target is None:
                        item["candidates"] = self.candidates(kind, source, old, new)
                    else:
                        item["evidence"] = self.reasons[kind][source]
                        if kind == "methods" and old.code(source) != new.code(target):
                            report["changed_method_bodies"].append(item.copy())
                    entries.append(item)
                report["symbols"][kind] = entries
        finally:
            Canonical.clear_caches()
        report["summary"] = {
            kind: {"total": len(entries), "matched": sum(item["new"] is not None for item in entries),
                   "unresolved": sum(item["new"] is None for item in entries)}
            for kind, entries in report["symbols"].items()
        }
        report["mapping_ready"] = not report["conflicts"] and all(
            not item["unresolved"] for item in report["summary"].values())
        return report


def compare_expected(report, expected):
    labels = mapping_symbols(expected)
    comparison = {}
    for kind, entries in report["symbols"].items():
        surviving = [item for item in entries if item["canonical"] in labels[kind]]
        correct = sum(item["new"] == labels[kind][item["canonical"]] for item in surviving if item["new"] is not None)
        mismatches = [
            {**item, "expected": labels[kind].get(item["canonical"])}
            for item in entries if item["new"] is not None and item["new"] != labels[kind].get(item["canonical"])
        ]
        comparison[kind] = {
            "surviving": len(surviving), "removed": len(entries) - len(surviving),
            "correct": correct, "recovery_percent": round(100 * correct / len(surviving), 2) if surviving else None,
            "mismatches": mismatches,
        }
    report["comparison"] = comparison


def render_mapping(mapping, report, new_inventory):
    if not report["mapping_ready"]:
        raise MappingError("Refusing to emit an incomplete or conflicting mapping; resolve the JSON review queue")
    targets = {
        kind: {item["canonical"]: item["new"] for item in entries}
        for kind, entries in report["symbols"].items()
    }
    for kind in KINDS:
        if len(set(targets[kind].values())) != len(targets[kind]):
            raise MappingError(f"Conflicting {kind} targets cannot be represented by a reversible library mapping")
    missing_classes = set(targets["classes"].values()) - new_inventory.classes.keys()
    if missing_classes:
        raise MappingError(f"Relocated classes are missing from the APK: {', '.join(sorted(missing_classes))}")
    def observed_type(text):
        return TYPE.sub(lambda match: targets["classes"].get(match[0], match[0]), text)
    output = [
        f"# Reconstructed PARTIAL library mapping for the official Boost {new_inventory.metadata['version']} APK.",
        f"# APK SHA-256: {new_inventory.metadata['apk_sha256']}",
        "# Proposed by scripts/update_boost_mapping.py; review changed bodies before release.",
    ]
    declarations = {}
    source_flags = {
        kind: {item["canonical"]: item["flags"] for item in entries}
        for kind, entries in report["symbols"].items()
    }
    for kind, table in (("methods", mapping.methods), ("fields", mapping.fields)):
        for canonical in table.values():
            declarations[symbol(canonical)] = canonical
        for canonical, target in targets[kind].items():
            declaration = declarations[canonical]
            owner, name, signature = split_member(target)
            if owner != observed_type(declaration[0]) or signature != (
                    observed_type(declaration[2]) if kind == "methods" else ":" + observed_type(declaration[2])):
                raise MappingError(f"Relocated declaration is incompatible: {canonical} -> {target}")
            if target not in getattr(new_inventory, kind):
                raise MappingError(f"Relocated declaration is missing from the APK: {target}")
            if getattr(new_inventory, kind)[target]["flags"] & 8 != source_flags[kind][canonical] & 8:
                raise MappingError(f"Relocated declaration changes static/instance dispatch: {canonical}")
            if kind == "methods":
                original_name = split_member(canonical)[1]
                if (name in ("<init>", "<clinit>") or original_name in ("<init>", "<clinit>")) and name != original_name:
                    raise MappingError(f"Relocated declaration changes constructor kind: {canonical}")
    canonical_owner = None
    for line in mapping.path.read_text(encoding="utf-8").splitlines():
        if line.startswith(("# Reconstructed PARTIAL", "# APK SHA-256:", "# Proposed by scripts/update_boost_mapping.py")):
            continue
        if not line.strip() or line.startswith("#"):
            output.append(line)
        elif not line[0].isspace():
            original = line.split(" -> ", 1)[0]
            canonical_owner = "L" + original.replace(".", "/") + ";"
            output.append(original + " -> " + targets["classes"][canonical_owner][1:-1].replace("/", ".") + ":")
        else:
            declaration = line.strip().split(" -> ", 1)[0]
            method = re.fullmatch(r"(\S+) ([^ (]+)\(([^)]*)\)", declaration)
            if method:
                returns, name, parameters = method.groups()
                canonical = canonical_owner + "->" + name + "(" + "".join(
                    java_type(value.strip()) for value in parameters.split(",") if value.strip()
                ) + ")" + java_type(returns)
                target = targets["methods"][canonical]
            else:
                typ, name = declaration.split()
                canonical = canonical_owner + "->" + name + ":" + java_type(typ)
                target = targets["fields"][canonical]
            output.append("    " + declaration + " -> " + split_member(target)[1])
    return "\n".join(output) + "\n"


def write_atomic(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent, delete=False) as destination:
            temporary = Path(destination.name)
            destination.write(text)
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--old-apk", type=Path, required=True)
    parser.add_argument("--old-mapping", type=Path, required=True)
    parser.add_argument("--new-apk", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--output-mapping", type=Path)
    parser.add_argument("--expected-mapping", type=Path, help="Score results only; never used to select matches")
    parser.add_argument("--cache-dir", type=Path, default=ROOT / "build/boost-fingerprints")
    parser.add_argument("--aapt2", type=Path)
    parser.add_argument("--no-source-metadata", action="store_true")
    args = parser.parse_args(argv)
    try:
        inputs = {path.resolve() for path in (args.old_apk, args.old_mapping, args.new_apk, args.expected_mapping) if path}
        outputs = [path.resolve() for path in (args.report, args.output_mapping) if path]
        if len(set(outputs)) != len(outputs) or any(path in inputs for path in outputs):
            raise MappingError("Output paths must be distinct from each other and all inputs")
        mapping = Mapping(args.old_mapping.resolve())
        aapt2 = find_aapt2(args.aapt2)
        old = Inventory.load(args.old_apk.resolve(), args.cache_dir.resolve(), aapt2)
        known = verify_mapping(mapping, old)
        new = Inventory.load(args.new_apk.resolve(), args.cache_dir.resolve(), aapt2)
        matcher = Matcher(old, new, known, not args.no_source_metadata)
        matcher.run()
        report = matcher.report()
        # The target map is deliberately unavailable to the matcher.
        if args.expected_mapping is not None:
            expected = Mapping(args.expected_mapping.resolve())
            verify_mapping(expected, new)
            compare_expected(report, expected)
        mismatches = sum(len(item["mismatches"]) for item in report.get("comparison", {}).values())
        mapping_text = None
        if report["mapping_ready"] and not mismatches:
            try:
                candidate = render_mapping(mapping, report, new)
                if args.output_mapping is not None:
                    mapping_text = candidate
            except MappingError as error:
                report["mapping_ready"] = False
                report["output_error"] = str(error)
        write_atomic(args.report.resolve(), json.dumps(report, indent=2, sort_keys=True) + "\n")
        print(json.dumps({"summary": report["summary"], "comparison": report.get("comparison"),
                          "changed_method_bodies": len(report["changed_method_bodies"]),
                          "conflicts": len(report["conflicts"])}, indent=2))
        print(f"Review report: {args.report}")
        if mapping_text is not None:
            write_atomic(args.output_mapping.resolve(), mapping_text)
            print(f"Proposed mapping: {args.output_mapping}")
        elif args.output_mapping is not None:
            print(report.get("output_error", "Mapping not emitted: unresolved symbols, conflicts, or comparison mismatches"),
                  file=sys.stderr)
        if mismatches:
            return 1
        return 0 if report["mapping_ready"] and not report["changed_method_bodies"] else 2
    except (ValueError, OSError, EOFError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
