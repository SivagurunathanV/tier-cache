package tier_cache.benchmark;

public interface CacheStrategy {
    String getName();
    void put(String key, String value);
    String get(String key);
    void simulateOutage();      // Mark DB as unavailable
    void restoreDatabase();     // Mark DB as available
    CacheMetrics getMetrics();
    void close();
}