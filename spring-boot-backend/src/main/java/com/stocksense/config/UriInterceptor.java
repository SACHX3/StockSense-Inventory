package com.stocksense.config;

import com.stocksense.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
@RequiredArgsConstructor
public class UriInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        if (modelAndView != null) {
            modelAndView.addObject("currentUri", request.getRequestURI());

            // Expose the logged-in user's avatar path to every page (used by the
            // sidebar's account block) without every controller needing to fetch it.
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                userRepository.findByUsername(auth.getName())
                        .ifPresent(u -> modelAndView.addObject("currentUserAvatar", u.getAvatarPath()));
            }
        }
    }
}
