package com.skinsshowcase.gateway.notification;

import com.skinsshowcase.gateway.config.GatewayTcpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP-прокси для уведомлений: клиент подключается к gateway, трафик проксируется в сервис messaging.
 * Протокол (JWT, OK/ERROR, NEW_MESSAGE, PING/PONG) обрабатывается на стороне messaging.
 */
@Component
public class TcpNotificationProxyServer {

    private static final Logger log = LoggerFactory.getLogger(TcpNotificationProxyServer.class);
    private static final int BUFFER_SIZE = 4096;

    private final GatewayTcpProperties tcpProperties;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        var t = new Thread(r, "tcp-notification-proxy");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running;

    public TcpNotificationProxyServer(GatewayTcpProperties tcpProperties) {
        this.tcpProperties = tcpProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running = true;
        executor.submit(this::acceptLoop);
        log.info("TCP notification proxy started on port {}, forwarding to {}:{}",
                tcpProperties.getPort(), tcpProperties.getMessagingHost(), tcpProperties.getMessagingPort());
    }

    private void acceptLoop() {
        try (var serverSocket = new ServerSocket(tcpProperties.getPort())) {
            while (running) {
                try {
                    var clientSocket = serverSocket.accept();
                    executor.submit(() -> proxyConnection(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        log.warn("TCP proxy accept error: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("TCP notification proxy server failed: {}", e.getMessage());
        }
    }

    private void proxyConnection(Socket clientSocket) {
        Socket messagingSocket = null;
        try {
            messagingSocket = new Socket(tcpProperties.getMessagingHost(), tcpProperties.getMessagingPort());
            clientSocket.setSoTimeout(0);
            messagingSocket.setSoTimeout(0);
            final var messaging = messagingSocket;
            var clientToMessaging = executor.submit(() -> copyStream(clientSocket.getInputStream(), messaging.getOutputStream(), "client->messaging"));
            copyStream(messagingSocket.getInputStream(), clientSocket.getOutputStream(), "messaging->client");
            clientToMessaging.cancel(true);
        } catch (IOException e) {
            log.debug("TCP proxy connection closed: {}", e.getMessage());
        } finally {
            closeQuietly(clientSocket);
            closeQuietly(messagingSocket);
        }
    }

    private static Void copyStream(InputStream in, OutputStream out, String direction) {
        var buffer = new byte[BUFFER_SIZE];
        try {
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException e) {
            log.trace("TCP proxy {}: {}", direction, e.getMessage());
        }
        return null;
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            log.trace("Close socket: {}", e.getMessage());
        }
    }
}
