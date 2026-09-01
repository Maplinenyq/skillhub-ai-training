package com.tianji.aigc.service.impl;


import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final SystemPromptConfig systemPromptConfig;
    private final StringRedisTemplate redisTemplate;

    // 会话生成状态标记，完整 key = AIGC:CHAT:GENERATE_STATUS:{sessionId}
    // key 存在 = 正在生成；key 不存在 = 已停止
    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS";

    /**
     * 聊天
     * @param question 问题
     * @param sessionId 会话ID
     * @return 聊天结果
     */
    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        String key = GENERATE_STATUS_KEY + sessionId;
        return this.chatClient.prompt()
                .system(promptSystem -> promptSystem
                        .text(this.systemPromptConfig.getChatSystemMessage().get())
                        .params(Map.of("now" , DateUtils.now()))
                )
                .user(question)
                .stream()
                .chatResponse()
                .doFirst(() -> redisTemplate.opsForValue().set(key, "true")) // 会话开始时添加会话ID
                .doOnError(e -> redisTemplate.delete(key)) // 会话出错时移除会话ID
                .doOnComplete(() -> redisTemplate.delete(key)) // 会话完成时移除会话ID
                .takeWhile(response -> redisTemplate.hasKey(key)) // 会话进行时，继续生成
                .map(response-> {
                    // 大模型生成的内容
                    var text = response.getResult().getOutput().getText();
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue()) // 数据事件
                            .build();
                })
                .concatWith(Flux.just(ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.STOP.getValue()) // 数据结束事件
                        .build()));
    }

    /**
     * 停止聊天
     * @param sessionId 会话ID
     */
    @Override
    public void stop(String sessionId) {
        redisTemplate.delete(GENERATE_STATUS_KEY + sessionId);
    }
}
