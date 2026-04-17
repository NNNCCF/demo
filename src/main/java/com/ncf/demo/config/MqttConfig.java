package com.ncf.demo.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;

@Configuration
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {
    @Bean
    public MqttPahoClientFactory mqttClientFactory(AppProperties appProperties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{appProperties.getEmqx().getBrokerUrl()});
        options.setUserName(appProperties.getEmqx().getUsername());
        options.setPassword(appProperties.getEmqx().getPassword().toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageProducer inbound(MqttPahoClientFactory mqttClientFactory, AppProperties appProperties) {
        String[] topics = {
                "/device/+/data",
                "$SYS/brokers/+/clients/+/disconnected"
        };
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                appProperties.getEmqx().getClientId() + "-inbound",
                mqttClientFactory,
                topics
        );
        adapter.setCompletionTimeout(5000);
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInboundChannel());
        return adapter;
    }

    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow mqttInboundFlow(com.ncf.demo.service.MqttMessageService mqttMessageService) {
        return IntegrationFlow.from(mqttInboundChannel())
                .handle((payload, headers) -> {
                    mqttMessageService.handleIncoming(new GenericMessage<>(payload, headers));
                    return null;
                })
                .get();
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageHandler mqttOutbound(MqttPahoClientFactory mqttClientFactory, AppProperties appProperties) {
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler(
                appProperties.getEmqx().getClientId() + "-outbound",
                mqttClientFactory
        );
        messageHandler.setAsync(true);
        messageHandler.setDefaultQos(1);
        return messageHandler;
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow(MessageHandler mqttOutbound) {
        return IntegrationFlow.from(mqttOutboundChannel())
                .handle(mqttOutbound)
                .get();
    }

    @MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
    public interface MqttGateway {
        @Gateway
        void sendToMqtt(String payload, @org.springframework.messaging.handler.annotation.Header(MqttHeaders.TOPIC) String topic);
    }
}
