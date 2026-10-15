package com.finanzia.api.web;

import com.finanzia.api.web.dto.ChangePasswordRequest;
import com.finanzia.api.web.dto.ForgotPasswordRequest;
import com.finanzia.api.web.dto.LoginRequest;
import com.finanzia.api.web.dto.LoginResponse;
import com.finanzia.api.web.dto.LogoutRequest;
import com.finanzia.api.web.dto.MessageResponse;
import com.finanzia.api.web.dto.RefreshRequest;
import com.finanzia.api.web.dto.RegisterRequest;
import com.finanzia.api.web.dto.ResendVerificationRequest;
import com.finanzia.api.web.dto.ResetPasswordRequest;
import com.finanzia.api.web.dto.TokenResponse;
import com.finanzia.api.web.dto.VerifyEmailRequest;
import com.finanzia.application.service.ChangePasswordService;
import com.finanzia.application.service.LoginService;
import com.finanzia.application.service.RefreshSessionService;
import com.finanzia.application.service.RegisterUserService;
import com.finanzia.application.service.ResetPasswordService;
import com.finanzia.application.service.SessionManagementService;
import com.finanzia.application.service.VerifyEmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Endpoints de autenticación. Los casos de uso son síncronos y se ejecutan en
 * el pool elástico; los errores se traducen en {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String RESEND_RESPONSE =
            "Se recibió la solicitud. Si el email requiere verificación, se enviará un nuevo código.";
    private static final String RESET_REQUEST_RESPONSE =
            "Se recibió la solicitud. Si el email está registrado, se enviará un código de recuperación.";

    private final RegisterUserService registerUserService;
    private final VerifyEmailService verifyEmailService;
    private final LoginService loginService;
    private final RefreshSessionService refreshSessionService;
    private final SessionManagementService sessionManagementService;
    private final ChangePasswordService changePasswordService;
    private final ResetPasswordService resetPasswordService;

    public AuthController(RegisterUserService registerUserService,
                          VerifyEmailService verifyEmailService,
                          LoginService loginService,
                          RefreshSessionService refreshSessionService,
                          SessionManagementService sessionManagementService,
                          ChangePasswordService changePasswordService,
                          ResetPasswordService resetPasswordService) {
        this.registerUserService = registerUserService;
        this.verifyEmailService = verifyEmailService;
        this.loginService = loginService;
        this.refreshSessionService = refreshSessionService;
        this.sessionManagementService = sessionManagementService;
        this.changePasswordService = changePasswordService;
        this.resetPasswordService = resetPasswordService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<MessageResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                          ServerWebExchange exchange) {
        return ReactiveCall.async(() -> registerUserService.register(
                        request.email(), request.password(), RequestContext.ip(exchange),
                        RequestContext.device(exchange), RequestContext.correlationId(exchange)))
                .map(message -> ResponseEntity.accepted().body(new MessageResponse(message)));
    }

    @PostMapping("/verify-email")
    public Mono<ResponseEntity<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request,
                                                  ServerWebExchange exchange) {
        return ReactiveCall.asyncRun(() -> verifyEmailService.verify(
                        request.code(), RequestContext.device(exchange),
                        RequestContext.ip(exchange), RequestContext.correlationId(exchange)))
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/verify-email/resend")
    public Mono<ResponseEntity<MessageResponse>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request, ServerWebExchange exchange) {
        return ReactiveCall.asyncRun(() -> verifyEmailService.resend(
                        request.email(), RequestContext.ip(exchange),
                        RequestContext.device(exchange), RequestContext.correlationId(exchange)))
                .thenReturn(ResponseEntity.accepted().body(new MessageResponse(RESEND_RESPONSE)));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                     ServerWebExchange exchange) {
        return ReactiveCall.async(() -> loginService.login(
                        request.email(), request.password(), RequestContext.ip(exchange),
                        RequestContext.device(exchange), RequestContext.correlationId(exchange)))
                .map(result -> ResponseEntity.ok(LoginResponse.from(result)));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request,
                                                       ServerWebExchange exchange) {
        return ReactiveCall.async(() -> refreshSessionService.refresh(
                        request.refreshToken(), RequestContext.device(exchange),
                        RequestContext.ip(exchange), RequestContext.correlationId(exchange)))
                .map(pair -> ResponseEntity.ok(TokenResponse.from(pair)));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(@Valid @RequestBody LogoutRequest request,
                                             ServerWebExchange exchange) {
        return ReactiveCall.asyncRun(() -> sessionManagementService.logout(
                        request.refreshToken(), RequestContext.ip(exchange),
                        RequestContext.device(exchange), RequestContext.correlationId(exchange)))
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/logout-all")
    public Mono<ResponseEntity<Void>> logoutAll(ServerWebExchange exchange) {
        return ReactiveCall.currentUser()
                .flatMap(user -> ReactiveCall.asyncRun(() -> sessionManagementService.logoutAll(
                        user.userId(), RequestContext.ip(exchange),
                        RequestContext.device(exchange), RequestContext.correlationId(exchange))))
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/change-password")
    public Mono<ResponseEntity<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                     ServerWebExchange exchange) {
        return ReactiveCall.currentUser()
                .flatMap(user -> ReactiveCall.asyncRun(() -> changePasswordService.changePassword(
                        user.userId(), request.currentPassword(), request.newPassword(), user.sessionId(),
                        RequestContext.ip(exchange), RequestContext.device(exchange),
                        RequestContext.correlationId(exchange))))
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/forgot-password")
    public Mono<ResponseEntity<MessageResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                                ServerWebExchange exchange) {
        return ReactiveCall.asyncRun(() -> resetPasswordService.requestReset(
                        request.email(), RequestContext.device(exchange),
                        RequestContext.ip(exchange), RequestContext.correlationId(exchange)))
                .thenReturn(ResponseEntity.accepted().body(new MessageResponse(RESET_REQUEST_RESPONSE)));
    }

    @PostMapping("/reset-password")
    public Mono<ResponseEntity<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                    ServerWebExchange exchange) {
        return ReactiveCall.asyncRun(() -> resetPasswordService.resetPassword(
                        request.code(), request.newPassword(), RequestContext.ip(exchange),
                        RequestContext.device(exchange), RequestContext.correlationId(exchange)))
                .thenReturn(ResponseEntity.noContent().build());
    }
}