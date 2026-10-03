package com.tianji.aigc.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 文本转语音
 */
public interface AudioService {

    /**
     * 文本转语音
     * @param text 文本
     * @return 语音流
     */
    ResponseBodyEmitter ttsStream(String text);

    /**
     * 语音转文本
     * @param audio 语音
     * @return 文本
     */
    String stt(MultipartFile audio);
}
