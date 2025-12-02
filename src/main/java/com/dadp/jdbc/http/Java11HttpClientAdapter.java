package com.dadp.jdbc.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Java 11+용 HTTP 클라이언트 어댑터
 * java.net.http.HttpClient를 사용합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.5
 */
class Java11HttpClientAdapter implements HttpClientAdapter {
    
    private final HttpClient httpClient;
    private final int readTimeout;
    
    public Java11HttpClientAdapter(int connectTimeout, int readTimeout) {
        this.readTimeout = readTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .build();
    }
    
    @Override
    public HttpResponse get(URI uri) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(readTimeout))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            return new HttpResponse() {
                @Override
                public int getStatusCode() {
                    return response.statusCode();
                }
                
                @Override
                public String getBody() {
                    return response.body();
                }
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }
    
    @Override
    public HttpResponse post(URI uri, String body) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(readTimeout))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            return new HttpResponse() {
                @Override
                public int getStatusCode() {
                    return response.statusCode();
                }
                
                @Override
                public String getBody() {
                    return response.body();
                }
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }
}

