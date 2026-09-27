package com.tianji.aigc.agent;

import cn.hutool.core.map.MapUtil;
import com.alibaba.dashscope.app.Application;
import com.alibaba.dashscope.app.ApplicationParam;
import com.alibaba.dashscope.app.ApplicationResult;
import com.alibaba.dashscope.utils.JsonUtils;
import io.reactivex.Flowable;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class AppTest {

    @Test

    public void testAppCall() throws Exception {
        // 构造业务参数
        String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJ1c2VyIjp7InVzZXJJZCI6Miwicm9sZUlkIjoyLCJyZW1lbWJlck1lIjpmYWxzZX0sImV4cCI6MTc5MjkxNjEwNH0.SeY8-zYNNgHb206WpuubFiEogsSlwk9gpg7RXipEKCYrMCNytJLqu27I6pTW059A6c1Fe0DTK1Tn3pfv62VtbZqBaDFkCScqb4IKzwfwv9T9bVX0j3mhu2u2BVax8e-GddZ-qjgg7v2OPhz-HGYsRxcVj3XDZF2KZcxF8aCTM1gNt-miEfEVzUuc4kk8HyKLUob8eqrnLO9_ISVdI0jHZQzflDALJRCxlMnOPlKv_mzh2iG--_4CRPhMnvGVrxCHRFYLjdggy0uTCfIZmKb-k-ignJbJFaZrz1eB8FTANyRRAtjF-rfQmgdnB7Hkttv74ZjNDt4PhML-fCmutEkBkg";
        Map<String, Object> bizParams = MapUtil.<String, Object>builder()
                .put("user_defined_tokens", MapUtil.of("tool_6455e71e-e63f-496f-8be4-e964a692a606", // 工具id
                        MapUtil.of("user_token", token)))
                .build();

        // bizParams.add("user_defined_tokens", JsonObject);
        ApplicationParam param = ApplicationParam.builder()
                // 若没有配置环境变量，可用百炼API Key将下行替换为：.apiKey("sk-xxx")。但不建议在生产环境中直接将API Key硬编码到代码中，以减少API Key泄露风险。
                .apiKey("sk-ws-H.PLLPIXI.0ywL.MEYCIQCEsTTqOym1Xx-bgI0k8YCJl0E79PdznDQQ7yjRN0pPRwIhAJIAYuiJNjGKppvF_FyBSCBfDcaX9OZ_SOD3jtztt0eM")
                .appId("2fe3a24429434801a1de59cf7e159c75") // 智能体id
                .prompt("查询课程，id为：1880533253575225346")
                .incrementalOutput(true) // 开启增量输出
                .bizParams(JsonUtils.toJsonObject(bizParams))
                .build();

        Application application = new Application();
        Flowable<ApplicationResult> result = application.streamCall(param);

        // 阻塞式的打印内容
        result.blockingForEach(data -> {
            System.out.printf("%s\n",data.getOutput().getText());
        });

    }

}
