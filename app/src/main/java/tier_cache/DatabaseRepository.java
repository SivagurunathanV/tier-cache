package tier_cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public class DatabaseRepository<T> implements Repository<T> {

    private Map<Object, T> database;
    private final long minDelayMs;
    private final long maxDelayMs;
    private boolean isAvailable = true;

    public DatabaseRepository() {
        this(100, 1000); // Default: 100ms to 1 second delay
    }

    public DatabaseRepository(long minDelayMs, long maxDelayMs) {
        this.database = new ConcurrentHashMap<>();
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    @Override
    public T findByKey(Object key) {
        if (!isAvailable) {
            return null; // Changed from throw exception
        }
        return executeWithDelay(() -> database.get(key));
    }

    @Override
    public void save(Object id, T obj) {
        executeWithDelay(() -> {
            if (obj == null) {
                database.remove(id);
                return null;
            }
            return database.put(id, obj);
        });
    }

    // sleep for random delay to simulate database latency
    private <R> R executeWithDelay(Supplier<R> action) {
        try {
            long msToSleep = ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs);
            Thread.sleep(msToSleep);
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed", e);
        }
    }

    @Override
    public void close() {
        this.database.clear();
    }

    public void simulateOutage() {
        isAvailable = false;
    }

    public void restoreDatabase() {
        isAvailable = true;
    }
    
}
