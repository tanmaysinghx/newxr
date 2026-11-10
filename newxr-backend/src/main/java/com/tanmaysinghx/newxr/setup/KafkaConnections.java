package com.tanmaysinghx.newxr.setup;

import com.tanmaysinghx.newxr.events.KafkaEventSink;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.stereotype.Service;

@Service
public class KafkaConnections {

    public record KafkaRequest(String bootstrapServers, String topicPrefix, String securityProtocol,
                               String saslMechanism, String username, String password) {}

    public record TestResult(boolean ok, String message, String clusterId, int brokers) {}

    public TestResult test(KafkaRequest r) {
        if (r.bootstrapServers() == null || r.bootstrapServers().isBlank()) {
            return new TestResult(false, "Enter at least one bootstrap server (host:port).", null, 0);
        }
        Properties p = KafkaEventSink.clientProperties(r.bootstrapServers(), r.securityProtocol(), r.saslMechanism(),
                r.username(), r.password());
        p.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 8000);
        p.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 8000);
        try (Admin admin = Admin.create(p)) {
            DescribeClusterResult c = admin.describeCluster(new DescribeClusterOptions().timeoutMs(8000));
            String id = c.clusterId().get(9, TimeUnit.SECONDS);
            int brokers = c.nodes().get(9, TimeUnit.SECONDS).size();
            return new TestResult(true, "Connected to cluster " + id + " (" + brokers + " broker" + (brokers == 1 ? "" : "s") + ").", id, brokers);
        } catch (Exception e) {
            Throwable t = e;
            while (t.getCause() != null) t = t.getCause();
            return new TestResult(false, "Could not reach Kafka: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()), null, 0);
        }
    }
}
