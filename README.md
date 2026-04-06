# Kitsune

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk&color=437291" alt="Java 21">
  <img src="https://img.shields.io/badge/Paper-1.21-brightgreen?style=flat-square" alt="Paper 1.21+">
  <img src="https://img.shields.io/badge/DJL-AI%2FML-blue?style=flat-square" alt="DJL">
  <img src="https://img.shields.io/badge/HuggingFace-NLP-yellow?style=flat-square" alt="HuggingFace">
</p>

<p align="center">
  <strong>🦊 AI-Powered Semantic Item Search for Minecraft</strong>
</p>

<p align="center">
  Find items using natural language — "building blocks", "red stuff", "tools".
</p>

---

## ✨ Features

- **🧠 Semantic Search** — AI understands item descriptions and categories
- **🔍 Natural Language** — Search with phrases like "building blocks" or "shiny things"
- **📦 Container Indexing** — Automatically indexes chests, barrels, shulker boxes
- **⚡ Real-Time Results** — Instant search across all loaded containers
- **🎮 Simple Commands** — Single `/find` command with intuitive syntax
- **🔧 Auto-Download** — AI model downloads automatically on first run (~50MB)

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|------------|
| **Platform** | Paper 1.21+ |
| **Language** | Java 21 |
| **AI Framework** | DJL (Deep Java Library) 0.31.1 |
| **NLP** | HuggingFace Tokenizers |
| **Build** | Gradle with Shadow plugin |

---

## 🚀 Installation

### Requirements

- Paper server 1.21 or higher
- Java 21+
- ~50MB free space (for AI model download)

### Setup

```bash
# 1. Download latest release
# Download Kitsune-x.x.x.jar from GitHub Releases

# 2. Install plugin
mv Kitsune-x.x.x.jar /path/to/server/plugins/

# 3. Start server
# Model will auto-download on first run

# 4. (Optional) Configure
cd /path/to/server/plugins/Kitsune
vim config.yml
```

---

## 🎮 Usage

### Commands

| Command | Description |
|---------|-------------|
| `/find <query>` | Search for items using natural language |
| `/find reload` | Reload plugin configuration |

### Search Examples

```
/find diamond sword       → Find all diamond swords
/find building blocks     → Find stone, wood, bricks, etc.
/find red                 → Find redstone, red dye, red wool
/find tools               → Find pickaxes, axes, shovels
/find weapons             → Find swords, bows, tridents
/find food                → Find apples, bread, steak
```

### How It Works

```
┌─────────────────────────────────────────────────────────┐
│                    Search Flow                            │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  User: "/find red tools"                                │
│            │                                            │
│            ▼                                            │
│  ┌─────────────────────┐                               │
│  │  Query Embedding    │  ← AI model converts text      │
│  │  Vector Generation  │    to semantic vector           │
│  └──────────┬──────────┘                               │
│             │                                           │
│             ▼                                           │
│  ┌─────────────────────┐                               │
│  │  Similarity Search  │  ← Compare with item vectors  │
│  │  (Cosine Similarity) │    in container index           │
│  └──────────┬──────────┘                               │
│             │                                           │
│             ▼                                           │
│  ┌─────────────────────┐                               │
│  │  Ranked Results     │  → Show nearest matches         │
│  │  (with locations)   │    with container locations     │
│  └─────────────────────┘                               │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 📁 Project Structure

```
Kitsune/
├── api/                    # Public API
├── common/                 # Shared components
│   ├── indexing/          # Container indexing logic
│   ├── vector/            # Embedding vector operations
│   └── search/            # Semantic search engine
├── bukkit/                # Paper/Spigot implementation
│   ├── BukkitPlatform.java
│   ├── KitsuneLoader.java  # Runtime library loading
│   └── indexing/
│       └── BukkitContainerIndexer.java
├── build.gradle.kts        # Multi-module Gradle build
└── settings.gradle.kts
```

---

## 🔧 Configuration

```yaml
# plugins/Kitsune/config.yml

search:
  max-results: 10           # Maximum results per search
  min-similarity: 0.7       # Minimum similarity threshold (0-1)
  
indexing:
  auto-index: true         # Automatically index new containers
  chunk-radius: 3          # Index radius around player (chunks)
  
model:
  auto-download: true      # Auto-download AI model
  cache-path: "models/"    # Local model cache directory
```

---

## 🔌 Developer API

```java
// Get API instance
KitsuneAPI api = KitsuneProvider.get();

// Perform semantic search
List<SearchResult> results = api.search("wooden building blocks");

// Get container at location
Container container = api.getContainer(location);

// Index manually
api.indexContainer(container);
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test on Paper 1.21+
5. Submit a Pull Request

---

## 📄 License

[MIT License](LICENSE)

---

<p align="center">
  Find anything, just by describing it 🦊
</p>
