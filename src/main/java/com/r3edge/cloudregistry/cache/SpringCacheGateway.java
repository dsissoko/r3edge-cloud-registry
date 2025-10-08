package com.r3edge.cloudregistry.cache;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import com.hazelcast.map.IMap;

@Component
public class SpringCacheGateway implements CacheGateway {

  private final CacheManager cacheManager;

  public SpringCacheGateway(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  // ---------- READ ----------

  @Override
  public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
    Cache cache = requireCache(cacheName);
    T v = cache.get(key, type);
    return Optional.ofNullable(v);
  }

  // ---------- WRITE: RAW (rebuild bourrin) ----------

  @Override
  public <T> T putRaw(String cacheName, Object key, Supplier<T> loader, WriteOpts opts) {
    T value = loader.get();
    putWithTtl(cacheName, key, value, opts);
    return value;
  }

  // ---------- WRITE: SMART (merge existing + delta) ----------

  @Override
  public <T> T putSmart(String cacheName, Object key, T delta,
                        BiFunction<Optional<T>, T, T> loader, WriteOpts opts) {
    Cache cache = requireCache(cacheName);
    @SuppressWarnings("unchecked")
    T existing = (T) (cache.get(key) == null ? null : cache.get(key).get());

    T merged = loader.apply(Optional.ofNullable(existing), delta);
    putWithTtl(cacheName, key, merged, opts);
    return merged;
  }

  // ---------- EVICT ----------

  @Override
  public void evict(String cacheName, Object key) {
    requireCache(cacheName).evict(key);
  }

  @Override
  public void evictAll(String cacheName) {
    requireCache(cacheName).clear();
  }

  // ---------- Helpers ----------

  private Cache requireCache(String cacheName) {
    Cache c = cacheManager.getCache(cacheName);
    if (c == null) throw new IllegalArgumentException("Unknown cache: " + cacheName);
    return c;
  }

  private <T> void putWithTtl(String cacheName, Object key, T value, WriteOpts opts) {
    Cache cache = requireCache(cacheName);
    Integer ttl = (opts == null) ? null : opts.ttlSeconds();

    // Si Hazelcast IMap dispo: on applique un TTL natif, sinon fallback Spring Cache
    Object nativeCache = cache.getNativeCache();
    if (ttl != null && nativeCache instanceof IMap<?, ?> iMap) {
      @SuppressWarnings("unchecked")
      IMap<Object,Object> map = (IMap<Object,Object>) iMap;
      map.set(key, value, ttl.longValue(), TimeUnit.SECONDS);
    } else {
      cache.put(key, value);
    }
  }
}
