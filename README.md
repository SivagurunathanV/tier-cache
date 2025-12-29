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

## Benchmark Results

╔════════════════════════════════════════════════╗
║           BENCHMARK RESULTS SUMMARY            ║
╚════════════════════════════════════════════════╝

## 1. LONG OUTAGE RESILIENCE (~25-min DB outage)
┌─────────────────────────┬────────┬────────┬────────┬────────┐
│ Strategy                │  3 min │ 5 min │ 7 min │ 10 min │
├─────────────────────────┼────────┼────────┼────────┼────────┤
│ TierCache (Caffeine+RocksDB) │ 100.0% │ 100.0% │ 100.0% │ 100.0% │
│ EhCache with Disk       │ 100.0% │   0.0% │   0.0% │   0.0% │
│ Caffeine Only (Baseline) │ 100.0% │   0.0% │   0.0% │   0.0% │
└─────────────────────────┴────────┴────────┴────────┴────────┘

## 2. NORMAL OPERATION PERFORMANCE
┌─────────────────────────┬──────────────┬──────────────┐
│ Strategy                │ Cache Hit    │ Cache Miss   │
├─────────────────────────┼──────────────┼──────────────┤
│ TierCache (Caffeine+RocksDB) │      2.50 μs │     19.11 μs │
│ EhCache with Disk       │      6.31 μs │  12042.11 μs │
│ Caffeine Only (Baseline) │      2.74 μs │  12022.38 μs │
└─────────────────────────┴──────────────┴──────────────┘

## 3. MEMORY PRESSURE (50K writes, 10K cache size)
┌─────────────────────────┬──────────────┬──────────────┐
│ Strategy                │ Total Time   │ Throughput   │
├─────────────────────────┼──────────────┼──────────────┤
│ TierCache (Caffeine+RocksDB) │       140 ms │    357143 op/s │
│ EhCache with Disk       │       201 ms │    248756 op/s │
│ Caffeine Only (Baseline) │        37 ms │   1351351 op/s │
└─────────────────────────┴──────────────┴──────────────┘

## 4. RECOMMENDATIONS

🏆 BEST FOR LONG OUTAGES: TierCache (Caffeine+RocksDB)
   → Maintains 100.0% availability after 25 minutes

⚡ FASTEST PERFORMANCE: TierCache (Caffeine+RocksDB)
   → 2.50 μs average latency
