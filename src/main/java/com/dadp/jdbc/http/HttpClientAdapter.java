package com.dadp.jdbc.http;

import java.io.IOException;
import java.net.URI;

/**
 * HTTP 클라이언트 어댑터 인터페이스
 * 
 * Java 버전에 따라 적절한 HTTP 클라이언트 구현을 사용합니다.
 * 
 * @author DADP Development Team
 * @version 3.0.5
 */
public interface HttpClientAdapter {
    
    /**
     * HTTP GET 요청
     * 
     * @param uri 요청 URI
     * @return HTTP 응답
     * @throws IOException IO 오류
     */
    HttpResponse get(URI uri) throws IOException;
    
    /**
     * HTTP POST 요청
     * 
     * @param uri 요청 URI
     * @param body 요청 본문
     * @return HTTP 응답
     * @throws IOException IO 오류
     */
    HttpResponse post(URI uri, String body) throws IOException;
    
    /**
     * HTTP 응답 인터페이스
     */
    interface HttpResponse {
        /**
         * HTTP 상태 코드
         * 
         * @return 상태 코드
         */
        int getStatusCode();
        
        /**
         * 응답 본문
         * 
         * @return 응답 본문 문자열
         */
        String getBody();
    }
    
    /**
     * HttpClientAdapter 팩토리
     * Java 버전에 따라 적절한 구현을 반환합니다.
     */
    class Factory {
        private static final int JAVA_VERSION = getJavaVersion();
        
        /**
         * Java 버전 확인
         * 
         * @return Java 버전 (8, 11, 17 등)
         */
        private static int getJavaVersion() {
            String version = System.getProperty("java.version");
            if (version.startsWith("1.")) {
                // Java 8 이하: "1.8.0_xxx"
                version = version.substring(2, 3);
            } else {
                // Java 9 이상: "11.0.1", "17.0.1" 등
                int dot = version.indexOf(".");
                if (dot != -1) {
                    version = version.substring(0, dot);
                }
            }
            try {
                return Integer.parseInt(version);
            } catch (NumberFormatException e) {
                // 기본값으로 Java 8로 간주
                return 8;
            }
        }
        
        /**
         * Java 버전에 따라 적절한 HttpClientAdapter 인스턴스 생성
         * 
         * @param connectTimeout 연결 타임아웃 (밀리초)
         * @param readTimeout 읽기 타임아웃 (밀리초)
         * @return HttpClientAdapter 인스턴스
         */
        public static HttpClientAdapter create(int connectTimeout, int readTimeout) {
            if (JAVA_VERSION >= 11) {
                // Java 11+ : java.net.http.HttpClient 사용 (리플렉션으로 로드)
                try {
                    Class<?> clazz = Class.forName("com.dadp.jdbc.http.Java11HttpClientAdapter");
                    java.lang.reflect.Constructor<?> constructor = clazz.getConstructor(int.class, int.class);
                    return (HttpClientAdapter) constructor.newInstance(connectTimeout, readTimeout);
                } catch (Exception e) {
                    // Java 11+ 클래스를 찾을 수 없으면 Java 8 구현 사용
                    return new Java8HttpClientAdapter(connectTimeout, readTimeout);
                }
            } else {
                // Java 8 : HttpURLConnection 사용
                return new Java8HttpClientAdapter(connectTimeout, readTimeout);
            }
        }
    }
}

