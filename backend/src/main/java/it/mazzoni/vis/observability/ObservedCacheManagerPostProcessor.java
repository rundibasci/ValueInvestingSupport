package it.mazzoni.vis.observability;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class ObservedCacheManagerPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<ObservabilitySupport> observability;

    public ObservedCacheManagerPostProcessor(ObjectProvider<ObservabilitySupport> observability) {
        this.observability = observability;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof CacheManager cacheManager && !(bean instanceof ObservedCacheManager)) {
            return new ObservedCacheManager(cacheManager, observability.getObject());
        }
        return bean;
    }
}
