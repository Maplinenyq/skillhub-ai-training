package com.tianji.aigc.agent;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class AbstractAgent implements Agent {

    private static final ChatEventVO STOP_EVENT = ChatEventVO.builder()
            .eventType(ChatEventTypeEnum.STOP.getValue()) // 停止事件
            .build();
    @Resource
    private ChatClient chatClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ChatMemory chatMemory;
    @Resource
    private ChatSessionService chatSessionService;

    private static final String GENERATE_STATUS_KEY = "GENERATE_STATUS";

    /**
     * 处理用户输入，返回流式输出
     *
     * @param question 用户输入
     * @param sessionId 会话ID
     * @return 生成的内容
     */
    @Override
    public Flux<ChatEventVO> processStream(String question, String sessionId) {
        var hashOps = this.stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        // 生成请求ID
        var requestId = generateRequestId();
        // 获取会话ID
        var conversationId = ChatService.getConversationId(sessionId);
        // 创建一个可变字符串，用于存储生成的内容
        var outputBuilder = StrUtil.builder();
        // 获取用户ID
        var userId = UserContext.getUser();
        // 更新会话时间
        this.chatSessionService.update(sessionId , question , userId);
        return getChatClientRequest(question, sessionId, requestId)
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
     * 处理用户输入，返回生成的内容
     *
     * @param question 用户输入
     * @param sessionId 会话ID
     * @return 生成的内容
     */
    @Override
    public String process(String question, String sessionId) {
        // 获取用户ID
        var userId = UserContext.getUser();
        // 更新会话时间
        this.chatSessionService.update(sessionId , question , userId);
        // 生成请求ID
        var requestId = generateRequestId();
        return getChatClientRequest(question, sessionId, requestId)
                .call()
                .content();
    }

    @NotNull
    private ChatClient.ChatClientRequestSpec getChatClientRequest(String question, String sessionId, String requestId) {
        // 1. 获取system提示词需要的参数（包含now）
        Map<String, Object> sysParams = systemMessageParams();
        // 2. 获取原来的advisorParams
        Map<String, Object> advisorParams = this.advisorParams(sessionId, null);
        // 合并两套参数，advisorParams会覆盖同名key
        Map<String, Object> systemRenderParams = new HashMap<>(sysParams);
        systemRenderParams.putAll(advisorParams);

        return this.chatClient.prompt()
                .system(promptSystemSpec -> promptSystemSpec
                        .text(this.systemMessage())
                        .params(systemRenderParams)) // 使用合并后的参数！
                .advisors(advisorSpec -> advisorSpec
                        .advisors(this.advisors()).params(this.advisorParams(sessionId, requestId)))
                .tools(this.tools())
                .toolContext(this.toolContext(sessionId, requestId))
                .user(question);
    }


    @Override
    public void stop(String sessionId) {
        var hashOps = this.stringRedisTemplate.boundHashOps(GENERATE_STATUS_KEY);
        hashOps.delete(sessionId);
    }

    private String generateRequestId(){
        return IdUtil.fastSimpleUUID();
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
     * 获取顾问参数
     * @param sessionId 会话ID
     * @param requestId 请求ID
     * @return 顾问参数
     */
    @Override
    public Map<String, Object> advisorParams(String sessionId, String requestId) {
        return Map.of(ChatMemory.CONVERSATION_ID , ChatService.getConversationId(sessionId));
    }

    // 在AbstractAgent抽象类新增抽象方法
    public abstract Map<String, Object> systemMessageParams();
}
