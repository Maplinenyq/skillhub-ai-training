package com.tianji.aigc.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.aigc.config.SessionProperties;
import com.tianji.aigc.entity.ChatSession;
import com.tianji.aigc.enums.MessageTypeEnum;
import com.tianji.aigc.mapper.ChatSessionMapper;
import com.tianji.aigc.memory.MyAssistantMessage;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.ChatSessionVO;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.aigc.vo.SessionVO;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    private final SessionProperties sessionProperties;
    private final ChatMemory chatMemory;

    /**
     * 创建会话session
     *
     * @param num 热门问题的数量
     * @return 会话信息
     */
    @Override
    public SessionVO createSession(Integer num) {
        var sessionVO = BeanUtil.toBean(this.sessionProperties, SessionVO.class);
        // 随机生成三个热门课题
        sessionVO.setExamples(RandomUtil.randomEleList(sessionProperties.getExamples(), num));
        // 生成sessionID
        sessionVO.setSessionId(IdUtil.simpleUUID());
        // 保存会话数据到数据库
        ChatSession chatSession = ChatSession.builder()
                .sessionId(sessionVO.getSessionId())
                .userId(UserContext.getUser()) //当前用户ID
                .build();// 保存会话数据到数据库
        super.save(chatSession);
        return sessionVO;
    }

    /**
     * 获取热门会话
     *
     * @param num 热门会话的数量
     * @return 热门会话列表
     */
    @Override
    public List<SessionVO.Example> getHotExamples(Integer num) {
        return RandomUtil.randomEleList(sessionProperties.getExamples(), num);
    }

    /**
     * 根据会话id查询会话信息
     *
     * @param sessionId 会话id
     * @return 会话信息
     */
    @Override
    public List<MessageVO> queryBySessionId(String sessionId) {
        // 将SessionId转换为对话Id
        String conversionId = ChatService.getConversationId(sessionId);

        // 查询对话列表
        List<Message> messageList = this.chatMemory.get(conversionId);

        // 转换为VO列表
        return StreamUtil.of(messageList)
                .filter(message -> message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.ASSISTANT)
                .map(message -> {
                    if(message instanceof MyAssistantMessage myAssistantMessage){
                        return MessageVO.builder()
                                .type(MessageTypeEnum.valueOf(myAssistantMessage.getMessageType().name()))
                                .content(myAssistantMessage.getText())
                                .params(myAssistantMessage.getParams())
                                .build();
                    }
                    return MessageVO.builder()
                            .type(MessageTypeEnum.valueOf(message.getMessageType().name()))
                            .content(message.getText())
                            .build();
                })
                .toList();
    }

    /**
     * 更新会话信息
     *
     * @param sessionId 会话id
     * @param title     会话标题
     * @param userId    用户id
     */
    @Async // 异步更新会话信息
    @Override
    public void update(String sessionId, String title, Long userId) {
        List<ChatSession> chatSessionList = super.lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, userId)
                .list();
        if (CollUtil.isEmpty(chatSessionList)) {
            return;
        }
        // 获取第一个对象数据
        ChatSession chatSession = chatSessionList.get(0);

        if(StrUtil.isEmpty(chatSession.getTitle()) && StrUtil.isNotEmpty(title)){
            // 更新会话标题，限制长度为不超过100个字符
            chatSession.setTitle(StrUtil.sub(title, 0, 100));
        }
        // 设置更新时间
        chatSession.setUpdateTime(LocalDateTime.now());
        super.updateById(chatSession); // 更新数据
    }

    /**
     * 查询历史会话
     *
     * @return 历史会话列表
     */
    @Override
    public Map<String, List<ChatSessionVO>> queryHistorySession() {
        // 根据条件查询会话列表
        List<ChatSession> chatSessionList = super.lambdaQuery()
                .eq(ChatSession::getUserId, UserContext.getUser())
                .isNotNull(ChatSession::getTitle)
                .orderByDesc(ChatSession::getUpdateTime)
                .last("limit 30")
                .list();
        if(CollUtil.isEmpty(chatSessionList)){
            return Map.of();
        }
        // 转化为vo对象
        List<ChatSessionVO> chatSessionVOList = CollStreamUtil.toList(chatSessionList, chatSession -> ChatSessionVO.builder()
                .sessionId(chatSession.getSessionId())
                .title(chatSession.getTitle())
                .updateTime(chatSession.getUpdateTime())
                .build());
        // 根据时间差进行数据分组
        final var TODAY = "当天";
        final var LAST_30_DAYS = "最近30天";
        final var LAST_YEAR = "最近1年";
        final var MORE_THAN_YEAR = "1年以前";
        // 获取当前日期
        var now = LocalDateTime.now().toLocalDate();
        return CollStreamUtil.groupByKey(chatSessionVOList, chatSessionVO -> {
            long days = Math.abs(ChronoUnit.DAYS.between(chatSessionVO.getUpdateTime().toLocalDate(), now));// 计算时间差
            if(days == 0) return TODAY;
            else if(days <= 30) return LAST_30_DAYS;
            else if(days <= 365) return LAST_YEAR;
            else return MORE_THAN_YEAR;
        });
    }

    /**
     * 更新会话标题
     *
     * @param sessionId 会话id
     * @param title     会话标题
     */
    @Override
    public void updateSessionTitle(String sessionId, String title) {
        super.lambdaUpdate()
                // 设置更新条件,更新字段为title（最多设置100个字）
                .set(ChatSession::getTitle, StrUtil.sub(title, 0, 100))
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, UserContext.getUser())
                .update();
    }

    @Override
    public void deleteHistorySession(String sessionId) {
        // 删除历史会话需要删除，需要删除数据库和chatMemory中的数据
        var lambdaQueryWrapper = Wrappers.<ChatSession>lambdaQuery() // 构造删除条件
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getUserId, UserContext.getUser());
        // 删除数据库中的数据
        super.remove(lambdaQueryWrapper);
        // 获取对话ID
        String conversionId = ChatService.getConversationId(sessionId);
        this.chatMemory.clear(conversionId);
    }
}
