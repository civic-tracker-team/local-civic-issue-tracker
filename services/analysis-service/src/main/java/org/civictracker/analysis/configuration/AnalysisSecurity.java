package org.civictracker.analysis.configuration;

import java.util.Map;
import org.civictracker.analysis.web.ReadProblems;
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

/** Separates internal read, processing and maintenance permissions. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AnalysisSecurity.Credentials.class)
public class AnalysisSecurity {
  @ConfigurationProperties("analysis.security")
  public record Credentials(
      Map<String, String> callers,
      Map<String, String> processors,
      Map<String, String> maintainers) {
    public Credentials {
      callers = callers == null ? Map.of() : Map.copyOf(callers);
      processors = processors == null ? Map.of() : Map.copyOf(processors);
      maintainers = maintainers == null ? Map.of() : Map.copyOf(maintainers);
      var all = new java.util.HashMap<String, String>();
      for (var group : java.util.List.of(callers, processors, maintainers)) {
        group.forEach((name, hash) -> {
          if (all.putIfAbsent(name, hash) != null) {
            throw new IllegalArgumentException("Service identities must have one authority group");
          }
        });
      }
      all.forEach((name, hash) -> {
        if (name.isBlank()
            || name.contains(":")
            || !hash.matches(
                "\\{bcrypt\\}\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])" + "\\$[./A-Za-z0-9]{53}")) {
          throw new IllegalArgumentException("Service identities require names and bcrypt hashes");
        }
      });
    }
  }

  @Bean
  UserDetailsService serviceUsers(Credentials credentials) {
    var users = new InMemoryUserDetailsManager();
    credentials
        .callers()
        .forEach((name, hash) ->
            users.createUser(
                User.withUsername(name).password(hash).roles("ANALYSIS_READ").build()));
    credentials
        .processors()
        .forEach((name, hash) ->
            users.createUser(
                User.withUsername(name)
                    .password(hash)
                    .roles("ANALYSIS_READ", "ANALYSIS_PROCESS")
                    .build()));
    credentials
        .maintainers()
        .forEach((name, hash) ->
            users.createUser(
                User.withUsername(name)
                    .password(hash)
                    .roles("ANALYSIS_READ", "ANALYSIS_PROCESS", "ANALYSIS_MAINTAIN")
                    .build()));
    return users;
  }

  @Bean
  SecurityFilterChain analysisReadChain(HttpSecurity http) throws Exception {
    org.springframework.security.web.AuthenticationEntryPoint unauthorized =
        (request, response, error) -> {
          response.setHeader("WWW-Authenticate", "Basic realm=\"analysis\"");
          ReadProblems.write(response, 401, "AUTHENTICATION_REQUIRED");
        };
    http.addFilterBefore(new InternalTransportFilter(), BasicAuthenticationFilter.class)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .csrf(csrf ->
            csrf.csrfTokenRepository(
                new org.springframework.security.web.csrf.CookieCsrfTokenRepository()))
        .authorizeHttpRequests(access ->
            access
                .requestMatchers(HttpMethod.POST, "/internal/v1/analysis-operations/*/resume")
                .hasRole("ANALYSIS_MAINTAIN")
                .requestMatchers(HttpMethod.POST, "/internal/v1/analysis-operations")
                .hasRole("ANALYSIS_PROCESS")
                .requestMatchers(HttpMethod.GET, "/internal/v1/csrf")
                .hasRole("ANALYSIS_PROCESS")
                .requestMatchers(
                    HttpMethod.GET,
                    "/internal/v1/analysis-operations/*",
                    "/internal/v1/meeting-analyses/*",
                    "/internal/v1/issues/*")
                .hasRole("ANALYSIS_READ")
                .anyRequest()
                .denyAll())
        .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized))
        .exceptionHandling(errors ->
            errors
                .authenticationEntryPoint(unauthorized)
                .accessDeniedHandler((request, response, error) ->
                    ReadProblems.write(response, 403, "ACCESS_DENIED")));
    return http.build();
  }
}
