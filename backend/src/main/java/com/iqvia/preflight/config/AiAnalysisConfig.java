package com.iqvia.preflight.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Makes our AI settings (API keys, provider choice) available to the analysis services.
 */
@Configuration
@EnableConfigurationProperties({AiAnalysisProperties.class, PreflightProperties.class})
public class AiAnalysisConfig {
}
