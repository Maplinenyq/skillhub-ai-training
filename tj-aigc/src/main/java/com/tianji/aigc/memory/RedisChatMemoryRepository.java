package com.tianji.aigc.memory;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

/**
 * 基于Redis的聊天记忆仓库
 */
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    public static final String DEFAULT_PREFIX = "CHAT:";
    private final String prefix;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public RedisChatMemoryRepository() {
        this.prefix = DEFAULT_PREFIX;
    }

    public RedisChatMemoryRepository(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = this.stringRedisTemplate.keys(this.prefix + "*");
        return StreamUtil.of(keys)
                .map(key -> StrUtil.replace(key , this.prefix, ""))
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        var redisKey = this.getKey(conversationId);
        var listOps = this.stringRedisTemplate.boundListOps(redisKey);
        // 获取消息列表
        List<String> messages = listOps.range(0, -1);
        return CollStreamUtil.toList(messages, MessageUtil::toMessage);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 注意：message是全量的消息列表
        var redisKey = this.getKey(conversationId);
        var listOps = this.stringRedisTemplate.boundListOps(redisKey);

        // 将原有的消息全部删除
        this.deleteByConversationId(conversationId);
        // 保存数据到redis
        messages.forEach(message -> listOps.rightPush(MessageUtil.toJson(message)));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        var redisKey = this.getKey(conversationId);
        this.stringRedisTemplate.delete(redisKey);
    }

    private String getKey(String conversationId){
        return prefix + conversationId;
    }
}
