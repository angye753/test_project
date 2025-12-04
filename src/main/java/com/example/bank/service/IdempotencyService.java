package com.example.bank.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * IdempotencyService - Ensures idempotent processing of transactions using Redis.
 * Prevents duplicate transaction processing in distributed systems.
 * 
 * Uses Redis with TTL to track processed idempotency keys temporarily.
 * Database stores the actual transaction, Redis is used for fast checks.
 */
@Slf4j
@Service
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final long TTL_MINUTES = 24 * 60; // 24 hours

    public IdempotencyService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Attempts to register an idempotency key.
     * Returns true if key was successfully registered (first time seen).
     * Returns false if key already exists (duplicate request).
     * 
     * @param idempotencyKey UUID key for transaction
     * @return true if key is new and registered, false if already exists
     */
    public boolean tryRegisterIdempotencyKey(UUID idempotencyKey) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        
        try {
            Boolean wasSet = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", TTL_MINUTES, TimeUnit.MINUTES);
            
            if (Boolean.TRUE.equals(wasSet)) {
                log.debug("Idempotency key registered: {}", idempotencyKey);
                return true;
            } else {
                log.warn("Duplicate idempotency key detected: {}", idempotencyKey);
                return false;
            }
        } catch (Exception ex) {
            log.error("Error checking idempotency key in Redis: {}", ex.getMessage());
            // Fail open - if Redis is down, allow the request
            // Database uniqueness constraint will catch true duplicates
            return true;
        }
    }

    /**
     * Checks if an idempotency key exists without registering it.
     * Used for read-only checks.
     * 
     * @param idempotencyKey UUID key to check
     * @return true if key exists, false otherwise
     */
    public boolean idempotencyKeyExists(UUID idempotencyKey) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        
        try {
            Boolean exists = redisTemplate.hasKey(redisKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception ex) {
            log.error("Error checking idempotency key existence: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Removes an idempotency key (cleanup after processing).
     * Usually not needed due to TTL, but available for explicit cleanup.
     * 
     * @param idempotencyKey UUID key to remove
     */
    public void removeIdempotencyKey(UUID idempotencyKey) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        
        try {
            redisTemplate.delete(redisKey);
            log.debug("Idempotency key removed: {}", idempotencyKey);
        } catch (Exception ex) {
            log.error("Error removing idempotency key: {}", ex.getMessage());
        }
    }

    /**
     * Gets remaining TTL for an idempotency key.
     * Useful for monitoring and debugging.
     * 
     * @param idempotencyKey UUID key
     * @return Remaining TTL in milliseconds, or -2 if key doesn't exist, -1 if no expiry
     */
    public long getIdempotencyKeyTTL(UUID idempotencyKey) {
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        
        try {
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
            return ttl != null ? ttl : -2;
        } catch (Exception ex) {
            log.error("Error getting idempotency key TTL: {}", ex.getMessage());
            return -2;
        }
    }
}
