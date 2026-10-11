package org.springframework.samples.petclinic.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring's cache abstraction. With no cache provider on the classpath, Spring Boot
 * backs the caches with in-memory maps.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
