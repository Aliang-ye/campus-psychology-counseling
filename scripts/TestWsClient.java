import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

public class TestWsClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java TestWsClient <userId> <sessionId> [holdSeconds]");
            return;
        }
        String userId = args[0];
        String sessionId = args[1];
        int holdSeconds = 20;
        if (args.length >= 3) {
            try { holdSeconds = Integer.parseInt(args[2]); } catch (Exception e) { }
        }
        String uri = "ws://127.0.0.1:8080/ws/chat?userId=" + userId;
        CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();
        WebSocket ws = client.newWebSocketBuilder().buildAsync(URI.create(uri), new Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                System.out.println("Connected as user " + userId);
                webSocket.request(1);
                // send a test message
                String payload = String.format("{\"sessionId\":%s,\"senderId\":%s,\"content\":\"hello_from_%s\"}", sessionId, userId, userId);
                webSocket.sendText(payload, true);
                System.out.println("Sent: " + payload);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                System.out.println("[recv user" + userId + "] " + data);
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                System.out.println("Closed: " + statusCode + " " + reason);
                latch.countDown();
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                System.out.println("Error: " + error.getMessage());
                latch.countDown();
            }
        }).join();

        // wait to receive messages while holding the connection
        Thread.sleep(holdSeconds * 1000L);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").thenRun(() -> System.out.println("Close requested"));
        latch.await();
    }
}
