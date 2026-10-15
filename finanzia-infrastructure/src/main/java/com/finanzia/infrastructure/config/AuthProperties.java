package com.finanzia.infrastructure.config;

import com.finanzia.application.config.AuthSettings;
import com.finanzia.domain.common.TokenTtl;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Propiedades de autenticación ({@code finanzia.auth.*}) enlazadas desde
 * configuración y traducidas a {@link AuthSettings}.
 */
@ConfigurationProperties(prefix = "finanzia.auth")
public class AuthProperties {

    private String issuer = "finanzia";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(7);
    private Duration verifyCodeTtl = Duration.ofHours(24);
    private Duration resetCodeTtl = Duration.ofMinutes(15);
    private Duration refreshGraceWindow = Duration.ofSeconds(30);
    private Duration loginFailureWindow = Duration.ofHours(1);
    private int maxActiveSessions = 10;
    private List<Threshold> accountLockout = List.of(
            new Threshold(5, Duration.ofMinutes(5)),
            new Threshold(10, Duration.ofMinutes(15)),
            new Threshold(15, Duration.ofHours(1)));
    private List<Threshold> ipLockout = List.of(new Threshold(10, Duration.ofMinutes(15)));
    private int registerWindowSeconds = 60;
    private int registerLimit = 5;
    private int recoveryWindowSeconds = 60;
    private int recoveryLimit = 5;

    public AuthSettings toSettings() {
        return new AuthSettings(
                issuer,
                new TokenTtl(accessTokenTtl),
                new TokenTtl(refreshTokenTtl),
                verifyCodeTtl,
                resetCodeTtl,
                refreshGraceWindow,
                loginFailureWindow,
                maxActiveSessions,
                accountLockout.stream().map(Threshold::toLockout).toList(),
                ipLockout.stream().map(Threshold::toLockout).toList(),
                registerWindowSeconds,
                registerLimit,
                recoveryWindowSeconds,
                recoveryLimit);
    }

    public record Threshold(int failures, Duration lockFor) {
        AuthSettings.LockoutThreshold toLockout() {
            return new AuthSettings.LockoutThreshold(failures, lockFor);
        }
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public Duration getVerifyCodeTtl() {
        return verifyCodeTtl;
    }

    public void setVerifyCodeTtl(Duration verifyCodeTtl) {
        this.verifyCodeTtl = verifyCodeTtl;
    }

    public Duration getResetCodeTtl() {
        return resetCodeTtl;
    }

    public void setResetCodeTtl(Duration resetCodeTtl) {
        this.resetCodeTtl = resetCodeTtl;
    }

    public Duration getRefreshGraceWindow() {
        return refreshGraceWindow;
    }

    public void setRefreshGraceWindow(Duration refreshGraceWindow) {
        this.refreshGraceWindow = refreshGraceWindow;
    }

    public Duration getLoginFailureWindow() {
        return loginFailureWindow;
    }

    public void setLoginFailureWindow(Duration loginFailureWindow) {
        this.loginFailureWindow = loginFailureWindow;
    }

    public int getMaxActiveSessions() {
        return maxActiveSessions;
    }

    public void setMaxActiveSessions(int maxActiveSessions) {
        this.maxActiveSessions = maxActiveSessions;
    }

    public List<Threshold> getAccountLockout() {
        return accountLockout;
    }

    public void setAccountLockout(List<Threshold> accountLockout) {
        this.accountLockout = accountLockout;
    }

    public List<Threshold> getIpLockout() {
        return ipLockout;
    }

    public void setIpLockout(List<Threshold> ipLockout) {
        this.ipLockout = ipLockout;
    }

    public int getRegisterWindowSeconds() {
        return registerWindowSeconds;
    }

    public void setRegisterWindowSeconds(int registerWindowSeconds) {
        this.registerWindowSeconds = registerWindowSeconds;
    }

    public int getRegisterLimit() {
        return registerLimit;
    }

    public void setRegisterLimit(int registerLimit) {
        this.registerLimit = registerLimit;
    }

    public int getRecoveryWindowSeconds() {
        return recoveryWindowSeconds;
    }

    public void setRecoveryWindowSeconds(int recoveryWindowSeconds) {
        this.recoveryWindowSeconds = recoveryWindowSeconds;
    }

    public int getRecoveryLimit() {
        return recoveryLimit;
    }

    public void setRecoveryLimit(int recoveryLimit) {
        this.recoveryLimit = recoveryLimit;
    }
}