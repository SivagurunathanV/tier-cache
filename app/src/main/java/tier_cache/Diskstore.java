package tier_cache;

import java.util.List;
import java.util.Map;

public interface Diskstore extends AutoCloseable {
    void save(Object key, Object val);
    void saveBatch(Map<Object, Object> entries);
    Object load(Object key);
    Map<Object, Object> loadBatch(List<Object> keys);
}
