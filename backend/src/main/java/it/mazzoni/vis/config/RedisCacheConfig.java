package it.mazzoni.vis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
@EnableConfigurationProperties(CacheTtlProperties.class)
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory, CacheTtlProperties ttl) {
        var serializer = new GenericJackson2JsonRedisSerializer();
        var valuePair = RedisSerializationContext.SerializationPair.fromSerializer(serializer);

        var defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(valuePair);

        Map<String, RedisCacheConfiguration> namedCaches = Map.of(
                "mdc-quote",        defaultConfig.entryTtl(ttl.quote()),
                "mdc-ratios",       defaultConfig.entryTtl(ttl.ratios()),
                "mdc-fundamentals", defaultConfig.entryTtl(ttl.fundamentals()),
                "mdc-profile",      defaultConfig.entryTtl(ttl.profile())
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(namedCaches)
                .build();
    }
}
