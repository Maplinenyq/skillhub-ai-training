package com.tianji.aigc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.aigc.entity.ChatSession;
import com.tianji.aigc.vo.ChatSessionVO;
import com.tianji.aigc.vo.MessageVO;
import com.tianji.aigc.vo.SessionVO;

import java.util.List;
import java.util.Map;

public interface ChatSessionService extends IService<ChatSession> {

    /**
     * 创建会话session
     *
     * @param num 热门问题的数量
     * @return 会话信息
     */
    SessionVO createSession(Integer num);

    /**
     * 获取热门会话
     *
     * @param num 热门会话的数量
     * @return 热门会话列表
     */
    List<SessionVO.Example> getHotExamples(Integer num);

    /**
     * 根据会话id查询会话信息
     *
     * @param sessionId 会话id
     * @return 会话信息
     */
    List<MessageVO> queryBySessionId(String sessionId);

    /**
     * 更新会话信息
     *
     * @param sessionId 会话id
     * @param title 会话标题
     * @param userId 用户id
     */
    void update(String sessionId , String title , Long userId);

    /**
     * 查询历史会话
     *
     * @return 历史会话
     */
    Map<String, List<ChatSessionVO>> queryHistorySession();

    /**
     * 更新会话标题
     *
     * @param sessionId 会话id
     * @param title 会话标题
     */
    void updateSessionTitle(String sessionId, String title);

    /**
     * 删除会话
     *
     * @param sessionId 会话id
     */
    void deleteHistorySession(String sessionId);
}
