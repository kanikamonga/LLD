---
name: md-to-docx
description: 'Convert a Markdown (.md) file into a professionally formatted Word (.docx) document with embedded PNG images, styled tables, code blocks, and a table of contents. Use whenever the user asks to convert markdown to docx/Word or export a .md file as a Word document.'
---

# Markdown to Word (.docx) Skill

Convert Markdown files into professionally formatted Word `.docx` documents using a
pre-installed **pure-JavaScript** converter (`docx` + `marked` npm packages). No Pandoc,
LibreOffice, or native binaries required.

## When to use

Trigger this skill whenever the user wants to:
- Convert a `.md` file to `.docx` / Word
- Export or "make a Word doc" from markdown
- Produce a shareable Word version of design docs, notes, or reports

## How to run

The converter is already installed (dependencies included) at:

```
C:\Users\kanikamonga\OneDrive - Microsoft\MyProjects\md-to-docx\scripts\md-to-docx.mjs
```

Run it with Node.js (18+):

```powershell
node "C:\Users\kanikamonga\OneDrive - Microsoft\MyProjects\md-to-docx\scripts\md-to-docx.mjs" "<input.md>" "<output.docx>"
```

- If `<output.docx>` is omitted, it defaults to `<input-basename>.docx` in the current directory.
- Always quote paths — they contain spaces (`OneDrive - Microsoft`).
- Use Windows-style backslash paths.

### Example

```powershell
node "C:\Users\kanikamonga\OneDrive - Microsoft\MyProjects\md-to-docx\scripts\md-to-docx.mjs" "C:\Users\kanikamonga\OneDrive - Microsoft\MyProjects\LLD\snapchat-hld-design.md" "C:\Users\kanikamonga\OneDrive - Microsoft\MyProjects\LLD\snapchat-hld-design.docx"
```

## Steps for the agent

1. Confirm the input `.md` path exists.
2. Decide the output path (default: same folder/basename with `.docx`, unless the user specifies one).
3. Run the `node` command above with both paths quoted.
4. Verify the output: check the file exists, is non-zero bytes, and starts with the `PK` magic bytes (valid docx/zip).
5. Report the created file path to the user.

## If dependencies are missing

If Node reports a missing module (`docx` or `marked`), reinstall once:

```powershell
cd "C:\Users\kanikamonga\OneDrive - Microsoft\MyProjects\md-to-docx\scripts"; npm install --no-audit --no-fund
```

## Features

The converter handles:
- **Front-matter** (`title`, `date`, `version`, `audience`) → generates a title page (title split on `—`/`–` into title + subtitle)
- **Table of contents** built from H1–H3 headings
- **PNG image embedding** — `![alt](path.png)` resolved relative to the input `.md`, scaled to fit 6 inches width; missing images become `[Image not found: <path>]`
- **Styled output** — Calibri body, colored headings (`#1F3864`), tables with header fill + alternating row colors, code blocks in Consolas, blockquotes, lists, links, horizontal rules

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Node.js | 18+ |
| `docx` | 9+ (installed) |
| `marked` | 15+ (installed) |
