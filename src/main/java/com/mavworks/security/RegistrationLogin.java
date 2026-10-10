package com.mavworks.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Uses Spring Security servlet integration and rotates the pre-login session ID. */
@Component
public class RegistrationLogin {
    public void signIn(HttpServletRequest request, String username, String password) throws ServletException {
        request.login(username, password);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
    }
}
