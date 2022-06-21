package com.uci.adapter.firebase.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.uci.adapter.provider.factory.AbstractProvider;
import com.uci.adapter.provider.factory.IProvider;
import com.uci.utils.BotService;
import com.uci.utils.service.VaultService;
import io.r2dbc.postgresql.codec.Json;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.MessageId;
import messagerosa.core.model.SenderReceiverInfo;
import messagerosa.core.model.XMessage;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import javax.xml.bind.JAXBException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Getter
@Setter
@Builder
public class FirebaseMessageAdapter  extends AbstractProvider implements IProvider {
    @Autowired
    public BotService botService;

    @Autowired
    public VaultService vaultService;

    @Override
    public Mono<XMessage> convertMessageToXMsg(Object message) throws JAXBException, JsonProcessingException {
        return null;
    }

    @Override
    public Mono<XMessage> processOutBoundMessageF(XMessage nextMsg) throws Exception {
        SenderReceiverInfo to = nextMsg.getTo();
        Map<String, String> meta = to.getMeta();
        if(meta != null && meta.get("fcmToken") != null) {
            return botService.getAdapterByID(nextMsg.getAdapterId()).map(new Function<JsonNode, Mono<Mono<XMessage>>>() {
                @Override
                public Mono<Mono<XMessage>> apply(JsonNode adapter) {
                    log.info("adapter: "+adapter);
                    if(adapter != null) {
                        String vaultKey;
                        try{
                            vaultKey = adapter.path("config").path("credentials").path("variable").asText();
                        } catch (Exception ex) {
                            log.error("Exception in fetching adapter variable from json node: "+ex.getMessage());
                            vaultKey = null;
                        }

                        if(vaultKey != null && !vaultKey.isEmpty()) {
                            return vaultService.getAdpaterCredentials(vaultKey).map(new Function<JsonNode, Mono<XMessage>>(){
                                @Override
                                public Mono<XMessage> apply(JsonNode credentials) {
                                    log.info("credentials: "+credentials);
                                    if(credentials != null && credentials.path("serviceKey") != null
                                            && !credentials.path("serviceKey").asText().isEmpty()) {
                                        return (new FirebaseMessagingService()).sendNotificationMessage(credentials.path("serviceKey").asText(), meta.get("fcmToken"), "Firebase Notification", nextMsg.getPayload().getText())
                                            .map(new Function<Boolean, XMessage>() {
                                                @Override
                                                public XMessage apply(Boolean result) {
                                                    if (result) {
                                                        to.setMeta(null);
                                                        nextMsg.setTo(to);
                                                        nextMsg.setMessageId(MessageId.builder().channelMessageId(LocalDateTime.now().toString()).build());
                                                        nextMsg.setMessageState(XMessage.MessageState.SENT);
                                                    }
                                                    return nextMsg;
                                                }
                                            });
                                    }
                                    return Mono.just(null);
                                }
                            });
                        }
                    }
                    return Mono.just(Mono.just(null));
                }
            }).flatMap(new Function<Mono<Mono<XMessage>>, Mono<XMessage>>() {
                @Override
                public Mono<XMessage> apply(Mono<Mono<XMessage>> m) {
                    log.info("Mono FlatMap Level 1");
                    return m.flatMap(new Function<Mono<XMessage>, Mono<? extends XMessage>>() {
                        @Override
                        public Mono<? extends XMessage> apply(Mono<XMessage> n) {
                            log.info("Mono FlatMap Level 2");
                            return n;
                        }
                    });
                }
            });

        }
        return null;
    }

//    @Override
//    public Mono<XMessage> processOutBoundMessageF(XMessage nextMsg) throws Exception {
//        SenderReceiverInfo to = nextMsg.getTo();
//        Map<String, String> meta = to.getMeta();
//        if(meta != null && meta.get("fcmToken") != null) {
//            return botService.getGupshupAdpaterCredentials(nextMsg.getAdapterId()).map(new Function<Map<String, String>, Mono<XMessage>>() {
//                @Override
//                public Mono<XMessage> apply(Map<String, String> credentials) {
//                    if(credentials.get("serverKey") != null && !credentials.get("serverKey").isEmpty()) {
//                        return (new FirebaseMessagingService()).sendNotificationMessage(credentials.get("serverKey"), meta.get("fcmToken"), "Firebase Notification", nextMsg.getPayload().getText())
//                                .map(new Function<Boolean, XMessage>() {
//                                    @Override
//                                    public XMessage apply(Boolean result) {
//                                        if (result) {
//                                            to.setMeta(null);
//                                            nextMsg.setTo(to);
//                                            nextMsg.setMessageId(MessageId.builder().channelMessageId(LocalDateTime.now().toString()).build());
//                                            nextMsg.setMessageState(XMessage.MessageState.SENT);
//                                        }
//                                        return nextMsg;
//                                    }
//                                });
//                    }
//                    return Mono.just(null);
//                }
//            }).flatMap(new Function<Mono<XMessage>, Mono<? extends XMessage>>() {
//                @Override
//                public Mono<? extends XMessage> apply(Mono<XMessage> o) {
//                    return o;
//                }
//            });
//        }
//        return null;
//    }
}
