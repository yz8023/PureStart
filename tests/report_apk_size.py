#!/usr/bin/env python3
"""APK/AAB 体积拆解，用于第三阶段「压了多少」的对比（不需要 ANDROID_HOME、不需要设备）。

用法：
    python3 tests/report_apk_size.py app/build/outputs/apk/release/app-release.apk
    python3 tests/report_apk_size.py 新.apk 旧.apk      # 同时给出逐项差值

输出：
    - 压缩后总大小 vs 解压后总大小（APK 里 dex 的「真实占用」看压缩后）
    - 按类别拆分：dex / resources.arsc / res / META-INF / lib / assets / 其它
    - 未压缩体积 Top 15 条目
    - 「不应出现」清单：kotlin_module、DebugProbesKt.bin、*.txt、多语言 res/values-**
      （出现即说明 packaging/resourceConfigurations 配置退化了）
"""
from __future__ import annotations
import sys
import zipfile
from collections import defaultdict
from pathlib import Path


def classify(name: str) -> str:
    if name.startswith('classes') and name.endswith('.dex'):
        return 'dex'
    if name == 'resources.arsc':
        return 'resources.arsc'
    if name.startswith('res/'):
        return 'res/'
    if name.startswith('META-INF/'):
        return 'META-INF/'
    if name.startswith('lib/'):
        return 'lib/'
    if name.startswith('assets/'):
        return 'assets/'
    return '其它'


def measure(path: Path):
    with zipfile.ZipFile(path) as apk:
        entries = [(info.filename, info.file_size, info.compress_size) for info in apk.infolist()]
    by_class = defaultdict(lambda: [0, 0, 0])
    for name, size, packed in entries:
        bucket = by_class[classify(name)]
        bucket[0] += 1
        bucket[1] += size
        bucket[2] += packed
    return entries, by_class


def kb(value: int) -> str:
    return '%8.1f KB' % (value / 1024.0)


def report(path: Path):
    entries, by_class = measure(path)
    total = sum(e[2] for e in entries)
    raw = sum(e[1] for e in entries)
    print('== %s' % path)
    print('   压缩后 %s   解压后 %s   条目 %d' % (kb(total), kb(raw), len(entries)))
    print('   %-16s %5s %12s %12s' % ('类别', '条目', '解压', '压缩后'))
    for name, (count, size, packed) in sorted(by_class.items(), key=lambda kv: -kv[1][2]):
        print('   %-16s %5d %s %s' % (name, count, kb(size), kb(packed)))
    print('   -- 未压缩体积最大的 15 个条目 --')
    for name, size, packed in sorted(entries, key=lambda e: -e[1])[:15]:
        print('   %s %s  (%s)' % (kb(size), name[:70], kb(packed)))
    junk = [n for n, _, _ in entries
            if n.endswith('.kotlin_module') or n.endswith('DebugProbesKt.bin')
            or n.endswith('.txt') or n.endswith('.kotlin_builtins')]
    locales = sorted({n.split('/')[1] for n in (e[0] for e in entries)
                      if n.startswith('res/values-') and n.count('/') >= 2} - {'values'})
    if junk:
        print('   !! 不应出现的构建期元数据：%s' % junk[:8])
    if len(locales) > 2:
        print('   !! 语言资源未裁剪（%d 种）：%s …' % (len(locales), locales[:8]))
    if not junk and len(locales) <= 2:
        print('   元数据清理与语言裁剪：正常')
    return total


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    new = Path(sys.argv[1])
    if not new.exists():
        raise SystemExit('找不到 APK：%s（先 bash build.sh）' % new)
    new_total = report(new)
    if len(sys.argv) > 2:
        old = Path(sys.argv[2])
        if not old.exists():
            raise SystemExit('找不到对比 APK：%s' % old)
        print()
        old_total = report(old)
        delta = new_total - old_total
        print()
        print('== 对比：%s -> %s' % (old.name, new.name))
        print('   %s -> %s （%s，%.1f%%）' % (kb(old_total), kb(new_total),
                                              ('+' if delta >= 0 else '') + kb(delta), 100.0 * delta / old_total))


if __name__ == '__main__':
    main()
