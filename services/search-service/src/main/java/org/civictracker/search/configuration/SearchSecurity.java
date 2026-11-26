package org.civictracker.search.configuration;

import org.civictracker.search.web.SearchProblems;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Search serves only published results, so its one route is public. Every other route is denied,
 * and no login, session or default identity exists.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SearchSecurity {
  @Bean
  UserDetailsService noUsers() {
    return new InMemoryUserDetailsManager();
  }

  @Bean
  SecurityFilterChain searchChain(HttpSecurity http) throws Exception {
    http.addFilterBefore(new TransportFilter(), SecurityContextHolderFilter.class)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .csrf(csrf -> csrf.disable())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .authorizeHttpRequests(access ->
            access
                .requestMatchers(HttpMethod.GET, "/api/v1/search")
                .permitAll()
                .anyRequest()
                .denyAll())
        .exceptionHandling(errors ->
            errors
                .authenticationEntryPoint((request, response, error) ->
                    SearchProblems.write(response, 404, "NOT_FOUND"))
                .accessDeniedHandler((request, response, error) ->
                    SearchProblems.write(response, 404, "NOT_FOUND")));
    return http.build();
  }
}
