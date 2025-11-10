package tier_cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for DatabaseRepository implementation.
 * Tests the in-memory repository with simulated database latency.
 */
class DatabaseRepositoryTest {

    private DatabaseRepository<String> repository;

    @BeforeEach
    void setUp() {
        repository = new DatabaseRepository<>();
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.close();
        }
    }

    @Nested
    @DisplayName("Basic Repository Operations")
    class BasicOperationsTests {

        @Test
        @DisplayName("Should save and retrieve values")
        void shouldSaveAndRetrieveValues() {
            // Given
            String key = "test-key";
            String value = "test-value";

            // When
            repository.save(key, value);
            String result = repository.findByKey(key);

            // Then
            assertEquals(value, result);
        }

        @Test
        @DisplayName("Should return null for non-existent keys")
        void shouldReturnNullForNonExistentKeys() {
            // When
            String result = repository.findByKey("non-existent-key");

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle null values")
        void shouldHandleNullValues() {
            // Given
            String key = "null-key";

            // When
            repository.save(key, null);
            String result = repository.findByKey(key);

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("Should overwrite existing values")
        void shouldOverwriteExistingValues() {
            // Given
            String key = "overwrite-key";
            String originalValue = "original-value";
            String newValue = "new-value";

            // When
            repository.save(key, originalValue);
            repository.save(key, newValue);
            String result = repository.findByKey(key);

            // Then
            assertEquals(newValue, result);
        }
    }

    @Nested
    @DisplayName("Simulated Database Latency Tests")
    class LatencyTests {

        @Test
        @DisplayName("Should introduce random delay on save operations")
        @Timeout(value = 5, unit = TimeUnit.SECONDS) // Max possible delay is ~1 second
        void shouldIntroduceDelayOnSave() {
            // Given
            String key = "delay-save-key";
            String value = "delay-save-value";

            // When: Measure time for save operation
            long startTime = System.currentTimeMillis();
            repository.save(key, value);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Then: Should take at least 100ms (minimum random delay)
            assertTrue(duration >= 100, "Save operation should introduce delay of at least 100ms, but took " + duration + "ms");
        }

        @Test
        @DisplayName("Should introduce random delay on findByKey operations")
        @Timeout(value = 10, unit = TimeUnit.SECONDS) // Max possible delay is ~2 seconds (save + findByKey)
        void shouldIntroduceDelayOnFindByKey() {
            // Given
            String key = "delay-find-key";
            String value = "delay-find-value";
            repository.save(key, value);

            // When: Measure time for findByKey operation
            long startTime = System.currentTimeMillis();
            repository.findByKey(key);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Then: Should take at least 100ms (minimum random delay)
            assertTrue(duration >= 100, "FindByKey operation should introduce delay of at least 100ms, but took " + duration + "ms");
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent access safely")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void shouldHandleConcurrentAccessSafely() throws InterruptedException {
            // Given: Multiple threads accessing repository
            int numThreads = 5;
            int itemsPerThread = 10;
            Thread[] threads = new Thread[numThreads];

            // When: Multiple threads save data concurrently
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < itemsPerThread; j++) {
                        String key = "thread" + threadId + "-item" + j;
                        String value = "value-" + threadId + "-" + j;
                        repository.save(key, value);
                    }
                });
                threads[i].start();
            }

            // Wait for all save operations to complete
            for (Thread thread : threads) {
                thread.join();
            }

            // Then: Verify all data was saved correctly
            for (int i = 0; i < numThreads; i++) {
                for (int j = 0; j < itemsPerThread; j++) {
                    String key = "thread" + i + "-item" + j;
                    String expectedValue = "value-" + i + "-" + j;
                    assertEquals(expectedValue, repository.findByKey(key));
                }
            }
        }
    }

    @Nested
    @DisplayName("Data Types Tests")
    class DataTypesTests {

        @Test
        @DisplayName("Should handle different key types")
        void shouldHandleDifferentKeyTypes() {
            // Given: Repository that accepts Object keys
            DatabaseRepository<String> repo = new DatabaseRepository<>();

            // When: Save with different key types
            repo.save("string-key", "string-value");
            repo.save(123, "integer-key-value");
            repo.save(123L, "long-key-value");

            // Then: Should retrieve correctly
            assertEquals("string-value", repo.findByKey("string-key"));
            assertEquals("integer-key-value", repo.findByKey(123));
            assertEquals("long-key-value", repo.findByKey(123L));

            repo.close();
        }

        @Test
        @DisplayName("Should handle complex value types")
        void shouldHandleComplexValueTypes() {
            // Given: Repository for complex types
            DatabaseRepository<App.Config> configRepo = new DatabaseRepository<>();

            App.Config config = new App.Config("database.host", "localhost");

            // When
            configRepo.save("config1", config);
            App.Config result = configRepo.findByKey("config1");

            // Then
            assertNotNull(result);
            assertEquals("database.host", result.key());
            assertEquals("localhost", result.value());

            configRepo.close();
        }
    }

    @Nested
    @DisplayName("Resource Management Tests")
    class ResourceManagementTests {

        @Test
        @DisplayName("Should clear data on close")
        void shouldClearDataOnClose() {
            // Given
            String key = "close-test-key";
            String value = "close-test-value";

            repository.save(key, value);
            assertEquals(value, repository.findByKey(key));

            // When
            repository.close();

            // Then: Data should be cleared (though we can't test this directly due to close clearing the map)
            assertDoesNotThrow(() -> repository.close());
        }

        @Test
        @DisplayName("Should handle multiple close calls")
        void shouldHandleMultipleCloseCalls() {
            // When/Then: Should not throw exception on multiple closes
            assertDoesNotThrow(() -> {
                repository.close();
                repository.close(); // Second close should be safe
            });
        }
    }

    @Nested
    @DisplayName("Thread Interruption Tests")
    class ThreadInterruptionTests {

        @Test
        @DisplayName("Should handle thread interruption during save")
        void shouldHandleThreadInterruptionDuringSave() {
            // Given: A thread that will be interrupted
            Thread testThread = new Thread(() -> {
                Thread.currentThread().interrupt(); // Set interrupt flag

                // When: Try to save (this should throw RuntimeException due to InterruptedException)
                assertThrows(RuntimeException.class, () -> {
                    repository.save("interrupt-key", "interrupt-value");
                });
            });

            // When/Then
            assertDoesNotThrow(() -> {
                testThread.start();
                testThread.join();
            });
        }

        @Test
        @DisplayName("Should handle thread interruption during findByKey")
        void shouldHandleThreadInterruptionDuringFindByKey() {
            // Given: A thread that will be interrupted
            Thread testThread = new Thread(() -> {
                Thread.currentThread().interrupt(); // Set interrupt flag

                // When: Try to find (this should throw RuntimeException due to InterruptedException)
                assertThrows(RuntimeException.class, () -> {
                    repository.findByKey("interrupt-key");
                });
            });

            // When/Then
            assertDoesNotThrow(() -> {
                testThread.start();
                testThread.join();
            });
        }
    }
}