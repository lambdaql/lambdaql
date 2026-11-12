# lambdaQL documentation

## User manual

| Language | Manual |
|---|---|
| English | [manual/en/README.md](manual/en/README.md) |
| 한국어 (Korean) | [manual/ko/README.md](manual/ko/README.md) |
| 日本語 (Japanese) | [manual/ja/README.md](manual/ja/README.md) |
| 简体中文 (Simplified Chinese) | [manual/zh/README.md](manual/zh/README.md) |

The English manual is the source. The other languages are translations of it.
Each manual has a **Java track** and a **Kotlin track** (`java/`, `kotlin/`) with the same chapters, plus common pages for both languages.
If a translation is wrong or old, please open an issue or a pull request. Help is welcome.

## Design documents (for contributors, Korean)

| Document | What it has |
|---|---|
| [design/ROADMAP.md](design/ROADMAP.md) | Current status, design decisions (D1–D34), rules, milestones M0–M12 |
| [design/KOTLIN.md](design/KOTLIN.md) | Kotlin support design: bytecode patterns, rules, limits, operators |
| [../DEVNOTE.md](../DEVNOTE.md) | Hand-over notes for developers: environment, how to verify, pitfalls |

## Folder layout

```
docs/
├── README.md            this page
├── manual/
│   ├── en/              English manual (source): README.md, common pages, java/, kotlin/
│   ├── ko/              Korean
│   ├── ja/              Japanese
│   └── zh/              Simplified Chinese
└── design/              design documents (Korean)
    ├── ROADMAP.md
    └── KOTLIN.md
```

Every manual folder has the same file names, so you can switch language by changing the folder name in the link.
