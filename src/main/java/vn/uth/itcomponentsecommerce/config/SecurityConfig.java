package vn.uth.itcomponentsecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.uth.itcomponentsecommerce.security.CustomUserDetailsService;
import vn.uth.itcomponentsecommerce.security.JwtAuthFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import static org.springframework.security.config.Customizer.withDefaults;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          CustomUserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(withDefaults())
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(
                            "/api/auth/**",
                            "/api/v1/**",
                            "/api/admin/**"
                    )
            )
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(
                        "/", "/home",
                        "/checkout", "/cart", "/orders/my", "/wishlist",
                        "/payment/success", "/payment/error", "/payment/cancel",
                        "/products", "/products/**",
                        "/login", "/register",
                        "/account",
                        "/admin", "/admin/**",
                        "/staff", "/staff/**",
                        "/api/auth/**",
                        "/api/v1/payments/sepay/ipn",
                        "/api/products", "/api/products/**",
                        "/api/v1/wishlist/**",
                        "/css/**", "/js/**", "/images/**", "/uploads/**", "/vendor/**", "/webjars/**",
                        "/error"
                ).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/products/*/reviews", "/api/v1/products/*/reviews/summary").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/vouchers/preview").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/v1/orders/*/status").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/orders/*/refund/reject").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/orders/admin/**").hasAnyRole("STAFF", "ADMIN")
                .requestMatchers("/staff/**").hasAnyRole("STAFF", "ADMIN")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseAllowedOrigins(allowedOrigins));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-CSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> parseAllowedOrigins(String origins) {
        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
