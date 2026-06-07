package com.project.airBnbApp.util;

import com.project.airBnbApp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public class AppUtils {
//    public static User getCurrentUser() {
//        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
//    }
    public static User getCurrentUser() {
        var context = SecurityContextHolder.getContext();
        var authentication = context != null ? context.getAuthentication() : null;

        if (authentication == null) {
            log.info("getCurrentUser: Authentication is null in SecurityContext");
            return null;
        }

        Object principal = authentication.getPrincipal();
        log.info("getCurrentUser: principal class = {}", principal != null ? principal.getClass().getName() : null);

        return (User) principal;
    }
}
