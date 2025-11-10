/*
 * TierCache - A three-tier caching library
 * Provides in-memory -> repository -> disk store caching
 */
package tier_cache;

import java.util.concurrent.TimeUnit;
import org.rocksdb.RocksDBException;

/**
 * TierCache - A standalone three-tier caching library.
 *
 * Usage:
 *   TierCache<String, MyData, MyData> cache = TierCache.builder()
 *       .diskStorePath("/path/to/disk")
 *       .maxCacheSize(1000)
 *       .build();
 *
 *   MyData data = cache.get("key");
 *   cache.put("key", data);
 */
public class App {

    public String getGreeting() {
        return "TierCache Library v1.0";
    }

    public record Config(String key, String value) {}

    // Nested TierCache class for the library functionality
    public static class TierCache<K, V, T> implements AutoCloseable {

        private final CacheStore<K, V, T> cacheStore;
        private final RocksDBDiskStore diskStore;
        private final Repository<T> repository;

    private TierCache(RocksDBDiskStore diskStore, Repository<T> repository, long maxCacheSize) {
        this.diskStore = diskStore;
        this.repository = repository;
        this.cacheStore = new CacheStore<>(diskStore, repository, maxCacheSize);
    }

    /**
     * Get a value from the three-tier cache.
     * Checks: in-memory cache -> repository -> disk store
     */
    public V get(K key) {
        return cacheStore.get(key);
    }

    /**
     * Put a value directly into the repository and cache
     */
    public void put(K key, V value) {
        cacheStore.put(key, value);
    }

    @Override
    public void close() {
        if (cacheStore != null) {
            cacheStore.close();
        }
        if (repository != null) {
            try {
                repository.close();
            } catch (Exception e) {
                // Log and ignore close errors
                System.err.println("Warning: Error closing repository: " + e.getMessage());
            }
        }
        if (diskStore != null) {
            diskStore.close();
        }
    }

    /**
     * Create a TierCache builder
     */
    public static <K, V, T> Builder<K, V, T> builder() {
        return new Builder<>();
    }

    public static class Builder<K, V, T> {
        private String diskStorePath = "./tier_cache_db";
        private long maxCacheSize = 1000L;
        private long retentionDays = 7L;
        private Repository<T> repository;

        public Builder<K, V, T> diskStorePath(String path) {
            this.diskStorePath = path;
            return this;
        }

        public Builder<K, V, T> maxCacheSize(long size) {
            this.maxCacheSize = size;
            return this;
        }

        public Builder<K, V, T> retentionDays(long days) {
            this.retentionDays = days;
            return this;
        }

        public Builder<K, V, T> repository(Repository<T> repository) {
            this.repository = repository;
            return this;
        }

        public TierCache<K, V, T> build() throws RocksDBException {
            RocksDBDiskStore diskStore = new RocksDBDiskStore(diskStorePath, retentionDays, 0, TimeUnit.SECONDS);
            Repository<T> repo = repository != null ? repository : new DatabaseRepository<>();
            return new TierCache<>(diskStore, repo, maxCacheSize);
        }
    }
    }

    // Keep for backward compatibility / demonstration
    public static void main(String[] args) {
        System.out.println("TierCache Library v1.0");
        System.out.println("Usage: Create TierCache instances using App.TierCache.builder()");

        // Example usage
        try (TierCache<String, App.Config, App.Config> cache = TierCache.<String, App.Config, App.Config>builder()
                .diskStorePath("./demo_cache")
                .maxCacheSize(100)
                .build()) {

            System.out.println("Demo TierCache created successfully!");

            // Demo operations
            App.Config testConfig = new App.Config("demo.key", "demo.value");
            cache.put("test", testConfig);

            App.Config retrieved = cache.get("test");
            System.out.println("Retrieved: " + retrieved);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
