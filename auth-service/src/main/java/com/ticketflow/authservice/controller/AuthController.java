package com.ticketflow.authservice.controller;

import com.ticketflow.authservice.dto.RegisterRequestDto;
import com.ticketflow.authservice.dto.RegisterResponseDto;
import com.ticketflow.authservice.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDto> register(
            @Valid @RequestBody RegisterRequestDto request
            ){
        log.debug("event=REGISTER_REQUEST_RECEIVED email={}", request.email());

        RegisterResponseDto response = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


}
