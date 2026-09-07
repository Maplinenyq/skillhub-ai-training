package com.tianji.aigc.service.impl;


import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.SystemPromptConfig;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    public static final ChatEventVO STOP_EVENT = ChatEventVO.builder()
            .eventType(ChatEventTypeEnum.STOP.getValue())
            .build();
    private final ChatClient chatClient;
    private final SystemPromptConfig systemPromptConfig;
    private final StringRedisTemplate stringRedisTemplate;
    private final ChatMemory chatMemory;

    // 通过一个容器，保存当前会话的会话ID 以及 是否继续生成的标识，用于后续停止会话
    // 容器实现：1、使用Map， 2、如果考虑到分布式场景的话，需要使用redis
    // private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();
    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS";

    /**
     * 聊天
     * @param question 问题
     * @param sessionId 会话ID
     * @return 聊天结果
     */
    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 获取会话ID对应的Redis操作对象
        var hashOps = this.stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        // 获取会话ID
        var conversationId = ChatService.getConversationId(sessionId);
        // 创建一个大模型输出缓存器，用于输出中断的信息储存
        var outputBuilder = new StringBuilder();
        // 生成请求ID
        var requestId = IdUtil.fastSimpleUUID();
        // 获取用户ID
        var userId = UserContext.getUser();
        return this.chatClient.prompt()
                .system(promptSystem -> promptSystem
                        .text(this.systemPromptConfig.getChatSystemMessage().get())
                        .params(Map.of("now" , DateUtils.now()))
                )
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId)) // 设置对话记忆里的对话ID
                .toolContext(Map.of(Constant.REQUEST_ID, requestId, Constant.USER_ID, userId)) //通过工具上下文传递参数
                .user(question)
                .stream()
                .chatResponse()
                .doFirst(() -> hashOps.put(sessionId, "true")) // 会话开始时添加会话ID
                .doOnError(e -> hashOps.delete(sessionId)) // 会话出错时移除会话ID
                .doOnComplete(() -> hashOps.delete(sessionId)) // 会话完成时移除会话ID
                .doOnCancel(() -> {
                    // 打断输出的事件
                    this.saveStopRecord(conversationId, outputBuilder.toString());
                })
                .takeWhile(response -> hashOps.get(sessionId) != null) // 会话进行时，继续生成，
                .map(response-> {
                    // 大模型生成的内容
                    var text = response.getResult().getOutput().getText();
                    // 将大模型生成的内容追加到缓存器中
                    outputBuilder.append(text);

                    // 获取结束原因
                    String finishReason = response.getResult().getMetadata().getFinishReason();
                    if(StrUtil.equals(finishReason, Constant.STOP)){
                        // 获取消息Id
                        var messageId = response.getMetadata().getId();
                        // 将消息ID和请求ID相关联
                        ToolResultHolder.put(messageId , Constant.REQUEST_ID , requestId);
                    }
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue()) // 数据事件
                            .build();
                })
                .concatWith(Flux.defer(() -> {
                    Map<String, Object> result = ToolResultHolder.get(requestId);
                    if(ObjectUtil.isNotEmpty(result)){
                        ToolResultHolder.remove(requestId);
                        // 工具被调用了，需要向前端传递参数
                        return Flux.just(ChatEventVO.builder()
                                .eventType(ChatEventTypeEnum.PARAM.getValue())
                                .eventData(result)
                                .build(), STOP_EVENT);
                    }
                    return Flux.just(STOP_EVENT); // 结束标识
                }));
    }

    /**
     * 保存停止输出的记录
     * @param conversationId 会话ID
     * @param content 停止输出的内容
     */
    public void saveStopRecord(String conversationId, String content) {
        this.chatMemory.add(conversationId, new AssistantMessage(content));
    }

    /**
     * 停止聊天
     * @param sessionId 会话ID
     */
    @Override
    public void stop(String sessionId) {
        var hashOps = this.stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        hashOps.delete(sessionId);
    }
}
