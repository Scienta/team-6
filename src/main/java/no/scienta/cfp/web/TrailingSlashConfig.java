package no.scienta.cfp.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.UrlHandlerFilter;

/**
 * A trailing slash works like no slash, on every path and for every method (Speakers spec: the legacy web client
 * uses {@code /speakers/} and {@code /speakers/{id}/}). Spring Boot 2.7 did this by default; Spring 6+ doesn't. The
 * request is wrapped, not redirected, so {@code POST /speakers/} keeps its body and answers 201.
 */
@Configuration
public class TrailingSlashConfig {

    @Bean
    public FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
        FilterRegistrationBean<UrlHandlerFilter> registration =
                new FilterRegistrationBean<>(UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
