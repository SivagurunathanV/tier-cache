# Tier Cache

A resilient three-tier caching system designed to **withstand long-duration database outages** while maintaining service availability through in-memory cache, repository layer, and persistent disk storage.

## Architecture

```
┌─────────────────┐
│  In-Memory      │  Fast, limited size, configurable TTL (Caffeine)
│  Cache          │
└────────┬────────┘
         ↓ miss
┌─────────────────┐
│  Repository     │  Simulated database (100ms-1min latency)
│  Layer          │
└────────┬────────┘
         ↓ miss
┌─────────────────┐
│  Disk Storage   │  Persistent storage (RocksDB, compressed)
│  (RocksDB)      │
└─────────────────┘
```

## Quick Start

```bash
# Run tests
./gradlew test

# Run demonstration
./gradlew run
```

## Test Suites

| Test File | Purpose |
|-----------|---------|
| `CacheStoreTest` | Core tier cache behavior and coordination |
| `RocksDBDiskStoreTest` | Persistent storage operations |
| `DatabaseRepositoryTest` | Repository layer with simulated latency |
| `TierCacheIntegrationTest` | End-to-end integration scenarios |
| `AppTest` | Application-level smoke tests |

### Run Specific Tests

```bash
./gradlew test --tests "tier_cache.CacheStoreTest"
./gradlew test --tests "tier_cache.RocksDBDiskStoreTest"
./gradlew test --tests "tier_cache.TierCacheIntegrationTest"
```

## Key Features

- **Outage Resilience**: Continue serving cached data during extended database outages
- **Fast Access**: Cache hits typically <1ms
- **Automatic Persistence**: Evicted items saved to disk for outage recovery
- **Thread-Safe**: Handles concurrent access
- **Graceful Degradation**: Falls back through tiers on failure
- **Compressed Storage**: RocksDB with compression enabled
- **Configurable**: TTL, cache size, cleanup options

## Data Flow

1. **First Access**: Repository → slow (simulated latency)
2. **Subsequent**: Cache → fast (<1ms)
3. **Cache Full**: Evicted items → disk storage
4. **Cache Miss**: Repository → disk → null
5. **Database Outage**: Cache → disk → continues serving stale data

## Dependencies

- Caffeine (in-memory cache)
- RocksDB (persistent storage)
- Mockito (testing)

## Test Coverage

✅ Cache hit/miss behavior  
✅ Multi-tier data flow  
✅ Concurrent access patterns  
✅ Error handling & recovery  
✅ Data persistence  
✅ Resource management  

## Troubleshooting

- **RocksDB errors**: Ensure native libraries are installed
- **Slow tests**: Random delays (100ms-60s) are intentional
- **Disk space**: Tests create temporary databases

## Performance Validation

Cache speedup demonstrated in integration tests:
- Cache hits: <1ms
- Repository access: 100ms-60s (simulated)
- Disk access: 1-10ms typical
