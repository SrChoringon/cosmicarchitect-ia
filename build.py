#!/usr/bin/env python3
"""Reproducible local compiler bootstrap. Needs Python 3, Java 21, HTTPS."""
import hashlib
import pathlib
import subprocess
import urllib.request
import zipfile
import re

ROOT = pathlib.Path(__file__).resolve().parent
DEPS = ROOT / 'deps'
CLASSES = ROOT / 'build/classes'
DEPS.mkdir(exist_ok=True)
CLASSES.mkdir(parents=True, exist_ok=True)
CENTRAL = 'https://repo.maven.apache.org/maven2/'
ARTIFACTS = [
    'org/eclipse/jdt/ecj/3.40.0/ecj-3.40.0.jar',
    'net/kyori/adventure-api/4.26.1/adventure-api-4.26.1.jar',
    'net/kyori/adventure-key/4.26.1/adventure-key-4.26.1.jar',
    'net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar',
    'net/kyori/examination-string/1.3.0/examination-string-1.3.0.jar',
    'org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar',
    'org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar',
    'org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar',
    'com/google/guava/guava/33.3.1-jre/guava-33.3.1-jre.jar',
    'com/google/code/gson/gson/2.11.0/gson-2.11.0.jar',
]

def fetch(url, target):
    if not target.exists():
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read()
        temporary = target.with_suffix('.tmp')
        temporary.write_bytes(data)
        temporary.replace(target)

for artifact in ARTIFACTS:
    fetch(CENTRAL + artifact, DEPS / artifact.split('/')[-1])
# Pin the exact API used for the release; recorded from official repository metadata.
lock = ROOT / 'paper-api-version.txt'
if not lock.exists():
    raise SystemExit('Missing paper-api-version.txt')
paper_version = lock.read_text().strip()
fetch('https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/'
      '1.21.11-R0.1-SNAPSHOT/paper-api-' + paper_version + '.jar', DEPS / 'paper-api.jar')
import os
classpath = os.pathsep.join(str(p) for p in sorted(DEPS.glob('*.jar')))
compiler = DEPS / 'ecj-3.40.0.jar'
sources = sorted((ROOT / 'src/main/java').rglob('*.java'))
subprocess.run(['java', '-jar', str(compiler), '-21', '-proc:none', '-cp', classpath,
                '-d', str(CLASSES), *map(str, sources)], check=True, cwd=ROOT)
subprocess.run(['java', '-jar', str(compiler), '-21', '-proc:none', '-cp', str(CLASSES),
                '-d', str(ROOT / 'build/test-classes'), str(ROOT / 'tests/ArchitectureTest.java')], check=True, cwd=ROOT)
subprocess.run(['java', '-Xmx512M', '-cp', os.pathsep.join([str(CLASSES), str(ROOT / 'build/test-classes')]),
                'ArchitectureTest'], check=True, cwd=ROOT)
version = re.search(r'^version: (.+)$', (ROOT / 'src/main/resources/plugin.yml').read_text(), re.M).group(1).strip()
target = ROOT / ('build/CosmicArchitect-' + version + '.jar')
with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
    for folder in [CLASSES, ROOT / 'src/main/resources']:
        for path in sorted(folder.rglob('*')):
            if path.is_file() and path.name != 'ArchitectureTest.class':
                info = zipfile.ZipInfo(path.relative_to(folder).as_posix(), (2026, 11, 7, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, path.read_bytes())
print(target.name, 'SHA256', hashlib.sha256(target.read_bytes()).hexdigest())
