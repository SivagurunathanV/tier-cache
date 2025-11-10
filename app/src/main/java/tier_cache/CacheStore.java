package tier_cache;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class CacheStore<K, V, T> implements AutoCloseable {
    private Cache<K, V> cache;
    private Diskstore diskStore;
    private Repository<T> repository;

    public CacheStore(Diskstore diskStore, Repository<T> repository, long maxSize) {
        this.diskStore = diskStore;
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofMinutes(15L))
                .evictionListener((key, value, cause) ->  {
                    this.diskStore.save(key, value);
                })
                .build();
    }

    // returns null if the key not present in cache, database repo and disk
    @SuppressWarnings("unchecked")
    public V get(K key) {
        V val = this.cache.getIfPresent(key);
        if(val != null) {
            return val;
        }

        try {
            val = (V) repository.findByKey(key);
            if (val != null) {
                this.cache.put(key, val);
                return val;
            }
        } catch (Exception e) {
            // not found
        }

        // loading from DB fail, fallback to if key exists in diskStorage
        try {
            val = (V) diskStore.load(key);
            if (val != null) {
                this.cache.put(key, val);
                return val;
            }
        } catch (Exception e) {
            // failed to load from disk
        }

        return val;
    }

    public void put(K key, V val) {
        cache.put(key, val);
    }

    @Override
    public void close() {
        this.cache.cleanUp();
    }
}
