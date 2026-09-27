package com.tianji.aigc.agent;

import com.alibaba.dashscope.app.Application;
import com.alibaba.dashscope.app.ApplicationParam;
import com.alibaba.dashscope.app.ApplicationResult;
import io.reactivex.Flowable;
import org.junit.jupiter.api.Test;

/**
 * 测试 工作流应用-任务型-天机AI助手
 */
public class AppTest2 {

    @Test
    public void testAppCall() throws Exception {
        ApplicationParam param = ApplicationParam.builder()
                // 若没有配置环境变量，可用百炼API Key将下行替换为：.apiKey("sk-xxx")。但不建议在生产环境中直接将API Key硬编码到代码中，以减少API Key泄露风险。
                .apiKey("sk-ws-H.PLLPIXI.0ywL.MEYCIQCEsTTqOym1Xx-bgI0k8YCJl0E79PdznDQQ7yjRN0pPRwIhAJIAYuiJNjGKppvF_FyBSCBfDcaX9OZ_SOD3jtztt0eM")
                .appId("91fc836ca5c345218ce019af914d2189") // 智能体id
                // .prompt("你好")
                .prompt("课程推荐")
                .incrementalOutput(true) // 开启增量输出
                .parameter("flow_stream_mode", "agent_format")
                // .bizParams(JsonUtils.toJsonObject(bizParams))
                .build();

        Application application = new Application();
        Flowable<ApplicationResult> result = application.streamCall(param);

        // 阻塞式的打印内容
        result.blockingForEach(data -> {
            System.out.printf("%s\n",data.getOutput().getText());
        });

    }

}
