package com.eclakehouse.auth.web;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
