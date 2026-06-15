package it.mazzoni.vis.marketdata;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

@Service
public class CacheEvictionService {

    @Caching(evict = {
            @CacheEvict(cacheNames = "mdc-quote",        key = "@cacheKeyHelper.key('quote', #symbol)"),
            @CacheEvict(cacheNames = "mdc-profile",      key = "@cacheKeyHelper.key('profile', #symbol)"),
            @CacheEvict(cacheNames = "mdc-fundamentals", key = "@cacheKeyHelper.key('fundamentals', #symbol)"),
            @CacheEvict(cacheNames = "mdc-ratios",       key = "@cacheKeyHelper.key('ratios', #symbol)")
    })
    public void evictSymbol(String symbol) {}
}
