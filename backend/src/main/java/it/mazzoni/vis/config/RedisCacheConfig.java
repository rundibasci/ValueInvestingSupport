package it.mazzoni.vis.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.mazzoni.vis.client.yahoo.dto.ChartResponse;
import it.mazzoni.vis.client.yahoo.dto.QuoteSummaryResponse;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.marketdata.CacheSchema;
import it.mazzoni.vis.observability.ObservabilitySupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
@EnableConfigurationProperties(CacheTtlProperties.class)
public class RedisCacheConfig implements CachingConfigurer {
    private final ObservabilitySupport observability;

    public RedisCacheConfig(ObservabilitySupport observability) {
        this.observability = observability;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     CacheTtlProperties ttl,
                                     ObjectMapper applicationObjectMapper) {
        ObjectMapper mapper = applicationObjectMapper.copy();
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> caches = new LinkedHashMap<>();
        caches.put("mdc-quote", typed(defaults, mapper, MarketPriceQuote.class, ttl.quote()));
        caches.put("mdc-ratios", typed(defaults, mapper, RatioSnapshot.class, ttl.ratios()));
        caches.put("mdc-fundamentals", typed(defaults, mapper, FundamentalSnapshot.class, ttl.fundamentals()));
        caches.put("mdc-profile", typed(defaults, mapper, CompanyProfile.class, ttl.profile()));
        JavaType annualRatios = mapper.getTypeFactory().constructCollectionType(List.class, RatioSnapshot.class);
        caches.put("mdc-annual-ratios", typed(defaults, mapper, annualRatios, ttl.ratios()));
        caches.put(CacheSchema.YAHOO_QUOTE_SUMMARY,
                typed(defaults, mapper, QuoteSummaryResponse.class, ttl.fundamentals()));
        caches.put(CacheSchema.YAHOO_CHART,
                typed(defaults, mapper, ChartResponse.class, ttl.quote()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(caches)
                .disableCreateOnMissingCache()
                .build();
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisCacheReadErrorHandler(observability);
    }

    private static RedisCacheConfiguration typed(RedisCacheConfiguration base, ObjectMapper mapper,
                                                  Class<?> type, Duration ttl) {
        return base.entryTtl(ttl).serializeValuesWith(pair(new Jackson2JsonRedisSerializer<>(mapper, type)));
    }

    private static RedisCacheConfiguration typed(RedisCacheConfiguration base, ObjectMapper mapper,
                                                  JavaType type, Duration ttl) {
        return base.entryTtl(ttl).serializeValuesWith(pair(new Jackson2JsonRedisSerializer<>(mapper, type)));
    }

    private static RedisSerializationContext.SerializationPair<Object> pair(
            Jackson2JsonRedisSerializer<?> serializer) {
        @SuppressWarnings("unchecked")
        var typed = (org.springframework.data.redis.serializer.RedisSerializer<Object>) serializer;
        return RedisSerializationContext.SerializationPair.fromSerializer(typed);
    }
}
