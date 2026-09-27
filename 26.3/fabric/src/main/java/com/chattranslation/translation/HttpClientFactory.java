package com.chattranslation.translation;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared HTTP plumbing.
 *
 * <p>Both the {@link HttpClient} and the worker pool are process-wide and reused. Rebuilding a
 * client per provider instance throws away the connection pool and keep-alive sockets, so chat
 * bursts used to pay a fresh TCP+TLS handshake for every translated line. The pool is small and
 * deliberately modest in priority because chat translation must never compete with the render
 * thread for CPU time.
 */
public final class HttpClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int WORKERS = 4;

    private static final Map<Boolean, HttpClient> CLIENTS = new ConcurrentHashMap<>();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(WORKERS, runnable -> {
        Thread thread = new Thread(runnable, "chattranslation-http");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private HttpClientFactory() {
    }

    /** Returns the shared client, building it on first use; insecure and secure clients are distinct. */
    public static HttpClient create(boolean insecureSsl) {
        return CLIENTS.computeIfAbsent(insecureSsl, HttpClientFactory::build);
    }

    /** Shared daemon pool for blocking HTTP calls; never the ForkJoin common pool. */
    public static ExecutorService executor() {
        return EXECUTOR;
    }

    private static HttpClient build(boolean insecureSsl) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(EXECUTOR);

        if (insecureSsl) {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }};
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new SecureRandom());
                builder.sslContext(sslContext);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create insecure SSL client", e);
            }
        }

        return builder.build();
    }
}
