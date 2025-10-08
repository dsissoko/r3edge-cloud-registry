package com.r3edge.cloudregistry.cache;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Passerelle générique d’accès au cache distribué.
 * - putRaw : rebuild depuis la source canonique (le loader fait tout).
 * - putSmart : update par delta (merge existing + delta dans le loader).
 */
public interface CacheGateway {

  /** Lecture simple (sans chargement). */
  <T> Optional<T> get(String cacheName, Object key, Class<T> type);

  /** Écriture déterministe : le loader reconstruit la valeur canonique (DB “bourrin”). */
  <T> T putRaw(String cacheName, Object key, Supplier<T> loader, WriteOpts opts);

  /**
   * Écriture “smart” par delta : le loader fusionne (existing, delta) -> merged.
   * existing est absent si miss.
   */
  <T> T putSmart(String cacheName, Object key, T delta,
                 BiFunction<Optional<T>, T, T> loader,
                 WriteOpts opts);

  /** Invalidation ciblée. */
  void evict(String cacheName, Object key);

  /** Purge complète d’un cache. */
  void evictAll(String cacheName);

  /** Options d’écriture (TTL natif, tracing, etc.). */
  record WriteOpts(Integer ttlSeconds, String traceId) {
    public static WriteOpts none() { return new WriteOpts(null, null); }
    public static WriteOpts ttl(int seconds) { return new WriteOpts(seconds, null); }
  }
}
