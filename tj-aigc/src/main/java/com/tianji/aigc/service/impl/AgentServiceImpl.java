package com.tianji.aigc.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.tianji.aigc.agent.Agent;
import com.tianji.aigc.enums.AgentTypeEnum;
import com.tianji.aigc.enums.ChatEventTypeEnum;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 基于路由工作流模式实现的智能体
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai", name = "chat-type", havingValue = "ROUTE")
public class AgentServiceImpl implements ChatService {

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 实现思路，先把问题发给路由智能体，然后根据路由智能体的返回结果，调用不同的智能体
        Agent routeAgent = this.findAgentByType(AgentTypeEnum.ROUTE);
        String routeResult = routeAgent.process(question, sessionId);

        // 将结果转换为枚举，如果能转换成功，说明需要路由转换到其他的智能体执行，否则返回原始结果
        AgentTypeEnum agentType = AgentTypeEnum.agentNameOf(routeResult);
        Agent agent = this.findAgentByType(agentType);
        if(agent == null){
            // 没有找到对应的智能体，返回原始结果
            return Flux.just(ChatEventVO.builder()
                    .eventType(ChatEventTypeEnum.DATA.getValue())
                    .eventData(routeResult)
                    .build());
        }
        // 找到智能体，调用对应的智能体处理问题
        return agent.processStream(question, sessionId);
    }

    /**
     * 根据智能体类型查找智能体
     * @param agentTypeEnum 智能体类型
     * @return 智能体, 如果没有找到则返回null
     */
    public Agent findAgentByType(AgentTypeEnum agentTypeEnum){
        if(agentTypeEnum == null){
            return null;
        }
        // 查找spring中所有Agent实例
        Map<String, Agent> agents = SpringUtil.getBeansOfType(Agent.class);
        // 遍历所有实例，找到对应的智能体
        for (Agent agent : agents.values()) {
            if (agent.getAgentType().equals(agentTypeEnum)) {
                // 返回对应的实例
                return agent;
            }
        }
        // 如果没有找到对应的智能体，则返回null
        return null;
    }

    @Override
    public void stop(String sessionId) {
        var routeAgent = this.findAgentByType(AgentTypeEnum.ROUTE);
        routeAgent.stop(sessionId);
    }
}
