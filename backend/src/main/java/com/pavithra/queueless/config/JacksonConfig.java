package com.pavithra.queueless.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring MVC's own ObjectMapper (used for REST responses) already has JavaTimeModule
 * registered automatically via spring-boot-starter-json. But two other places in this
 * app build their OWN plain ObjectMapper that does NOT get that auto-configuration:
 *   - the STOMP/WebSocket message converter (WebSocketConfig)
 *   - the Redis JSON serializer (RedisConfig)
 * Both need this bean explicitly so java.time.Instant fields (like Ticket.checkedInAt)
 * serialize correctly instead of throwing "Java 8 date/time type ... not supported".
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper appObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
