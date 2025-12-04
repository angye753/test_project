package com.example.bank.config;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.UUID;

/**
 * JacksonConfig - Configures Jackson JSON serialization/deserialization.
 * Provides custom deserializers for better error handling.
 */
@Configuration
public class JacksonConfig {

    /**
     * Custom UUID deserializer with better error messages.
     */
    public static class UUIDDeserializer extends JsonDeserializer<UUID> {
        @Override
        public UUID deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("UUID cannot be null or empty");
            }
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    String.format("Invalid UUID format: '%s'. Expected format: 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'", value),
                    e
                );
            }
        }
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(UUID.class, new UUIDDeserializer());
        mapper.registerModule(module);
        return mapper;
    }
}
