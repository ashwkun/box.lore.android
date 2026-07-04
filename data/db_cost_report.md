# 📊 Turso Database Cost Report (Last 30 Days)

This report tracks the Turso SQLite read and write metrics for the background synchronization pipeline, bifurcated by country and step.

## 📅 Date: 2026-07-04

| Country | Sync Step | DB Reads | DB Writes |
| :--- | :--- | :---: | :---: |
| **`FR`** | `import-pi-data` | 10,817 | 0 |
|  | `populate-charts` | 20,191 | 11,388 |
|  | `sync-episodes` | 14,654 | 84 |
|  | `vectorize` | 11,438 | 84 |
|  | `vectorize-podcasts` | 11,516 | 108 |
| | *Subtotal (FR)* | *68,616* | *11,664* |
| **`GB`** | `import-pi-data` | 14,831 | 87 |
|  | `populate-charts` | 20,144 | 11,010 |
|  | `sync-episodes` | 15,359 | 55 |
|  | `vectorize` | 12,130 | 64 |
|  | `vectorize-podcasts` | 11,952 | 12 |
| | *Subtotal (GB)* | *74,416* | *11,228* |
| **`IN`** | `import-pi-data` | 10,788 | 0 |
|  | `populate-charts` | 4,020 | 0 |
|  | `sync-episodes` | 15,203 | 56 |
|  | `vectorize` | 11,953 | 56 |
|  | `vectorize-podcasts` | 12,108 | 97 |
| | *Subtotal (IN)* | *54,072* | *209* |
| **`US`** | `import-pi-data` | 14,791 | 48 |
|  | `populate-charts` | 4,019 | 0 |
|  | `sync-episodes` | 15,430 | 88 |
|  | `vectorize` | 12,296 | 88 |
|  | `vectorize-podcasts` | 11,988 | 11 |
| | *Subtotal (US)* | *58,524* | *235* |

---

