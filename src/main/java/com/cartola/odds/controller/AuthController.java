package com.cartola.odds.controller;

import com.cartola.odds.controller.api.AuthApi;
import com.cartola.odds.model.request.LoginRequest;
import com.cartola.odds.model.response.LoginResponse;
import com.cartola.odds.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthService authService;

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest request) {
        log.info("POST /api/auth/login - Autenticando...");
        return ResponseEntity.ok(authService.login(request));
    }
}
