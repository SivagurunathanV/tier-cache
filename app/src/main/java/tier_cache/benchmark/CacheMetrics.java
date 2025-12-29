package tier_cache.benchmark;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CacheMetrics {
    public long cacheHits = 0;
    public long cacheMisses = 0;
    public long diskHits = 0;
    public long dbCalls = 0;
    public long failedReads = 0;
    public List<Long> readLatenciesNs = new CopyOnWriteArrayList<>();
    
    public double getHitRate() {
        long total = cacheHits + cacheMisses;
        return total == 0 ? 0 : (cacheHits * 100.0) / total;
    }
    
    public double getAvgLatencyUs() {
        return readLatenciesNs.isEmpty() ? 0 : 
            readLatenciesNs.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
    }
}