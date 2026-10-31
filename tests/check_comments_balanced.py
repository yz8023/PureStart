#!/usr/bin/env python3
"""块注释嵌套守门：Kotlin 的 `/* */` **可以嵌套**，Java 的不能。

这不是风格检查，而是抓一类静默事故：注释里只要出现 `/*`（例如写成
`app/src/main/**` 或 `META-INF/*.kotlin_module`），Kotlin 就会把它当成新的注释开始，
随后的 `*/` 只关掉内层——外层注释一路吞到下一个 `*/`，中间的**配置代码整段消失**。
真实案例：`app/build.gradle.kts` 的 KDoc 里写了 `app/src/main/**`，导致整个
`android { }` 块被注释掉，构建报错却是「project ':app' does not specify compileSdk」，
从错误信息完全看不出根因。

规则：
  1. Kotlin（.kt/.kts）：块注释内不允许再出现 `/*`；文件结束必须回到 0 层。
  2. Java（.java）：块注释不嵌套，只要求文件结束回到 0 层。
  3. 字符串与字符字面量（含 Kotlin 原始字符串 `\"\"\"…\"\"\"`）不参与判断。
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [ROOT / 'app/src/main/java', ROOT / 'app']
KOTLIN_SUFFIXES = ('.kt', '.kts')
JAVA_SUFFIXES = ('.java',)
SKIP_DIRS = {'build', '.gradle'}


def files():
    seen = set()
    for base in SOURCES:
        for path in sorted(base.rglob('*')):
            if not path.is_file() or path.suffix not in KOTLIN_SUFFIXES + JAVA_SUFFIXES:
                continue
            if any(part in SKIP_DIRS for part in path.parts):
                continue
            if path in seen:
                continue
            seen.add(path)
            yield path


def scan(path: Path):
    """返回 (错误列表)。逐字符扫描，区分注释/字符串。"""
    text = path.read_text(encoding='utf-8')
    kotlin = path.suffix in KOTLIN_SUFFIXES
    errors = []
    depth = 0
    line = 1
    i = 0
    while i < len(text):
        char = text[i]
        pair = text[i:i + 2]
        if char == '\n':
            line += 1
            i += 1
            continue
        if depth > 0:
            if pair == '/*':
                if kotlin:
                    errors.append('%s:%d: 块注释内又出现 `/*`（Kotlin 会嵌套，后面的代码会被吞掉）'
                                  % (path.relative_to(ROOT), line))
                i += 2
                continue
            if pair == '*/':
                depth -= 1
                i += 2
                continue
            i += 1
            continue
        if pair == '//':
            while i < len(text) and text[i] != '\n':
                i += 1
            continue
        if pair == '/*':
            depth = 1
            i += 2
            continue
        if kotlin and text[i:i + 3] == '"""':
            i += 3
            while i < len(text) and text[i:i + 3] != '"""':
                if text[i] == '\n':
                    line += 1
                i += 1
            i += 3
            continue
        if char == '"':
            i += 1
            while i < len(text) and text[i] != '"':
                if text[i] == '\\':
                    i += 1
                elif text[i] == '\n':
                    break
                i += 1
            i += 1
            continue
        if char == "'":
            i += 1
            while i < len(text) and text[i] != "'":
                if text[i] == '\\':
                    i += 1
                elif text[i] == '\n':
                    break
                i += 1
            i += 1
            continue
        i += 1
    if depth != 0:
        errors.append('%s: 文件结束仍有 %d 层块注释未闭合（后面的内容全部被当成注释）'
                      % (path.relative_to(ROOT), depth))
    return errors


def main():
    problems = []
    checked = 0
    for path in files():
        checked += 1
        problems += scan(path)
    if problems:
        for problem in problems:
            print('  !! ' + problem, file=sys.stderr)
        print('FAIL: %d 处块注释问题（Kotlin 块注释可嵌套：注释里不要写 `/*`）' % len(problems))
        return 1
    print('PASS: %d 个 Kotlin/Java 源文件块注释闭合且无嵌套' % checked)
    return 0


if __name__ == '__main__':
    sys.exit(main())
