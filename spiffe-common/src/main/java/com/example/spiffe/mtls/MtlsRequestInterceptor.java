package com.example.spiffe.mtls;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Interceptor to add SPIFFE identity header to mTLS requests.
 * This is optional - the certificate itself contains the SPIFFE ID.
 */
@Slf4j
public class MtlsRequestInterceptor implements ClientHttpRequestInterceptor {
    private final String spiffeId;
    
    public MtlsRequestInterceptor(String spiffeId) {
        this.spiffeId = spiffeId;
    }
    
    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        
        // Add SPIFFE identity header (optional - certificate also contains this)
        if (spiffeId != null) {
            request.getHeaders().add("X-SPIFFE-Identity", spiffeId);
        }
        
        return execution.execute(request, body);
    }
}




