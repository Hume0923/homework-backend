package com.example.demo.mq;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RocketMqProducer {

    private final String nameServer;
    private final String group;
    private DefaultMQProducer producer;

    public RocketMqProducer(
            @Value("${rocketmq.name-server}") String nameServer,
            @Value("${rocketmq.producer.group}") String group) {
        this.nameServer = nameServer;
        this.group = group;
    }

    @PostConstruct
    public void start() throws MQClientException {
        producer = new DefaultMQProducer(group);
        producer.setNamesrvAddr(nameServer);
        producer.setVipChannelEnabled(false);
        producer.start();
    }

    @PreDestroy
    public void shutdown() {
        if (producer != null) {
            producer.shutdown();
        }
    }

    public SendResult send(String topic, String payload) throws Exception {
        Message message = new Message(topic, payload.getBytes());
        return producer.send(message);
    }
}
