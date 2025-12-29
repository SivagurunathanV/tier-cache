package tier_cache.benchmark;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkRunner {
    
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║   TIER-CACHE BENCHMARK COMPARISON              ║");
        System.out.println("╚════════════════════════════════════════════════╝");
        
        // Initialize strategies
        CacheStrategy[] strategies = {
            new TierCacheStrategy(),
            new EhCacheDiskStrategy(),
            new CaffeineOnlyStrategy()
        };
        
        List<OutageBenchmark.OutageResults> outageResults = new ArrayList<>();
        List<OutageBenchmark.PerformanceResults> perfResults = new ArrayList<>();
        List<OutageBenchmark.PressureResults> pressureResults = new ArrayList<>();
        
        // Run benchmarks
        for (CacheStrategy strategy : strategies) {
            outageResults.add(OutageBenchmark.testLongOutage(strategy));
            perfResults.add(OutageBenchmark.testNormalPerformance(strategy));
            pressureResults.add(OutageBenchmark.testMemoryPressure(strategy));
            strategy.close();
        }
        
        // Generate report
        printReport(outageResults, perfResults, pressureResults);
    }
    
    private static void printReport(
            List<OutageBenchmark.OutageResults> outage,
            List<OutageBenchmark.PerformanceResults> perf,
            List<OutageBenchmark.PressureResults> pressure
        ) {
        
        System.out.println("\n\n");
        System.out.println("╔════════════════════════════════════════════════╗");
        System.out.println("║           BENCHMARK RESULTS SUMMARY            ║");
        System.out.println("╚════════════════════════════════════════════════╝");
        
        // 1. OUTAGE RESILIENCE (Most Important)
        System.out.println("\n## 1. LONG OUTAGE RESILIENCE (30-min DB outage)");
        System.out.println("┌─────────────────────────┬────────┬────────┬────────┬────────┐");
        System.out.println("│ Strategy                │  3 min │ 5 min │ 7 min │ 10 min │");
        System.out.println("├─────────────────────────┼────────┼────────┼────────┼────────┤");
        
        for (var result : outage) {
            System.out.printf("│ %-23s │ %5.1f%% │ %5.1f%% │ %5.1f%% │ %5.1f%% │\n",
                truncate(result.strategyName, 23),
                result.checkpoints.get(3) / 10.0,
                result.checkpoints.get(5) / 10.0,
                result.checkpoints.get(7) / 10.0,
                result.checkpoints.get(10) / 10.0);
        }
        System.out.println("└─────────────────────────┴────────┴────────┴────────┴────────┘");
        
        // 2. NORMAL PERFORMANCE
        System.out.println("\n## 2. NORMAL OPERATION PERFORMANCE");
        System.out.println("┌─────────────────────────┬──────────────┬──────────────┐");
        System.out.println("│ Strategy                │ Cache Hit    │ Cache Miss   │");
        System.out.println("├─────────────────────────┼──────────────┼──────────────┤");
        
        for (var result : perf) {
            System.out.printf("│ %-23s │ %9.2f μs │ %9.2f μs │\n",
                truncate(result.strategyName, 23),
                result.avgHitLatencyNs / 1000.0,
                result.avgMissLatencyNs / 1000.0);
        }
        System.out.println("└─────────────────────────┴──────────────┴──────────────┘");
        
        // 3. MEMORY PRESSURE
        System.out.println("\n## 3. MEMORY PRESSURE (50K writes, 10K cache size)");
        System.out.println("┌─────────────────────────┬──────────────┬──────────────┐");
        System.out.println("│ Strategy                │ Total Time   │ Throughput   │");
        System.out.println("├─────────────────────────┼──────────────┼──────────────┤");
        
        for (var result : pressure) {
            System.out.printf("│ %-23s │ %9.0f ms │ %9.0f op/s │\n",
                truncate(result.strategyName, 23),
                (double) result.writeTimeMs,  // Cast to double
                result.throughput);
        }
        System.out.println("└─────────────────────────┴──────────────┴──────────────┘");
        
        // 4. RECOMMENDATIONS
        printRecommendations(outage, perf, pressure);
    }
    
    private static void printRecommendations(
            List<OutageBenchmark.OutageResults> outage,
            List<OutageBenchmark.PerformanceResults> perf,
            List<OutageBenchmark.PressureResults> pressure
            ) {
        
        System.out.println("\n## 4. RECOMMENDATIONS\n");
        
        // Find best for outage resilience
        var bestOutage = outage.stream()
            .max((a, b) -> Integer.compare(a.checkpoints.get(10), b.checkpoints.get(10)))
            .get();
        
        System.out.println("🏆 BEST FOR LONG OUTAGES: " + bestOutage.strategyName);
        System.out.println("   → Maintains " + (bestOutage.checkpoints.get(10) / 10.0) + 
                         "% availability after 25 minutes\n");
        
        // Find fastest
        var fastest = perf.stream()
            .min((a, b) -> Long.compare(a.avgHitLatencyNs, b.avgHitLatencyNs))
            .get();
        
        System.out.println("⚡ FASTEST PERFORMANCE: " + fastest.strategyName);
        System.out.printf("   → %.2f μs average latency\n\n", fastest.avgHitLatencyNs / 1000.0);
    }
    
    private static String truncate(String str, int maxLen) {
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}