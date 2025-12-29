package tier_cache.benchmark;

import java.time.Duration;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.*;
import org.ehcache.config.units.*;

public class EhCacheDiskStrategy implements CacheStrategy {
    private CacheManager cacheManager;
    private Cache<String, String> cache;
    private CacheMetrics metrics = new CacheMetrics();
    private boolean dbAvailable = true;

    public EhCacheDiskStrategy() {
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
            .with(CacheManagerBuilder.persistence("./benchmark-ehcache"))
            .withCache("benchmark-cache",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    String.class, String.class,
                    ResourcePoolsBuilder.newResourcePoolsBuilder()
                        .heap(10_000, EntryUnit.ENTRIES)
                        .disk(100, MemoryUnit.MB, true))
                .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(5)))
                .build())
            .build(true);
        
        cache = cacheManager.getCache("benchmark-cache", String.class, String.class);
    }

    @Override
    public String getName() { return "EhCache with Disk"; }

    @Override
    public String get(String key) {
        long start = System.nanoTime();
        String value = cache.get(key);
        
        if (value == null && dbAvailable) {
            value = loadFromDb(key);
            if (value != null) {
                cache.put(key, value);
                metrics.dbCalls++;
            }
        }
        
        if (value != null) metrics.cacheHits++;
        else metrics.failedReads++;
        
        metrics.readLatenciesNs.add(System.nanoTime() - start);
        return value;
    }

    @Override
    public void put(String key, String value) {
        cache.put(key, value);
    }

    private String loadFromDb(String key) {
        if (!dbAvailable) return null;
        // Simulate DB load
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        return "db-value-" + key;
    }

    @Override
    public void simulateOutage() { dbAvailable = false; }

    @Override
    public void restoreDatabase() { dbAvailable = true; }

    @Override
    public CacheMetrics getMetrics() { return metrics; }

    @Override
    public void close() {
        cacheManager.close();
    }
}