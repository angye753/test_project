package com.example.bank.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.TracerProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability Configuration - Provides OpenTelemetry beans for tracing.
 * Uses default implementations when no external OpenTelemetry SDK is configured.
 */
@Configuration
public class ObservabilityConfiguration {

    /**
     * Provides a default OpenTelemetry instance if none is configured.
     * Uses the default TracerProvider (typically a no-op tracer for development).
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry() {
        return OpenTelemetry.noop();
    }
}
