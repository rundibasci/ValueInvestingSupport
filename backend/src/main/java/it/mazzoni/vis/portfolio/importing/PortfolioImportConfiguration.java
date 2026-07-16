package it.mazzoni.vis.portfolio.importing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PortfolioImportProperties.class)
public class PortfolioImportConfiguration {
}
