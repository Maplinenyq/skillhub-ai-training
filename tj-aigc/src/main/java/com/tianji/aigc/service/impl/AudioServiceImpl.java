package com.tianji.aigc.service.impl;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechSynthesisProperties;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionProperties;
import com.alibaba.cloud.ai.dashscope.audio.DashScopeSpeechSynthesisModel;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisOutput;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisPrompt;
import com.alibaba.cloud.ai.dashscope.audio.synthesis.SpeechSynthesisResponse;
import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.aigc.config.DashScopeProperties;
import com.tianji.aigc.service.AudioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioServiceImpl implements AudioService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DashScopeSpeechSynthesisModel dashScopeSpeechSynthesisModel;
    private final DashScopeAudioSpeechSynthesisProperties speechProperties;
    private final DashScopeAudioTranscriptionProperties transcriptionProperties;
    private final DashScopeProperties dashScopeProperties;

    @Override
    public ResponseBodyEmitter ttsStream(String text) {
        log.info("开始文字转语音 {}", text);
        // 定义输出响应对象
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();
        //调用DashScope TTS模型进行文本转语音
        SpeechSynthesisPrompt speechPrompt = new SpeechSynthesisPrompt(text, speechProperties.getOptions());
        // 获取流式输出
        Flux<SpeechSynthesisResponse> streamResponse = this.dashScopeSpeechSynthesisModel.stream(speechPrompt);
        // 订阅流式输出，将大模型响应的内容以流式的方式响应给前端
        streamResponse.subscribe(speechResponse -> {
                    try {
                        // 响应输出内容
                        SpeechSynthesisOutput output = speechResponse.getResult().getOutput();
                        if (output == null || output.getAudio() == null) {
                            return;   // 结束帧的 output 是 null，跳过（日志最后那条就是）
                        }
                        ByteBuffer audio = output.getAudio();
                        byte[] audioBytes = new byte[audio.remaining()];
                        audio.get(audioBytes);          // 相对读取，只读缓冲区也允许
                        // 发送给前端
                        emitter.send(audioBytes);
                    } catch (IOException e) {
                        log.error("文字转语音错误", e);
                        emitter.completeWithError(e);
                    }
                },
                emitter::completeWithError,
                emitter::complete
        );
        return emitter;
    }

    /**
     * 语音转文字。ApiFox测试通过，但是实测不通，百炼SDK有问题，暂不完成
     */
    @Override
    public String stt(MultipartFile audio) {
        var options = this.transcriptionProperties.getOptions();
        RecognitionParam param = RecognitionParam.builder()
                .apiKey(this.dashScopeProperties.getKey())
                .model(options.getModel())
                .format(options.getFormat().getValue())
                .sampleRate(options.getSampleRate())
                .build();

        File temp = null;
        try {
            temp = File.createTempFile("stt-", "." + param.getFormat());
            audio.transferTo(temp);
            String json = new Recognition().call(param, temp);
            log.info("语音识别原始结果 {}", json);
            return extractText(json);
        } catch (Exception e) {
            throw new IllegalStateException("语音识别失败", e);
        }
    }

    /**
     * 从 JSON 中提取文本内容
     */
    private String extractText(String json) throws JsonProcessingException {
        var sb = new StringBuilder();
        MAPPER.readTree(json).path("sentences").forEach(s -> sb.append(s.path("text").asText()));
        return sb.toString();
    }
}
