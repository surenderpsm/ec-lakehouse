package com.eclakehouse.common.client;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import com.eclakehouse.common.security.CurrentUser;

/** Forwards the caller's bearer token on service-to-service calls. */
public class TokenRelayInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            CurrentUser.token().ifPresent(t -> request.getHeaders().setBearerAuth(t));
        }
        return execution.execute(request, body);
    }
}
