package com.tianji.aigc.tools;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.api.client.course.CourseClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CourseTools {

    private final CourseClient courseClient;
    private static final String FIELD_NAME_FORMAT = "{}_{}";

    @Tool(description = Constant.Tools.QUERY_COURSE_BY_ID)
    public CourseInfo queryCourseById(@ToolParam(description = Constant.ToolParams.COURSE_ID) Long courseId , ToolContext toolContext){
        return Optional.ofNullable(courseId)
                .map(id -> this.courseClient.baseInfo(id , true)) // 获取课程基础信息
                .map(CourseInfo::of) // 转换为 CourseInfo
                .map(courseInfo -> {
                    // 将结果存储到容器之中
                    var requestId = MapUtil.get(toolContext.getContext() , Constant.REQUEST_ID , String.class);
                    var field = StrUtil.format(FIELD_NAME_FORMAT, StrUtil.lowerFirst(CourseInfo.class.getSimpleName()), courseId);
                    ToolResultHolder.put(requestId, field, courseInfo);
                    return courseInfo;
                })
                .orElse(null);
    }
}
