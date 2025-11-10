package tier_cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.rocksdb.RocksDB;
import org.rocksdb.Options;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;

import org.rocksdb.CompressionType;

public class RocksDBDiskStore implements Diskstore {
    private RocksDB rocksDB;
    private final Pool<Kryo> kryoPool;
    private final WriteOptions writeOptions;
    private final ScheduledExecutorService scheduler;
    private Options options;
    private final String path;
    private final Object dbLock = new Object();

    public RocksDBDiskStore(String path, long retentionDays, long cleanupDuration, TimeUnit unit) throws RocksDBException {
        RocksDB.loadLibrary();
        this.kryoPool = new Pool<Kryo>(true, false, 8) {
            protected Kryo create() {
                Kryo kryo = new Kryo();
                kryo.setRegistrationRequired(false);
                kryo.setReferences(true);
                return kryo;
            }
        };
        this.path = path;

        // Create parent directories if they don't exist
        try {
            Path dbPath = Paths.get(path);
            Files.createDirectories(dbPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create database directory: " + path, e);
        }

        this.options = new Options()
            .setCreateIfMissing(true)
            .setCompressionType(CompressionType.ZSTD_COMPRESSION)
            .setWriteBufferSize(1 * 1024 * 1024); // 1GB
        this.rocksDB = RocksDB.open(options, path);
        this.writeOptions = new WriteOptions().setSync(false);
        if(cleanupDuration > 0) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "RocksDB-Cleanup");
                thread.setDaemon(true);
                return thread;
            });
            this.scheduler.scheduleAtFixedRate(this::cleanup, cleanupDuration, cleanupDuration, unit);
        } else {
            this.scheduler = null;
        }
    }

    private byte[] serialize(Object obj) {
        Kryo kryo = kryoPool.obtain();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos)) {
            kryo.writeClassAndObject(output, obj);
            output.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Serialization Failed ", e.getCause());
        } finally {
            kryoPool.free(kryo);
        }
    }

    private Object deserialize(byte[] bytes) {
        Kryo kryo = kryoPool.obtain();
        try (Input input = new Input(new ByteArrayInputStream(bytes))) {
            return kryo.readClassAndObject(input);
        } finally {
            kryoPool.free(kryo);
        }
    }

    @Override
    public void close() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (writeOptions != null) {
            writeOptions.close();
        }
        if (rocksDB != null) {
            rocksDB.close();
        }
        if (options != null) {
            options.close();
        }
    }

    @Override
    public void save(Object key, Object val) {
        synchronized (dbLock) {
            try {
                rocksDB.put(serialize(key), serialize(val));
            } catch (RocksDBException e) {
                throw new RuntimeException("Save Failed", e.getCause());
            }
        }
    }

    @Override
    public void saveBatch(Map<Object, Object> entries) {
        synchronized (dbLock) {
            try(WriteBatch writeBatch = new WriteBatch()) {
                for(Map.Entry<Object, Object> entry: entries.entrySet()) {
                    writeBatch.put(serialize(entry.getKey()), serialize(entry.getValue()));
                }
                rocksDB.write(this.writeOptions, writeBatch);
            } catch (RocksDBException e) {
                throw new RuntimeException("Batch write failed", e.getCause());
            }
        }
    }

    @Override
    public Object load(Object key) {
        synchronized (dbLock) {
            try {
                byte[] value = rocksDB.get(serialize(key));
                if (value == null) {
                    return null;
                }
                return deserialize(value);
            } catch (RocksDBException e) {
               throw new RuntimeException("Failed to load from RocksDB", e.getCause());
            }
        }
    }

    @Override
    public Map<Object, Object> loadBatch(List<Object> keys) {
        synchronized (dbLock) {
            Map<Object, Object> result = new HashMap<>();
            if (keys == null || keys.isEmpty()) {
                return result;
            }
            List<byte[]> keyBytes = keys.stream().map(this::serialize).toList();
            try {
                List<byte[]> values = rocksDB.multiGetAsList(keyBytes);
                for(int i=0;i<keys.size();i++) {
                    byte[] valueBytes = values.get(i);
                    if(valueBytes != null) {
                        Object val = deserialize(valueBytes);
                        if(val != null) {
                            result.put(keys.get(i), val);
                        }
                    }
                }
                return result;
            } catch (RocksDBException e) {
                throw new RuntimeException("Batch load failed", e.getCause());
            }
        }
    }

    private void cleanup() {
        synchronized (dbLock) {
            try {
                rocksDB.close();
                RocksDB.destroyDB(path, this.options);
                Path dbPath = Paths.get(path);
                Files.createDirectories(dbPath);
                rocksDB = RocksDB.open(this.options, this.path);
            } catch (RocksDBException | IOException e) {
                throw new RuntimeException("Failed to cleanup ", e);
            }
        }
    }
    
}
