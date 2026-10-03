package com.tianji.aigc.controller;

import com.tianji.aigc.service.AudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@RequestMapping("/audio")
@RequiredArgsConstructor
@RestController
public class AudioController {

    private final AudioService audioService;

    /**
     * 文本转语音
     * @param text 文本
     * @return 语音流
     */
    @PostMapping(value = "/tts-stream" , produces = "audio/mp3")
    public ResponseBodyEmitter ttsStream(@RequestBody String text) {
        return this.audioService.ttsStream(text);
    }

    /**
     * 语音转文本
     * @param audio 语音
     * @return 文本
     */
    @PostMapping(value = "/stt")
    public String stt(@RequestParam("audioFile") MultipartFile audio){
        return this.audioService.stt(audio);
    }

}
