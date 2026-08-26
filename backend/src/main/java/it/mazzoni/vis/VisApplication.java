package it.mazzoni.vis;

import it.mazzoni.vis.config.ValuationDefaultsProperties;
import it.mazzoni.vis.config.ValuationEnhancementProperties;
import it.mazzoni.vis.config.ValuationWeightsProperties;
import it.mazzoni.vis.config.ScoringRiskProperties;
import it.mazzoni.vis.config.DeploymentProperties;
import it.mazzoni.vis.config.JobsProperties;
import it.mazzoni.vis.marketdata.MarketDataProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties({
        MarketDataProperties.class,
        ScoringRiskProperties.class,
        ValuationWeightsProperties.class,
        ValuationDefaultsProperties.class,
        ValuationEnhancementProperties.class,
        DeploymentProperties.class,
        JobsProperties.class
})
public class VisApplication {

    /**
     * K2 Cloud Run Jobs entry point: {@code --job=<jobKey>} runs one
     * {@code CloudRunJob} to completion with no web server and exits with
     * the process's success/failure status, instead of starting the normal
     * Cloud Run API service. See {@code CloudRunJobEntryPoint}.
     */
    public static void main(String[] args) {
        boolean jobMode = Arrays.stream(args).anyMatch(arg -> arg.startsWith("--job="));
        if (!jobMode) {
            SpringApplication.run(VisApplication.class, args);
            return;
        }
        try {
            ConfigurableApplicationContext context = new SpringApplicationBuilder(VisApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(args);
            System.exit(SpringApplication.exit(context, () -> 0));
        } catch (Exception e) {
            System.exit(1);
        }
    }
}
