package com.tianji.aigc.service;

import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.common.utils.UserContext;
import reactor.core.publisher.Flux;

public interface ChatService {

    static String getConversationId(String sessionId) {
        return UserContext.getUser() + "_" + sessionId;
    }

    /**
     * 聊天
     * @param question 问题
     * @param sessionId 会话ID
     * @return 聊天结果
     */
    Flux<ChatEventVO> chat(String question, String sessionId);

    /**
     * 停止聊天
     * @param sessionId 会话ID
     */
    void stop(String sessionId);
}
