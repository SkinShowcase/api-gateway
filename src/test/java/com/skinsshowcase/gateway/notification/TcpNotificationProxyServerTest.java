package com.skinsshowcase.gateway.notification;

import com.skinsshowcase.gateway.config.GatewayTcpProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TcpNotificationProxyServerTest {

    @Test
    void start_proxiesClientBytesToMessagingAndBack() throws Exception {
        var messagingPortHolder = new AtomicReference<Integer>();
        var messagingReady = new CountDownLatch(1);
        var messagingDone = new CountDownLatch(1);

        Thread messagingSide = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(0)) {
                messagingPortHolder.set(ss.getLocalPort());
                messagingReady.countDown();
                try (Socket peer = ss.accept()) {
                    byte[] buf = new byte[32];
                    int n = peer.getInputStream().read(buf);
                    assertThat(n).isGreaterThan(0);
                    peer.getOutputStream().write("ack".getBytes());
                    peer.getOutputStream().flush();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                messagingDone.countDown();
            }
        }, "test-messaging-tcp");
        messagingSide.setDaemon(true);
        messagingSide.start();

        assertThat(messagingReady.await(5, TimeUnit.SECONDS)).isTrue();
        int messagingPort = messagingPortHolder.get();
        assertThat(messagingPort).isPositive();

        int proxyPort;
        try (ServerSocket holder = new ServerSocket(0)) {
            proxyPort = holder.getLocalPort();
        }

        var tcpProps = new GatewayTcpProperties(proxyPort, "127.0.0.1", messagingPort);
        var proxy = new TcpNotificationProxyServer(tcpProps);
        proxy.start();

        try (Socket client = waitForProxyAccept(proxyPort)) {
            client.getOutputStream().write("ping".getBytes());
            client.getOutputStream().flush();
            byte[] resp = new byte[16];
            int r = client.getInputStream().read(resp);
            assertThat(r).isEqualTo(3);
            assertThat(new String(resp, 0, r)).isEqualTo("ack");
        }

        assertThat(messagingDone.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private static Socket waitForProxyAccept(int proxyPort) throws InterruptedException, IOException {
        IOException last = null;
        for (int i = 0; i < 60; i++) {
            try {
                return new Socket("127.0.0.1", proxyPort);
            } catch (IOException e) {
                last = e;
                Thread.sleep(50);
            }
        }
        throw last != null ? last : new IOException("Could not connect to proxy on port " + proxyPort);
    }
}
