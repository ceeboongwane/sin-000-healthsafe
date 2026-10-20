package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import java.net.http.*;
import java.net.URI;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.wethinkcode.healthsafe.mq.MqConfig;

import javax.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

public class WardServiceApp {

    private static List<Map> wards = new ArrayList<>();
    private static Map<String, Map> staffingUpdates = new HashMap<>();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));

        // TODO (Provides lists of wards and departments.)
        // Add domain endpoints for ward-service here.
        loadFromIngestion();

        app.get("/init", ctx -> {
            loadFromIngestion();
            ctx.json(Map.of("loaded", wards.size()));
        });

        app.get("/wards", ctx -> ctx.json(wards));

        app.get("/wards/{id}", ctx -> {
            String id = ctx.pathParam("id").toUpperCase();
            Map ward = findWard(id);
            if (ward == null) {
                ctx.status(404).json(Map.of("error", "Ward not found: " + id));
            } else {
                if (staffingUpdates.containsKey(id)) {
                    Map combined = new LinkedHashMap<>(ward);
                    combined.put("latest_staffing_event", staffingUpdates.get(id));
                    ctx.json(combined);
                } else {
                    ctx.json(ward);
                }
            }
        });

        app.get("/departments", ctx -> {
            Set<String> depts = new LinkedHashSet<>();
            for (Map w : wards) {
                Object d = w.get("department");
                if (d != null) depts.add(d.toString());
            }
            ctx.json(depts);
        });

        // Stage 4: trigger equipment failure
        app.post("/wards/{id}/equipment-failure", ctx -> {
            String id = ctx.pathParam("id").toUpperCase();
            if (findWard(id) == null) {
                ctx.status(404).json(Map.of("error", "Ward not found"));
                return;
            }
            Map<String, Object> alert = Map.of(
                    "ward_id", id,
                    "equipment", ctx.queryParam("equipment") != null ? ctx.queryParam("equipment") : "ventilator",
                    "severity", "CRITICAL",
                    "timestamp", new Date().toString()
            );
            publishToQueue(alert);
            ctx.json(Map.of("status", "sent", "alert", alert));
        });

        subscribeToTopic();

        System.out.println("WardService on 7031 - " + wards.size() + " wards");
    }

    private static void loadFromIngestion() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:7030/wards")).GET().build();
            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
            wards = new ObjectMapper().readValue(body, List.class);
        } catch (Exception e) {
            System.out.println("Ingestion not ready: " + e.getMessage());
        }
    }

    private static Map findWard(String id) {
        return wards.stream()
                .filter(w -> id.equals(w.get("ward_id")) || id.equals(w.get("id")))
                .map(w -> (Map)w)
                .findFirst().orElse(null);
    }

    // Uses MqConfig.TOPIC at MqConfig.BROKER_URL
    private static void subscribeToTopic() {
        new Thread(() -> {
            try {
                ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
                Connection conn = factory.createConnection();
                conn.start();
                Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Topic topic = session.createTopic(MqConfig.TOPIC);
                MessageConsumer consumer = session.createConsumer(topic);

                consumer.setMessageListener(msg -> {
                    try {
                        if (msg instanceof TextMessage) {
                            String json = ((TextMessage) msg).getText();
                            System.out.println("[TOPIC " + MqConfig.TOPIC + "] " + json);
                            Map update = new ObjectMapper().readValue(json, Map.class);
                            String wid = update.getOrDefault("ward_id", "UNKNOWN").toString().toUpperCase();
                            staffingUpdates.put(wid, update);
                        }
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
                System.out.println("Subscribed to " + MqConfig.TOPIC + " @ " + MqConfig.BROKER_URL);
            } catch (Exception e) {
                System.out.println("Topic not available yet - run: cd common && docker compose up -d");
            }
        }).start();
    }

    // Publishes to MqConfig.QUEUE when equipment failure
    private static void publishToQueue(Map<String, Object> alert) {
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            Connection conn = factory.createConnection();
            conn.start();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            javax.jms.Queue  queue = session.createQueue(MqConfig.QUEUE);
            MessageProducer producer = session.createProducer(queue);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            String json = new ObjectMapper().writeValueAsString(alert);
            producer.send(session.createTextMessage(json));
            System.out.println("[QUEUE " + MqConfig.QUEUE + "] Sent: " + json);

            producer.close();
            session.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("Failed to publish to queue: " + e.getMessage());
        }
    }
}

// MQ TODO: subscribes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
// MQ TODO: publishes to ActiveMQ queue MqConfig.QUEUE when it detects an equipment failure on one of its wards.

