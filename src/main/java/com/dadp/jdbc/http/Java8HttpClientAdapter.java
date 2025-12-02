package com.dadp.jdbc.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Java 8용 HTTP 클라이언트 어댑터
 * HttpURLConnection을 사용합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.5
 */
class Java8HttpClientAdapter implements HttpClientAdapter {
    
    private final int connectTimeout;
    private final int readTimeout;
    
    public Java8HttpClientAdapter(int connectTimeout, int readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }
    
    @Override
    public HttpResponse get(URI uri) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        
        return readResponse(conn);
    }
    
    @Override
    public HttpResponse post(URI uri, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setDoOutput(true);
        
        // 요청 본문 전송
        try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8")) {
            writer.write(body);
            writer.flush();
        }
        
        return readResponse(conn);
    }
    
    private HttpResponse readResponse(HttpURLConnection conn) throws IOException {
        int statusCode = conn.getResponseCode();
        String responseBody = null;
        
        // 응답 본문 읽기
        if (statusCode >= 200 && statusCode < 300) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                responseBody = readAll(reader);
            }
        } else {
            // 에러 응답 읽기
            if (conn.getErrorStream() != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), "UTF-8"))) {
                    responseBody = readAll(reader);
                }
            }
        }
        
        final int finalStatusCode = statusCode;
        final String finalBody = responseBody;
        
        return new HttpResponse() {
            @Override
            public int getStatusCode() {
                return finalStatusCode;
            }
            
            @Override
            public String getBody() {
                return finalBody;
            }
        };
    }
    
    private String readAll(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }
}

