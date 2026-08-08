package com.spiramindscape.backend.logging;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class LoggingConfig {

    /**
     * Registers {@link RequestLogContextFilter} ahead of everything else, including
     * Spring Security's own chain (registered at order {@code -100}).
     *
     * <p>Ordering is the whole decision here. Adding the filter inside {@code HttpSecurity}
     * would place it after several security filters, so an unauthenticated 401, a CSRF
     * rejection or a rate-limit 429 would be logged with no trace id — and those are
     * exactly the lines worth correlating. Wrapping the security chain instead means the
     * id is set before any of that can happen, and cleared after all of it.
     */
    @Bean
    public FilterRegistrationBean<RequestLogContextFilter> requestLogContextFilter() {
        FilterRegistrationBean<RequestLogContextFilter> registration =
                new FilterRegistrationBean<>(new RequestLogContextFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        // Boot registers filters for REQUEST only. The AI chat streams over an
        // SseEmitter, whose work happens on the ASYNC dispatch — without ASYNC here the
        // filter's shouldNotFilterAsyncDispatch() override could never take effect and
        // the stream's log lines would carry no trace id.
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
        return registration;
    }
}
