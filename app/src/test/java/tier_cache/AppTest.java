/*
 * TierCache Library Tests
 */
package tier_cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.rocksdb.RocksDBException;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    @Test
    @DisplayName("App should have a greeting")
    void appHasAGreeting() {
        App classUnderTest = new App();
        assertNotNull(classUnderTest.getGreeting(), "app should have a greeting");
        assertEquals("TierCache Library v1.0", classUnderTest.getGreeting());
    }

    @Nested
    @DisplayName("Config Record Tests")
    class ConfigTests {

        @Test
        @DisplayName("Should create Config record correctly")
        void shouldCreateConfigRecordCorrectly() {
            String key = "test.key";
            String value = "test.value";

            App.Config config = new App.Config(key, value);

            assertEquals(key, config.key());
            assertEquals(value, config.value());
        }

        @Test
        @DisplayName("Should support Config equality")
        void shouldSupportConfigEquality() {
            App.Config config1 = new App.Config("key", "value");
            App.Config config2 = new App.Config("key", "value");
            App.Config config3 = new App.Config("different", "value");

            assertEquals(config1, config2);
            assertNotEquals(config1, config3);
        }
    }

    @Nested
    @DisplayName("TierCache Library Tests")
    class TierCacheTests {

        @Test
        @DisplayName("Should create TierCache with default repository")
        void shouldCreateTierCacheWithDefaults() throws RocksDBException {
            String dbPath = System.getProperty("java.io.tmpdir") + "/test-cache-" + System.currentTimeMillis();

            try (App.TierCache<String, App.Config, App.Config> cache =
                 App.TierCache.<String, App.Config, App.Config>builder()
                     .diskStorePath(dbPath)
                     .maxCacheSize(100)
                     .build()) {

                assertNotNull(cache);

                // Test basic operations
                App.Config config = new App.Config("test.key", "test.value");
                cache.put("test", config);

                App.Config retrieved = cache.get("test");
                assertNotNull(retrieved);
                assertEquals(config.key(), retrieved.key());
                assertEquals(config.value(), retrieved.value());
            }
        }

        @Test
        @DisplayName("Should create TierCache with custom repository")
        void shouldCreateTierCacheWithCustomRepository() throws RocksDBException {
            String dbPath = System.getProperty("java.io.tmpdir") + "/test-cache-custom-" + System.currentTimeMillis();
            DatabaseRepository<App.Config> customRepo = new DatabaseRepository<>();

            try (App.TierCache<String, App.Config, App.Config> cache =
                 App.TierCache.<String, App.Config, App.Config>builder()
                     .diskStorePath(dbPath)
                     .maxCacheSize(50)
                     .retentionDays(1)
                     .repository(customRepo)
                     .build()) {

                assertNotNull(cache);

                // Test with custom repository
                App.Config config = new App.Config("custom.key", "custom.value");
                cache.put("custom", config);

                App.Config retrieved = cache.get("custom");
                assertNotNull(retrieved);
                assertEquals(config.key(), retrieved.key());
                assertEquals(config.value(), retrieved.value());
            } finally {
                customRepo.close();
            }
        }

        @Test
        @DisplayName("Should return null for non-existent keys")
        void shouldReturnNullForNonExistentKeys() throws RocksDBException {
            String dbPath = System.getProperty("java.io.tmpdir") + "/test-cache-null-" + System.currentTimeMillis();

            try (App.TierCache<String, App.Config, App.Config> cache =
                 App.TierCache.<String, App.Config, App.Config>builder()
                     .diskStorePath(dbPath)
                     .build()) {

                App.Config result = cache.get("non.existent.key");
                assertNull(result);
            }
        }
    }
}
