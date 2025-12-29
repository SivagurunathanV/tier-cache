package tier_cache.benchmark;

import java.util.LinkedHashMap;
import java.util.Map;

public class OutageBenchmark {
    
    // Scenario 1: Long Outage Resilience (PRIMARY TEST)
    public static OutageResults testLongOutage(CacheStrategy strategy) throws Exception {
        System.out.println("\n=== Testing: " + strategy.getName() + " ===");
        OutageResults results = new OutageResults(strategy.getName());
        
        // Phase 1: Load 10K items into cache
        System.out.println("Loading 10,000 items...");
        for (int i = 0; i < 10_000; i++) {
            strategy.put("key-" + i, "value-" + i);
        }
        
        // Phase 2: Simulate outage, test at intervals
        System.out.println("Simulating outage...");
        strategy.simulateOutage();
        
        int[] checkpoints = {3, 5, 7, 10}; // minutes
        for (int minutes : checkpoints) {
            Thread.sleep(minutes * 60 * 1000); // Wait
            
            int successful = 0;
            for (int i = 0; i < 1000; i++) {
                String value = strategy.get("key-" + i);
                if (value != null) successful++;
            }
            
            results.addCheckpoint(minutes, successful);
            System.out.printf("After %d min: %d/1000 items available (%.1f%%)\n", 
                minutes, successful, successful / 10.0);
        }
        
        strategy.restoreDatabase();
        return results;
    }
    
    // Scenario 2: Normal Performance
    public static PerformanceResults testNormalPerformance(CacheStrategy strategy) {
        System.out.println("\n=== Performance: " + strategy.getName() + " ===");
        PerformanceResults results = new PerformanceResults(strategy.getName());
        
        // Load items
        for (int i = 0; i < 10_000; i++) {
            strategy.put("key-" + i, "value-" + i);
        }
        
        // Test cache hits (warm cache)
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            strategy.get("key-" + i);
        }
        results.avgHitLatencyNs = (System.nanoTime() - start) / 10_000;
        
        // Test cache misses
        start = System.nanoTime();
        for (int i = 10_000; i < 11_000; i++) {
            strategy.get("key-" + i);
        }
        results.avgMissLatencyNs = (System.nanoTime() - start) / 1_000;
        
        results.metrics = strategy.getMetrics();
        return results;
    }
    
    // Scenario 3: Memory Pressure
    public static PressureResults testMemoryPressure(CacheStrategy strategy) {
        System.out.println("\n=== Memory Pressure: " + strategy.getName() + " ===");
        PressureResults results = new PressureResults(strategy.getName());
        
        long start = System.nanoTime();
        
        // Write 50K items (5x cache size)
        for (int i = 0; i < 50_000; i++) {
            strategy.put("key-" + i, "value-" + i);
        }
        
        results.writeTimeMs = (System.nanoTime() - start) / 1_000_000;
        results.throughput = 50_000.0 / (results.writeTimeMs / 1000.0);
        
        return results;
    }
    
    // Result classes
    public static class OutageResults {
        String strategyName;
        Map<Integer, Integer> checkpoints = new LinkedHashMap<>();
        
        public OutageResults(String name) { this.strategyName = name; }
        public void addCheckpoint(int minutes, int successCount) {
            checkpoints.put(minutes, successCount);
        }
    }
    
    public static class PerformanceResults {
        String strategyName;
        long avgHitLatencyNs;
        long avgMissLatencyNs;
        CacheMetrics metrics;
        
        public PerformanceResults(String name) { this.strategyName = name; }
    }
    
    public static class PressureResults {
        String strategyName;
        long writeTimeMs;
        double throughput;
        
        public PressureResults(String name) { this.strategyName = name; }
    }
}