package com.solocrew;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SSLConfig {

    @PostConstruct
    public void configureSSL() {
        // Configure TLS for better compatibility
        System.setProperty("https.protocols", "TLSv1.2,TLSv1.3");
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2,TLSv1.3");
        System.setProperty("javax.net.ssl.trustStore", System.getProperty("java.home") + "/lib/security/cacerts");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
        
        System.out.println("SSL configuration applied globally");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("TLS protocols: " + System.getProperty("https.protocols"));
    }
}