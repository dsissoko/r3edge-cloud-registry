package com.r3edge.cloudregistry.cache;

import java.util.Collection;
import java.util.concurrent.Callable;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.core.HazelcastInstance;

@EnableCaching
@Configuration
public class CacheConfig {

	@Bean
	@ConditionalOnBean(HazelcastInstance.class)
	CacheManager cacheManager(HazelcastInstance hz) {
		CacheManager hzMgr = new com.hazelcast.spring.cache.HazelcastCacheManager(hz);
		return new GzipDecoratingCacheManager(hzMgr);
	}
	
	  @Bean
	  @ConditionalOnMissingBean(CacheManager.class)
	  public CacheManager fallbackCacheManager() {
	    // Pour les tests : pas de cluster → cache local en mémoire
	    return new ConcurrentMapCacheManager();
	  }

	// 🎁 Décorateur: stocke en byte[] GZIP, rend un String à la lecture
	final class GzipDecoratingCacheManager implements CacheManager {
		private final CacheManager delegate;

		GzipDecoratingCacheManager(CacheManager d) {
			this.delegate = d;
		}

		@Override
		public Cache getCache(String name) {
			return new GzipCache(delegate.getCache(name));
		}

		@Override
		public Collection<String> getCacheNames() {
			return delegate.getCacheNames();
		}

		static final class GzipCache implements Cache {
			private final Cache target;

			GzipCache(Cache target) {
				this.target = target;
			}

			@Override
			public String getName() {
				return target.getName();
			}

			@Override
			public Object getNativeCache() {
				return target.getNativeCache();
			}

			@Override
			public ValueWrapper get(Object key) {
				ValueWrapper w = target.get(key);
				if (w == null)
					return null;
				Object v = w.get();
				if (v instanceof byte[] gz)
					return () -> ungzipToString(gz);
				return w;
			}

			@Override
			public <T> T get(Object key, Class<T> type) {
				ValueWrapper w = get(key);
				return w == null ? null : type.cast(w.get());
			}

			@Override
			public void put(Object key, Object value) {
				if (value instanceof String s)
					target.put(key, gzip(s));
				else
					target.put(key, value);
			}

			@Override
			public ValueWrapper putIfAbsent(Object key, Object value) {
				return target.putIfAbsent(key, (value instanceof String s) ? gzip(s) : value);
			}

			@Override
			public void evict(Object key) {
				target.evict(key);
			}

			@Override
			public void clear() {
				target.clear();
			}

			private static byte[] gzip(String s) {
				try (var baos = new java.io.ByteArrayOutputStream(s.length());
						var gos = new java.util.zip.GZIPOutputStream(baos)) {
					gos.write(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
					gos.close();
					return baos.toByteArray();
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}

			private static String ungzipToString(byte[] gz) {
				try (var gis = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
					return new String(gis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}

			@Override
			public <T> T get(Object key, Callable<T> valueLoader) {
				// 1) Essaye le cache (décompressé via notre get(...) décoré)
				ValueWrapper cached = get(key);
				if (cached != null) {
					@SuppressWarnings("unchecked")
					T val = (T) cached.get();
					return val;
				}

				// 2) Charge et essaye d'insérer de façon atomique
				try {
					T loaded = valueLoader.call();
					Object toStore = (loaded instanceof String s) ? gzip(s) : loaded;

					// putIfAbsent -> si quelqu'un a déjà stocké, on renvoie la valeur existante
					ValueWrapper previous = target.putIfAbsent(key, toStore);
					if (previous == null) {
						return loaded; // c'est nous qui avons stocké
					} else {
						Object v = previous.get();
						@SuppressWarnings("unchecked")
						T val = (T) (v instanceof byte[] gz ? ungzipToString(gz) : v);
						return val;
					}
				} catch (Exception ex) {
					throw new org.springframework.cache.Cache.ValueRetrievalException(key, valueLoader, ex);
				}
			}
		}
	}
}
