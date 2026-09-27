package com.tianji.aigc.controller;

import com.tianji.aigc.dto.ChatDTO;
import com.tianji.aigc.service.ChatService;
import com.tianji.aigc.vo.ChatEventVO;
import com.tianji.aigc.vo.TemplateVO;
import com.tianji.common.annotations.NoWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RequestMapping("/chat")
@RestController
@Slf4j
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private static final TemplateVO TEMPLATE_VO = new TemplateVO();

    /**
     * 聊天
     * @param chatDTO 聊天参数
     * @return 聊天结果
     */
    @NoWrapper
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventVO> chat(@RequestBody ChatDTO chatDTO) {
        return chatService.chat(chatDTO.getQuestion(),chatDTO.getSessionId());
    }

    /**
     * 停止聊天
     * @param sessionId 会话ID
     */
    @PostMapping("/stop")
    public void stop(@RequestParam("sessionId") String sessionId) {
        this.chatService.stop(sessionId);
    }

    /**
     * 聊天
     * @param question 问题
     * @return 聊天结果
     */
    @PostMapping("/text")
    public String chatText(@RequestBody String question) {
        return this.chatService.chatText(question);
    }

    /**
     * 获取模板
     * @return 模板
     */
    @GetMapping("/templates")
    public TemplateVO getTemplates() {
        return TEMPLATE_VO;
    }
}
