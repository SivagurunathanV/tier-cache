package tier_cache;

public interface Repository<T> extends AutoCloseable {
    void save(Object id, T obj);
    T findByKey(Object key);
}
