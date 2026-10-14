package dev.wordrush.botcli;

import dev.wordrush.core.Difficulty;
import dev.wordrush.core.FeedbackTable;
import dev.wordrush.core.WordList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Usage:
 * <pre>
 * java -jar bot/build/libs/wordrush-bot.jar --room ABC234 [--count 3] [--difficulty MEDIUM]
 *      [--server http://localhost:8080] [--think-scale 1.0] [--name cli] [--stay]
 * </pre>
 * Each bot signs in as a bot-labelled guest, joins the room over REST, then plays over STOMP.
 * Bots leave after one game unless --stay is given.
 */
public final class BotMain {

    private BotMain() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        String room = options.get("room");
        if (room == null) {
            System.err.println("Missing --room CODE. Example: --room ABC234 --count 3 --difficulty HARD");
            System.exit(2);
        }
        String server = options.getOrDefault("server", "http://localhost:8080");
        int count = Integer.parseInt(options.getOrDefault("count", "1"));
        Difficulty difficulty = Difficulty.valueOf(options.getOrDefault("difficulty", "MEDIUM").toUpperCase(Locale.ROOT));
        double thinkScale = Double.parseDouble(options.getOrDefault("think-scale", "1.0"));
        String name = options.getOrDefault("name", "cli");
        boolean stay = options.containsKey("stay");

        FeedbackTable table = FeedbackTable.of(WordList.loadDefault().answers());
        WebSocketStompClient stompClient = stompClient();
        String wsUrl = server.replaceFirst("^http", "ws").replaceAll("/+$", "") + "/ws";
        ApiClient api = new ApiClient(server);
        CountDownLatch finished = new CountDownLatch(count);
        List<BotClient> bots = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            ApiClient.Session session = api.signIn(name + "_" + difficulty.name().toLowerCase(Locale.ROOT) + i);
            api.join(session, room.toUpperCase(Locale.ROOT));
            BotClient bot = new BotClient(session, room.toUpperCase(Locale.ROOT), difficulty, thinkScale, table,
                    !stay, finished);
            bot.connect(stompClient, wsUrl);
            bots.add(bot);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> bots.forEach(bot -> bot.finish("stopped"))));

        finished.await();
        stompClient.stop();
        System.exit(0);
    }

    private static WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        ThreadPoolTaskScheduler heartbeats = new ThreadPoolTaskScheduler();
        heartbeats.setPoolSize(1);
        heartbeats.setThreadNamePrefix("stomp-heartbeat-");
        heartbeats.initialize();
        client.setTaskScheduler(heartbeats);
        client.setDefaultHeartbeat(new long[] {10_000, 10_000});
        return client;
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                continue;
            }
            String key = args[i].substring(2);
            boolean hasValue = i + 1 < args.length && !args[i + 1].startsWith("--");
            options.put(key, hasValue ? args[++i] : "true");
        }
        return options;
    }
}
