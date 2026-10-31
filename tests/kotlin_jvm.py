#!/usr/bin/env python3
"""Helpers for JVM-level contract tests of the Kotlin/Native-free sources.

These tests are **not** device tests: they compile the real source files against
tiny hand-written Android stubs and run them on a plain JVM. That is the only way
to keep a fast gate for code that normally needs an emulator.

v15.2: the UI-facing repositories moved to Kotlin, so the gates need a Kotlin
compiler. `kotlinc` is looked up from KOTLINC / PATH / ~/.local/kotlinc; when it
is missing the caller should skip instead of failing (see `require_kotlinc`).
"""
from pathlib import Path
import os
import shutil
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
MAIN_JAVA = ROOT / 'app/src/main/java'


def kotlinc_jar_dir(kotlinc: Path) -> Path:
    """<dist>/bin/kotlinc → <dist>/lib"""
    return kotlinc.resolve().parent.parent / 'lib'


def find_kotlinc():
    """Return the kotlinc executable or None."""
    explicit = os.environ.get('KOTLINC')
    if explicit:
        candidate = Path(explicit)
        if candidate.is_dir():
            candidate = candidate / 'bin' / 'kotlinc'
        return candidate if candidate.exists() else None
    on_path = shutil.which('kotlinc')
    if on_path:
        return Path(on_path)
    fallback = Path.home() / '.local/kotlinc/bin/kotlinc'
    return fallback if fallback.exists() else None


def require_kotlinc():
    """Return kotlinc, or exit 0 with a SKIP notice when no compiler is available."""
    kotlinc = find_kotlinc()
    if kotlinc is None:
        print('SKIP: kotlinc not found (set KOTLINC=/path/to/kotlinc or install the Kotlin '
              'compiler). This gate compiles the real Kotlin repositories against JVM stubs.')
        raise SystemExit(0)
    return kotlinc


def download(url: str, target: Path) -> Path:
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(url, target)
    return target


def javac(out_dir: Path, sources, classpath=()):
    args = ['javac', '-encoding', 'UTF-8', '-d', str(out_dir), '-nowarn']
    if classpath:
        args += ['-cp', os.pathsep.join(str(c) for c in classpath)]
    subprocess.run(args + [str(s) for s in sources], check=True)


def kotlinc_compile(kotlinc: Path, out_dir: Path, sources, classpath=()):
    args = [str(kotlinc), '-nowarn', '-d', str(out_dir)]
    if classpath:
        args += ['-classpath', os.pathsep.join(str(c) for c in classpath)]
    subprocess.run(args + [str(s) for s in sources], check=True)


def kotlin_runtime(kotlinc: Path):
    """kotlin-stdlib + kotlinx-coroutines, needed to *run* the compiled code."""
    lib = kotlinc_jar_dir(kotlinc)
    stdlib = lib / 'kotlin-stdlib.jar'
    if not stdlib.exists():
        raise SystemExit('kotlin-stdlib.jar not found next to ' + str(kotlinc))
    return [stdlib]


def coroutines_jar(cache_dir: Path) -> Path:
    return download(
        'https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/'
        'kotlinx-coroutines-core-jvm/1.8.1/kotlinx-coroutines-core-jvm-1.8.1.jar',
        cache_dir / 'kotlinx-coroutines-core-jvm-1.8.1.jar')


def run_java(main_class: str, classpath):
    subprocess.run(['java', '-cp', os.pathsep.join(str(c) for c in classpath), main_class],
                   check=True)


def write_sources(base: Path, files: dict):
    for name, content in files.items():
        target = base / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding='utf-8')
    return [base / name for name in files]
