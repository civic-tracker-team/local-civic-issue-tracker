package org.civictracker.coordinator.config;

import java.util.List;
import java.util.Map;
import org.civictracker.coordinator.web.RunProblems;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/** Restricts run control to maintainers and reserves publication reads for readers. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(CoordinatorSecurity.Credentials.class)
public class CoordinatorSecurity {
  @ConfigurationProperties("coordinator.security")
  public record Credentials(
      Map<String, String> maintainers, Map<String, String> publicationReaders) {
    public Credentials {
      maintainers = maintainers == null ? Map.of() : Map.copyOf(maintainers);
      publicationReaders = publicationReaders == null ? Map.of() : Map.copyOf(publicationReaders);
      var all = new java.util.HashMap<String, String>();
      for (var group : List.of(maintainers, publicationReaders)) {
        group.forEach((name, hash) -> {
          if (all.putIfAbsent(name, hash) != null) {
            throw new IllegalArgumentException("Service identities must have one authority group");
          }
          if (name.isBlank()
              || name.contains(":")
              || !hash.matches(
                  "\\{bcrypt\\}\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalArgumentException(
                "Service identities require names and bcrypt hashes");
          }
        });
      }
    }
  }

  @Bean
  UserDetailsService serviceUsers(Credentials credentials) {
    var users = new InMemoryUserDetailsManager();
    credentials
        .maintainers()
        .forEach((name, hash) ->
            users.createUser(
                User.withUsername(name).password(hash).roles("COORDINATOR_MAINTAIN").build()));
    credentials
        .publicationReaders()
        .forEach((name, hash) ->
            users.createUser(
                User.withUsername(name).password(hash).roles("PUBLICATION_READ").build()));
    return users;
  }

  @Bean
  SecurityFilterChain coordinatorChain(HttpSecurity http) throws Exception {
    org.springframework.security.web.AuthenticationEntryPoint unauthorized =
        (request, response, error) -> {
          response.setHeader("WWW-Authenticate", "Basic realm=\"coordinator\"");
          RunProblems.write(response, 401, "AUTHENTICATION_REQUIRED");
        };
    http.addFilterBefore(new InternalTransportFilter(), BasicAuthenticationFilter.class)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .csrf(csrf ->
            csrf.csrfTokenRepository(
                new org.springframework.security.web.csrf.CookieCsrfTokenRepository()))
        .authorizeHttpRequests(access ->
            access
                .requestMatchers(
                    HttpMethod.GET,
                    "/internal/v1/csrf",
                    "/internal/v1/runs/{id}",
                    "/internal/v1/runs/active")
                .hasRole("COORDINATOR_MAINTAIN")
                .requestMatchers(
                    HttpMethod.POST,
                    "/internal/v1/runs",
                    "/internal/v1/runs/{id}/retry",
                    "/internal/v1/runs/{id}/abandon",
                    "/internal/v1/runs/{id}/publish",
                    "/internal/v1/runs/{id}/skip")
                .hasRole("COORDINATOR_MAINTAIN")
                .requestMatchers(
                    HttpMethod.GET,
                    "/internal/v1/publications/current",
                    "/internal/v1/publications/{id}")
                .hasAnyRole("COORDINATOR_MAINTAIN", "PUBLICATION_READ")
                .anyRequest()
                .denyAll())
        .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized))
        .exceptionHandling(errors ->
            errors
                .authenticationEntryPoint(unauthorized)
                .accessDeniedHandler((request, response, error) ->
                    RunProblems.write(response, 403, "ACCESS_DENIED")));
    return http.build();
  }
}
