package tier_cache.benchmark;

import java.time.Duration;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class CaffeineOnlyStrategy implements CacheStrategy {
    private Cache<String, String> cache;
    private CacheMetrics metrics = new CacheMetrics();
    private boolean dbAvailable = true;

    public CaffeineOnlyStrategy() {
        cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
    }

    @Override
    public String getName() { return "Caffeine Only (Baseline)"; }

    @Override
    public String get(String key) {
        long start = System.nanoTime();
        String value = cache.getIfPresent(key);
        
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
        cache.invalidateAll();
    }
}