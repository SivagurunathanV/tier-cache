package tier_cache.benchmark;

import tier_cache.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class TierCacheStrategy implements CacheStrategy {
    private CacheStore<String, String, String> cacheStore;
    private DatabaseRepository<String> repository;
    private RocksDBDiskStore diskStore;
    private CacheMetrics metrics = new CacheMetrics();

    public TierCacheStrategy() throws Exception {
        // RocksDBDiskStore(path, retentionDays, cleanupDuration, unit)
        diskStore = new RocksDBDiskStore(
            "./benchmark-tier-cache",
            7,              // retentionDays
            1,              // cleanupDuration (0 = disabled)
            TimeUnit.HOURS  // unit
        );
        
        repository = new DatabaseRepository<String>(0, 1);
        
        // CacheStore(diskStore, repository, maxSize)
        cacheStore = new CacheStore<>(diskStore, repository, 10_000, Duration.ofMinutes(5));
    }

    @Override
    public String getName() { 
        return "TierCache (Caffeine+RocksDB)"; 
    }

    @Override
    public String get(String key) {
        long start = System.nanoTime();
        var value = cacheStore.get(key);
        metrics.readLatenciesNs.add(System.nanoTime() - start);
        return value;
    }

    @Override
    public void put(String key, String value) {
        cacheStore.put(key, value);
        repository.save(key, value);
        // System.out.println("Saved both");
    }

    @Override
    public void simulateOutage() { 
        repository.simulateOutage();
    }

    @Override
    public void restoreDatabase() { 
        repository.restoreDatabase();
    }

    @Override
    public CacheMetrics getMetrics() { 
        return metrics; 
    }

    @Override
    public void close() {
        try {
            cacheStore.close();
            diskStore.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}