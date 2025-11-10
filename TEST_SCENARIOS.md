# Tier Cache Test Scenarios

This document outlines the comprehensive test scenarios created to validate the tier cache implementation.

## Overview

The tier cache system implements a three-layer caching strategy:

1. **In-Memory Cache** (Caffeine) - Fast access, limited size, configurable TTL
2. **Repository Layer** (DatabaseRepository) - Simulates database with random latency (100ms-1min)
3. **Disk Storage** (RocksDB) - Persistent storage with compression and optional cleanup

## Test Files Created

### 1. CacheStoreTest.java
**Purpose**: Tests the core tier cache behavior and coordination between layers.

**Key Test Scenarios**:
- **Cache Hit Tests**: Validates in-memory cache performance and behavior
- **Repository Tier Tests**: Tests fallback to repository when cache misses
- **Disk Store Tier Tests**: Validates disk storage as final fallback
- **Cache Eviction Tests**: Ensures evicted items are saved to disk store
- **Resource Management Tests**: Tests proper cleanup and resource handling
- **Integration Tests**: Complex multi-tier interaction scenarios
- **Error Handling Tests**: Graceful handling of failures in any tier

### 2. RocksDBDiskStoreTest.java
**Purpose**: Comprehensive testing of the RocksDB-based persistent storage layer.

**Key Test Scenarios**:
- **Basic Operations**: Save/load operations with various data types
- **Batch Operations**: Bulk save/load operations for performance
- **Persistence Tests**: Data survival across database restarts
- **Serialization Tests**: Proper handling of complex objects and edge cases
- **Cleanup Functionality**: Automated cleanup and database maintenance
- **Error Handling**: Robust error recovery and concurrent access
- **Resource Management**: Proper resource cleanup and lifecycle management

### 3. DatabaseRepositoryTest.java
**Purpose**: Tests the repository layer with simulated database latency.

**Key Test Scenarios**:
- **Basic Repository Operations**: CRUD operations with simulated latency
- **Simulated Database Latency**: Validates random delay implementation (100ms-60s)
- **Concurrency Tests**: Thread-safe operations under concurrent access
- **Data Types Tests**: Support for various key/value combinations
- **Resource Management**: Proper cleanup and resource handling
- **Thread Interruption Tests**: Graceful handling of interrupted operations

### 4. TierCacheIntegrationTest.java
**Purpose**: End-to-end integration testing of the complete tier cache system.

**Key Test Scenarios**:
- **Complete Cache Flow**: Tests data flow through all three tiers
- **Cache Eviction and Persistence**: Validates eviction triggers disk persistence
- **Concurrent Access**: Multi-threaded cache access under load
- **Performance Characteristics**: Demonstrates caching performance benefits
- **Data Persistence**: Data survival across cache restarts
- **Error Recovery**: Graceful degradation when components fail

### 5. AppTest.java (Updated)
**Purpose**: Application-level tests and smoke tests for the main entry point.

**Key Test Scenarios**:
- **Config Record Tests**: Validates the Config record implementation
- **Main Method Integration**: Tests the demonstration code in main()
- **Application Smoke Tests**: End-to-end validation of core functionality

## Test Execution

### Running All Tests
```bash
./gradlew test
```

### Running Specific Test Classes
```bash
# Run tier cache behavior tests
./gradlew test --tests "tier_cache.CacheStoreTest"

# Run disk storage tests
./gradlew test --tests "tier_cache.RocksDBDiskStoreTest"

# Run repository tests
./gradlew test --tests "tier_cache.DatabaseRepositoryTest"

# Run integration tests
./gradlew test --tests "tier_cache.TierCacheIntegrationTest"

# Run application tests
./gradlew test --tests "tier_cache.AppTest"
```

### Running the Demonstration
```bash
./gradlew run
```

## Test Coverage Areas

### Functional Testing
- ✅ Cache hit/miss behavior across all tiers
- ✅ Data persistence and retrieval
- ✅ Cache eviction and disk storage
- ✅ Batch operations
- ✅ Complex data type handling
- ✅ Resource lifecycle management

### Performance Testing
- ✅ Cache vs repository vs disk access times
- ✅ Concurrent access patterns
- ✅ Large dataset handling
- ✅ Batch operation efficiency

### Reliability Testing
- ✅ Error handling and recovery
- ✅ Resource cleanup
- ✅ Thread safety
- ✅ Data integrity under concurrent access
- ✅ Graceful degradation

### Integration Testing
- ✅ Multi-tier data flow
- ✅ Component interaction
- ✅ End-to-end scenarios
- ✅ Real-world usage patterns

## Key Validation Points

### Cache Behavior Validation
1. **First Access**: Should hit repository (slow due to simulated latency)
2. **Subsequent Access**: Should hit cache (fast, <1ms typically)
3. **Cache Eviction**: Should automatically save evicted items to disk
4. **Cache Miss**: Should try repository, then disk store, then return null

### Data Persistence Validation
1. **Disk Storage**: Data should survive process restarts
2. **Serialization**: Complex objects should serialize/deserialize correctly
3. **Batch Operations**: Large datasets should be handled efficiently
4. **Cleanup**: Optional cleanup should clear old data

### Error Handling Validation
1. **Repository Failures**: Should gracefully fall back to disk store
2. **Disk Store Failures**: Should handle RocksDB exceptions appropriately
3. **Concurrent Access**: Should remain thread-safe under load
4. **Resource Leaks**: Should properly close all resources

### Performance Validation
1. **Cache Speedup**: Cache hits should be significantly faster than repository access
2. **Scalability**: Should handle thousands of concurrent operations
3. **Memory Usage**: Should respect cache size limits
4. **Disk Efficiency**: Should use compression and optimize storage

## Test Configuration

### Dependencies Added
- `mockito-core:5.8.0` - For mocking in unit tests
- `mockito-junit-jupiter:5.8.0` - JUnit 5 integration for Mockito

### Test Resources
- Tests use temporary directories for RocksDB storage
- Configurable timeouts for long-running operations
- Concurrent test execution for performance validation

## Expected Test Results

When all tests pass, you can be confident that:

1. **The tier cache properly implements the three-tier strategy**
2. **Data flows correctly between cache, repository, and disk layers**
3. **Performance benefits are realized from caching**
4. **The system handles errors gracefully and maintains data integrity**
5. **Resource management is proper with no leaks**
6. **The system scales well under concurrent access**
7. **Data persistence works correctly across restarts**

## Troubleshooting

### Common Issues
- **RocksDB Native Library**: Ensure RocksDB native libraries are available
- **Disk Space**: Tests create temporary databases that require disk space
- **Timing Issues**: Some tests have random delays; timeouts are set generously
- **Concurrent Access**: Tests may take longer on single-core systems

### Debug Tips
- Enable debug logging for more detailed test output
- Check temporary directories if tests fail unexpectedly
- Monitor resource usage during concurrent tests
- Verify RocksDB installation if disk store tests fail