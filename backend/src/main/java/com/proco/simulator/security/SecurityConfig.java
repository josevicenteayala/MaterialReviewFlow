package com.proco.simulator.security;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.ApplicationRunner;
@Configuration
public class SecurityConfig {
 @Bean BCryptPasswordEncoder encoder(){ return new BCryptPasswordEncoder(); }
 @Bean UserDetailsService users(JdbcTemplate db){ return name -> db.query("select * from app_user where username=?", (r,n)->User.withUsername(r.getString("username")).password(r.getString("password")).roles(r.getString("role")).build(),name).stream().findFirst().orElseThrow(()->new UsernameNotFoundException(name)); }
 @Bean SecurityFilterChain filter(HttpSecurity http) throws Exception {
  // Stateless Basic credentials are supplied explicitly by the SPA, never cookies.
  return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.anyRequest().authenticated()).httpBasic(b->{}).build();
 }
}
