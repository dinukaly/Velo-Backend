package com.dinukaly.velo.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        //get token from request
        final String tokenHeader = request.getHeader("Authorization");
        final String jwtToken;
        final String username;
        if (tokenHeader == null || !tokenHeader.startsWith("Bearer ")) {
            //no token found || remove "Bearer " prefix
            filterChain.doFilter(request, response);
            return;
        }
        //extract token from header
        jwtToken = tokenHeader.substring(7);
        username = jwtUtil.getUsernameFromToken(jwtToken);
        //find authentication object in security context holder
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            //create authentication object
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            //validate token
            if (jwtUtil.validateToken(jwtToken)) {
                //create authentication object
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                //set authentication object in security context holder
                authenticationToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                //set authentication object in security context holder
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
