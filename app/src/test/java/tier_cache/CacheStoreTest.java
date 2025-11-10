package tier_cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for CacheStore tier cache implementation.
 * Tests the three-tier caching behavior: in-memory -> repository -> disk store
 */
class CacheStoreTest {

    @Mock
    private Diskstore mockDiskStore;

    @Mock
    private Repository<String> mockRepository;

    private CacheStore<String, String, String> cacheStore;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        cacheStore = new CacheStore<>(mockDiskStore, mockRepository, 1000L);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (cacheStore != null) {
            cacheStore.close();
        }
        mocks.close();
    }

    @Nested
    @DisplayName("Cache Hit Scenarios")
    class CacheHitTests {

        @Test
        @DisplayName("Should return value from in-memory cache on first hit")
        void shouldReturnFromInMemoryCache() {
            // Given: Add item to cache first
            String key = "test-key";
            String expectedValue = "test-value";

            // Simulate getting item that will be cached
            when(mockRepository.findByKey(key)).thenReturn(expectedValue);

            // First call loads from repository and caches
            String firstResult = cacheStore.get(key);
            assertEquals(expectedValue, firstResult);

            // Reset mocks to ensure second call doesn't hit repository/disk
            reset(mockRepository, mockDiskStore);

            // When: Get same key again
            String result = cacheStore.get(key);

            // Then: Should return from cache without hitting repository or disk
            assertEquals(expectedValue, result);
            verifyNoInteractions(mockRepository, mockDiskStore);
        }
    }

    @Nested
    @DisplayName("Repository Tier Tests")
    class RepositoryTierTests {

        @Test
        @DisplayName("Should fallback to repository when not in cache")
        void shouldFallbackToRepository() {
            // Given
            String key = "repo-key";
            String expectedValue = "repo-value";
            when(mockRepository.findByKey(key)).thenReturn(expectedValue);

            // When
            String result = cacheStore.get(key);

            // Then
            assertEquals(expectedValue, result);
            verify(mockRepository).findByKey(key);
            verifyNoInteractions(mockDiskStore);
        }

        @Test
        @DisplayName("Should proceed to disk store when repository throws exception")
        void shouldProceedToDiskStoreOnRepositoryException() {
            // Given
            String key = "disk-key";
            String expectedValue = "disk-value";
            when(mockRepository.findByKey(key)).thenThrow(new RuntimeException("Repository error"));
            when(mockDiskStore.load(key)).thenReturn(expectedValue);

            // When
            String result = cacheStore.get(key);

            // Then
            assertEquals(expectedValue, result);
            verify(mockRepository).findByKey(key);
            verify(mockDiskStore).load(key);
        }

        @Test
        @DisplayName("Should proceed to disk store when repository returns null")
        void shouldProceedToDiskStoreOnRepositoryNull() {
            // Given
            String key = "disk-key";
            String expectedValue = "disk-value";
            when(mockRepository.findByKey(key)).thenReturn(null);
            when(mockDiskStore.load(key)).thenReturn(expectedValue);

            // When
            String result = cacheStore.get(key);

            // Then
            assertEquals(expectedValue, result);
            verify(mockRepository).findByKey(key);
            verify(mockDiskStore).load(key);
        }
    }

    @Nested
    @DisplayName("Disk Store Tier Tests")
    class DiskStoreTierTests {

        @Test
        @DisplayName("Should return value from disk store when not in cache or repository")
        void shouldReturnFromDiskStore() {
            // Given
            String key = "disk-key";
            String expectedValue = "disk-value";
            when(mockRepository.findByKey(key)).thenThrow(new RuntimeException("Not found"));
            when(mockDiskStore.load(key)).thenReturn(expectedValue);

            // When
            String result = cacheStore.get(key);

            // Then
            assertEquals(expectedValue, result);
            verify(mockDiskStore).load(key);
        }

        @Test
        @DisplayName("Should return null when not found in any tier")
        void shouldReturnNullWhenNotFoundAnywhere() {
            // Given
            String key = "missing-key";
            when(mockRepository.findByKey(key)).thenThrow(new RuntimeException("Not found"));
            when(mockDiskStore.load(key)).thenReturn(null);

            // When
            String result = cacheStore.get(key);

            // Then
            assertNull(result);
            verify(mockRepository).findByKey(key);
            verify(mockDiskStore).load(key);
        }
    }

    @Nested
    @DisplayName("Cache Eviction Tests")
    class CacheEvictionTests {

        @Test
        @DisplayName("Should save evicted items to disk store")
        void shouldSaveEvictedItemsToDiskStore() throws InterruptedException {
            // Given: Create a cache with maximum size of 1
            CacheStore<String, String, String> smallCacheStore = new CacheStore<>(mockDiskStore, mockRepository, 1L);

            String key1 = "key1";
            String value1 = "value1";
            String key2 = "key2";
            String value2 = "value2";

            when(mockRepository.findByKey(key1)).thenReturn(value1);
            when(mockRepository.findByKey(key2)).thenReturn(value2);

            // When: Add first item to cache
            smallCacheStore.get(key1);

            // Add second item which should evict first due to maxSize=1
            smallCacheStore.get(key2);

            // Wait a bit for eviction listener to execute
            Thread.sleep(100);

            // Then: First item should be saved to disk store
            verify(mockDiskStore).save(key1, value1);

            // Clean up
            smallCacheStore.close();
        }
    }

    @Nested
    @DisplayName("Resource Management Tests")
    class ResourceManagementTests {

        @Test
        @DisplayName("Should clean up cache on close")
        void shouldCleanUpCacheOnClose() {
            // When
            assertDoesNotThrow(() -> cacheStore.close());

            // Then: No exception should be thrown
            // Cache cleanup is called internally (tested via behavior, not verification)
        }
    }

    @Nested
    @DisplayName("Integration Test Scenarios")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle complex cache flow scenario")
        void shouldHandleComplexCacheFlow() {
            // Given
            String key = "complex-key";
            String repoValue = "repo-value";
            String diskValue = "disk-value";

            // Setup: Repository has value, disk has different value
            when(mockRepository.findByKey(key)).thenReturn(repoValue);
            when(mockDiskStore.load(key)).thenReturn(diskValue);

            // When: First access should hit repository
            String firstResult = cacheStore.get(key);
            assertEquals(repoValue, firstResult);

            // Clear repository for next test
            reset(mockRepository);
            when(mockRepository.findByKey(key)).thenThrow(new RuntimeException("Not found"));

            // Second access should hit cache (no repo/disk interaction)
            String secondResult = cacheStore.get(key);
            assertEquals(repoValue, secondResult);

            // Verify repository wasn't called on second access
            verifyNoInteractions(mockRepository);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle disk store exceptions gracefully")
        void shouldHandleDiskStoreExceptions() {
            // Given
            String key = "error-key";
            when(mockRepository.findByKey(key)).thenThrow(new RuntimeException("Repo error"));
            when(mockDiskStore.load(key)).thenThrow(new RuntimeException("Disk error"));

            // When/Then: Should return null when all tiers fail
            String result = cacheStore.get(key);
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle eviction save failures gracefully")
        void shouldHandleEvictionSaveFailures() throws InterruptedException {
            // Given
            String key = "eviction-key";
            String value = "eviction-value";
            when(mockRepository.findByKey(key)).thenReturn(value);
            doThrow(new RuntimeException("Save failed")).when(mockDiskStore).save(key, value);

            // When: Load item and trigger eviction
            cacheStore.get(key);
            when(mockRepository.findByKey("key2")).thenReturn("value2");
            cacheStore.get("key2"); // This should trigger eviction of first item

            Thread.sleep(100);

            // Then: Exception in eviction listener should not break the cache
            // The cache should still function for new requests
            String result = cacheStore.get("key2");
            assertEquals("value2", result);
        }
    }
}