#!/usr/bin/env python3
"""안드로이드 앱의 tr(...) 번역 중 값이 들어가는 문장을 iOS용 표(ios/Bubble/Templates.swift)로 만듭니다.

Kotlin:  tr("보유 ${p.potions}개", "Owned: ${p.potions}", ja = "所持 ${p.potions}個")
Swift:   tf("보유 {0}개", p.potions)

값 자리({0}, {1}…)는 한국어 문장에 나온 순서대로 번호를 매기고, 다른 언어는 같은 식(예: p.potions)을
같은 번호로 바꿉니다. 그래서 언어마다 순서가 달라도 맞는 값이 들어갑니다.

다시 만들기 (저장소 맨 위 폴더에서):  python3 ios/tools/gen_templates.py
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java"
OUT = ROOT / "ios/Bubble/Templates.swift"
LANGS = ["en", "zh", "hi", "es", "fr", "ar", "de", "pt", "ru", "ja"]


def parse_string(s, i):
    """s[i] == '"' 인 Kotlin 문자열을 읽어 (부분 목록, 끝 위치)를 돌려줌. 부분은 ('t', 글) 또는 ('e', 식)."""
    assert s[i] == '"'
    if s.startswith('"""', i):
        return None, i
    i += 1
    parts, buf = [], []
    while i < len(s):
        c = s[i]
        if c == '"':
            if buf:
                parts.append(("t", "".join(buf)))
            return parts, i + 1
        if c == "\\":
            n = s[i + 1]
            if n == "u":
                buf.append(chr(int(s[i + 2:i + 6], 16)))
                i += 6
                continue
            buf.append({"n": "\n", "t": "\t", "r": "\r", "$": "$", '"': '"', "\\": "\\", "'": "'"}.get(n, n))
            i += 2
            continue
        if c == "$" and i + 1 < len(s) and (s[i + 1] == "{" or s[i + 1].isalpha() or s[i + 1] == "_"):
            if buf:
                parts.append(("t", "".join(buf)))
                buf = []
            if s[i + 1] == "{":
                depth, j = 1, i + 2
                while depth:
                    if s[j] == "{":
                        depth += 1
                    elif s[j] == "}":
                        depth -= 1
                    elif s[j] == '"':
                        _, j = parse_string(s, j)
                        continue
                    j += 1
                parts.append(("e", s[i + 2:j - 1].strip()))
                i = j
            else:
                m = re.match(r"[A-Za-z_][A-Za-z0-9_]*", s[i + 1:])
                parts.append(("e", m.group(0)))
                i += 1 + len(m.group(0))
            continue
        if c == "\n":
            return None, i
        buf.append(c)
        i += 1
    return None, i


def skip_ws(s, i):
    while i < len(s):
        if s[i].isspace():
            i += 1
        elif s.startswith("//", i):
            i = s.index("\n", i)
        else:
            break
    return i


def parse_call(s, i):
    """s[i]는 'tr(' 다음 위치. 인자 목록 [(이름 또는 None, 부분)]과 끝 위치. 문자열만으로 된 호출이 아니면 None."""
    args = []
    while True:
        i = skip_ws(s, i)
        if s[i] == ")":
            return args, i + 1
        name = None
        m = re.match(r"([a-z]{2})\s*=\s*", s[i:])
        if m:
            name = m.group(1)
            i += m.end()
        parts = []
        while True:
            i = skip_ws(s, i)
            if s[i] != '"':
                return None, i
            p, i = parse_string(s, i)
            if p is None:
                return None, i
            parts += p
            i = skip_ws(s, i)
            if s[i] == "+":
                i += 1
                continue
            break
        args.append((name, parts))
        i = skip_ws(s, i)
        if s[i] == ",":
            i += 1
        elif s[i] != ")":
            return None, i


def template(parts, index):
    out = []
    for kind, v in parts:
        if kind == "t":
            out.append(v.replace("{", "{{").replace("}", "}}"))
        else:
            if v not in index:
                return None
            out.append("{%d}" % index[v])
    return "".join(out)


def swift_str(v):
    out = []
    for ch in v:
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\t":
            out.append("\\t")
        elif ord(ch) < 0x20:
            out.append("\\u{%x}" % ord(ch))
        else:
            out.append(ch)
    return '"' + "".join(out) + '"'


def main():
    table = {}
    for f in sorted(SRC.rglob("*.kt")):
        s = f.read_text(encoding="utf-8")
        for m in re.finditer(r"(?<![A-Za-z0-9_.])tr\(", s):
            args, _ = parse_call(s, m.end())
            if not args or len(args) < 2 or args[0][0] or args[1][0]:
                continue
            ko = args[0][1]
            exprs = [v for k, v in ko if k == "e"]
            if not exprs:
                continue  # 값이 없는 문장은 Strings.swift(t)가 맡음
            index = {}
            for e in exprs:
                index.setdefault(e, len(index))
            key = template(ko, index)
            row = {}
            for name, parts in [("en", args[1][1])] + [(n, p) for n, p in args[2:]]:
                if name in LANGS:
                    tpl = template(parts, index)
                    if tpl is not None:
                        row[name] = tpl
            if "en" in row and key not in table:
                table[key] = row
    lines = [
        "// 자동 생성: python3 ios/tools/gen_templates.py (안드로이드 tr(...) 중 값이 들어가는 문장).",
        "// 손으로 고치지 마세요. 한국어 문장(값 자리는 {0}, {1}…) → 언어별 번역.",
        "let templateStrings: [String: [String: String]] = [",
    ]
    for key in sorted(table):
        row = table[key]
        cells = ", ".join(f"{swift_str(l)}: {swift_str(row[l])}" for l in LANGS if l in row)
        lines.append(f"    {swift_str(key)}: [{cells}],")
    lines.append("]")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(table)} templates → {OUT.relative_to(ROOT)}", file=sys.stderr)


if __name__ == "__main__":
    main()
