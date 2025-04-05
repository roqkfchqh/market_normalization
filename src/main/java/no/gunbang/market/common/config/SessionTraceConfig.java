package no.gunbang.market.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SessionTraceConfig {

    @Bean
    public FilterRegistrationBean<SessionTraceFilter> sessionTraceFilter1() {
        FilterRegistrationBean<SessionTraceFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new SessionTraceFilter());
        reg.setOrder(-100);
        return reg;
    }
}
