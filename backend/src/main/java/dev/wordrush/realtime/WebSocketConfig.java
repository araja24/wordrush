package dev.wordrush.realtime;

import dev.wordrush.config.WordRushProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket at /ws.
 * <ul>
 *   <li>{@code /app/...}: client commands, routed to {@code @MessageMapping} methods</li>
 *   <li>{@code /topic/rooms/{code}}: room broadcasts</li>
 *   <li>{@code /user/queue/...}: private messages, routed by the principal's name (user id)</li>
 * </ul>
 * The in-memory simple broker is enough for one instance; Stage 9 fans events out via Redis.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Server and client each promise a frame at least this often; silence means a dead connection. */
    private static final long HEARTBEAT_MILLIS = 10_000;

    private final StompAuthChannelInterceptor authInterceptor;
    private final WordRushProperties properties;
    private TaskScheduler heartbeatScheduler;

    WebSocketConfig(StompAuthChannelInterceptor authInterceptor, WordRushProperties properties) {
        this.authInterceptor = authInterceptor;
        this.properties = properties;
    }

    /** The broker's own scheduler, injected lazily because it is created by this same configuration. */
    @Autowired
    void setHeartbeatScheduler(@Lazy TaskScheduler messageBrokerTaskScheduler) {
        this.heartbeatScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
                .setTaskScheduler(heartbeatScheduler);
        registry.setPreservePublishOrder(true); // per-session ordering of outbound messages
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
