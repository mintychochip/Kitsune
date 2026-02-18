# Kitsune - Semantic Search

Find all your lost items

## Showcase

<p align="center">
  <img src="docs/images/search-diamond.png" alt="Diamond search" width="280">
  <img src="docs/images/search-red.png" alt="Red items search" width="280">
  <img src="docs/images/search-nether.png" alt="Nether items search" width="280">
</p>

## Requirements

- Paper 1.21+
- Java 21+

## Installation

1. Download latest release
2. Place in `plugins/` folder
3. Start server - model auto-downloads on first run
4. Configure `plugins/ChestFind/config.yml` as needed

## Usage

```
/find <query>    Search containers
/find reload     Reload config
```

**Examples**:
- `/find diamond sword` - Find diamond swords
- `/find building blocks` - Find stone, wood, etc.
- `/find red` - Find red items (redstone, red dye, etc.)

## License

[MIT](LICENSE)
