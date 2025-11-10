package tier_cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDBException;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for RocksDBDiskStore implementation.
 * Tests serialization, persistence, batch operations, and cleanup functionality.
 */
class RocksDBDiskStoreTest {

    @TempDir
    Path tempDir;

    private RocksDBDiskStore diskStore;

    @BeforeEach
    void setUp() throws RocksDBException {
        String dbPath = tempDir.resolve("test-rocksdb").toString();
        // Create diskstore without cleanup for most tests
        diskStore = new RocksDBDiskStore(dbPath, 1, 0, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (diskStore != null) {
            diskStore.close();
        }
    }

    @Nested
    @DisplayName("Basic Save and Load Operations")
    class BasicOperationsTests {

        @Test
        @DisplayName("Should save and load string values")
        void shouldSaveAndLoadStrings() {
            // Given
            String key = "test-key";
            String value = "test-value";

            // When
            diskStore.save(key, value);
            Object result = diskStore.load(key);

            // Then
            assertEquals(value, result);
        }

        @Test
        @DisplayName("Should save and load integer values")
        void shouldSaveAndLoadIntegers() {
            // Given
            String key = "int-key";
            Integer value = 42;

            // When
            diskStore.save(key, value);
            Object result = diskStore.load(key);

            // Then
            assertEquals(value, result);
        }

        @Test
        @DisplayName("Should save and load complex objects")
        void shouldSaveAndLoadComplexObjects() {
            // Given
            String key = "config-key";
            App.Config config = new App.Config("database.url", "jdbc:postgresql://localhost:5432/testdb");

            // When
            diskStore.save(key, config);
            Object result = diskStore.load(key);

            // Then
            assertInstanceOf(App.Config.class, result);
            App.Config loadedConfig = (App.Config) result;
            assertEquals(config.key(), loadedConfig.key());
            assertEquals(config.value(), loadedConfig.value());
        }

        @Test
        @DisplayName("Should return null for non-existent keys")
        void shouldReturnNullForNonExistentKeys() {
            // When
            Object result = diskStore.load("non-existent-key");

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle null values")
        void shouldHandleNullValues() {
            // Given
            String key = "null-key";

            // When
            diskStore.save(key, null);
            Object result = diskStore.load(key);

            // Then
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Batch Operations")
    class BatchOperationsTests {

        @Test
        @DisplayName("Should save and load multiple entries in batch")
        void shouldSaveAndLoadBatch() {
            // Given
            Map<Object, Object> entries = new HashMap<>();
            entries.put("key1", "value1");
            entries.put("key2", 123);
            entries.put("key3", new App.Config("batch.test", "true"));

            // When
            diskStore.saveBatch(entries);

            List<Object> keys = Arrays.asList("key1", "key2", "key3");
            Map<Object, Object> results = diskStore.loadBatch(keys);

            // Then
            assertEquals(3, results.size());
            assertEquals("value1", results.get("key1"));
            assertEquals(123, results.get("key2"));

            App.Config loadedConfig = (App.Config) results.get("key3");
            assertEquals("batch.test", loadedConfig.key());
            assertEquals("true", loadedConfig.value());
        }

        @Test
        @DisplayName("Should handle empty batch operations")
        void shouldHandleEmptyBatchOperations() {
            // Given
            Map<Object, Object> emptyEntries = new HashMap<>();
            List<Object> emptyKeys = Arrays.asList();

            // When/Then
            assertDoesNotThrow(() -> diskStore.saveBatch(emptyEntries));
            assertDoesNotThrow(() -> {
                Map<Object, Object> results = diskStore.loadBatch(emptyKeys);
                assertTrue(results.isEmpty());
            });
        }

        @Test
        @DisplayName("Should handle batch load with missing keys")
        void shouldHandleBatchLoadWithMissingKeys() {
            // Given
            diskStore.save("existing-key", "existing-value");
            List<Object> keys = Arrays.asList("existing-key", "missing-key");

            // When
            Map<Object, Object> results = diskStore.loadBatch(keys);

            // Then
            assertEquals(1, results.size());
            assertEquals("existing-value", results.get("existing-key"));
            assertFalse(results.containsKey("missing-key"));
        }
    }

    @Nested
    @DisplayName("Persistence Tests")
    class PersistenceTests {

        @Test
        @DisplayName("Should persist data across database reopens")
        void shouldPersistDataAcrossReopens() throws RocksDBException {
            // Given
            String key = "persistent-key";
            String value = "persistent-value";
            String dbPath = tempDir.resolve("persistence-test").toString();

            // When: Save data and close
            try (RocksDBDiskStore firstInstance = new RocksDBDiskStore(dbPath, 1, 0, TimeUnit.SECONDS)) {
                firstInstance.save(key, value);
            }

            // Reopen database and load
            try (RocksDBDiskStore secondInstance = new RocksDBDiskStore(dbPath, 1, 0, TimeUnit.SECONDS)) {
                Object result = secondInstance.load(key);

                // Then
                assertEquals(value, result);
            }
        }

        @Test
        @DisplayName("Should handle large data sets")
        void shouldHandleLargeDataSets() {
            // Given: Large dataset
            Map<Object, Object> largeDataset = new HashMap<>();
            for (int i = 0; i < 1000; i++) {
                largeDataset.put("key" + i, "value" + i);
            }

            // When
            diskStore.saveBatch(largeDataset);

            // Then: Verify random sampling of data
            assertEquals("value0", diskStore.load("key0"));
            assertEquals("value500", diskStore.load("key500"));
            assertEquals("value999", diskStore.load("key999"));
        }
    }

    @Nested
    @DisplayName("Serialization Tests")
    class SerializationTests {

        @Test
        @DisplayName("Should handle various data types")
        void shouldHandleVariousDataTypes() {
            // Given: Different data types
            diskStore.save("string", "test string");
            diskStore.save("integer", 42);
            diskStore.save("long", 123456789L);
            diskStore.save("double", 3.14159);
            diskStore.save("boolean", true);

            // When/Then
            assertEquals("test string", diskStore.load("string"));
            assertEquals(42, diskStore.load("integer"));
            assertEquals(123456789L, diskStore.load("long"));
            assertEquals(3.14159, diskStore.load("double"));
            assertEquals(true, diskStore.load("boolean"));
        }

        @Test
        @DisplayName("Should handle special characters in strings")
        void shouldHandleSpecialCharacters() {
            // Given
            String key = "special-chars";
            String value = "Special chars: åäö ñ 中文 🚀 \n\t\r";

            // When
            diskStore.save(key, value);
            Object result = diskStore.load(key);

            // Then
            assertEquals(value, result);
        }

        @Test
        @DisplayName("Should handle large strings")
        void shouldHandleLargeStrings() {
            // Given: Create large string (1MB)
            StringBuilder sb = new StringBuilder();
            String pattern = "This is a test string for large data handling. ";
            while (sb.length() < 1024 * 1024) { // 1MB
                sb.append(pattern);
            }
            String largeValue = sb.toString();

            // When
            diskStore.save("large-string", largeValue);
            Object result = diskStore.load("large-string");

            // Then
            assertEquals(largeValue, result);
        }
    }

    @Nested
    @DisplayName("Cleanup Functionality Tests")
    class CleanupTests {

        @Test
        @DisplayName("Should perform cleanup when configured")
        void shouldPerformCleanupWhenConfigured() throws RocksDBException, InterruptedException {
            // Given: DiskStore with cleanup enabled (short interval for testing)
            String dbPath = tempDir.resolve("cleanup-test").toString();
            try (RocksDBDiskStore cleanupDiskStore = new RocksDBDiskStore(dbPath, 1, 100, TimeUnit.MILLISECONDS)) {

                // Save some data
                cleanupDiskStore.save("cleanup-key", "cleanup-value");
                assertEquals("cleanup-value", cleanupDiskStore.load("cleanup-key"));

                // Wait for cleanup to occur
                Thread.sleep(200);

                // After cleanup, data should be gone (database is destroyed and recreated)
                Object result = cleanupDiskStore.load("cleanup-key");
                assertNull(result);
            }
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should create database directory if missing")
        void shouldCreateDatabaseDirectoryIfMissing() throws RocksDBException {
            // Given: Non-existent directory path
            String nonExistentPath = tempDir.resolve("non-existent/deep/path").toString();

            // When/Then: Should create directory and not throw exception
            assertDoesNotThrow(() -> {
                try (RocksDBDiskStore store = new RocksDBDiskStore(nonExistentPath, 1, 0, TimeUnit.SECONDS)) {
                    store.save("test", "value");
                    assertEquals("value", store.load("test"));
                }
            });
        }

        @Test
        @DisplayName("Should handle concurrent access")
        void shouldHandleConcurrentAccess() throws InterruptedException {
            // Given: Multiple threads accessing the same store
            int numThreads = 10;
            int itemsPerThread = 100;
            Thread[] threads = new Thread[numThreads];

            // When: Multiple threads save data concurrently
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < itemsPerThread; j++) {
                        String key = "thread" + threadId + "-item" + j;
                        String value = "value-" + threadId + "-" + j;
                        diskStore.save(key, value);
                    }
                });
                threads[i].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }

            // Then: Verify all data was saved correctly
            for (int i = 0; i < numThreads; i++) {
                for (int j = 0; j < itemsPerThread; j++) {
                    String key = "thread" + i + "-item" + j;
                    String expectedValue = "value-" + i + "-" + j;
                    assertEquals(expectedValue, diskStore.load(key));
                }
            }
        }
    }

    @Nested
    @DisplayName("Resource Management Tests")
    class ResourceManagementTests {

        @Test
        @DisplayName("Should close cleanly")
        void shouldCloseCleanly() {
            // When/Then
            assertDoesNotThrow(() -> diskStore.close());
        }

        @Test
        @DisplayName("Should handle multiple close calls")
        void shouldHandleMultipleCloseCalls() {
            // When/Then: Should not throw exception on multiple closes
            assertDoesNotThrow(() -> {
                diskStore.close();
                diskStore.close(); // Second close should be safe
            });
        }
    }
}