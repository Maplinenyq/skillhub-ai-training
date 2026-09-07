package com.tianji.aigc.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.aigc.config.SessionProperties;
import com.tianji.aigc.entity.ChatSession;
import com.tianji.aigc.enums.MessageTypeEnum;
import com.tianji.aigc.mapper.ChatSessionMapper;
import com.tianji.aigc.memory.MyAssistantMessage;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.service.ChatSessionService;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.aigc.vo.SessionVO;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
