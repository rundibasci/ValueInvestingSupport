package it.mazzoni.vis;

import it.mazzoni.vis.config.ValuationWeightsProperties;
import it.mazzoni.vis.marketdata.MarketDataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties({MarketDataProperties.class, ValuationWeightsProperties.class})
public class VisApplication {

    public static void main(String[] args) {
        SpringApplication.run(VisApplication.class, args);
    }
}
