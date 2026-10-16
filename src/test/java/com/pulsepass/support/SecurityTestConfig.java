package com.pulsepass.support;

import com.pulsepass.security.RestAccessDeniedHandler;
import com.pulsepass.security.RestAuthenticationEntryPoint;
import com.pulsepass.security.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Aplica la SecurityConfig REAL en los tests @WebMvcTest (no se desactivan filtros: PRD 17.2).
 * El UserDetailsService es un stub vacío porque en MockMvc la identidad se simula con @WithMockUser.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
public class SecurityTestConfig {

    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }
}
