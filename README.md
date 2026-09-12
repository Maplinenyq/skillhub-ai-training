# 智行云——B2C 在线培训平台

智行云是面向职业培训机构的一站式在线学习与内容运营平台，采用微服务架构，覆盖内容发布、课程检索、订单支付、学习进度、互动测评及退款售后等场景，形成“内容上线—用户购买—学习交付—考试评价”的业务闭环。

> 项目基于公开教学项目进行学习与二次开发，重点完成了会话隔离、流式问答、聊天记忆及业务工具调用等功能升级。

## 项目功能

### 学员端

- 课程分类、搜索及详情查看
- 课程购买、支付及退款
- 视频学习与进度回放
- 在线考试、评价与互动
- AI 课程问答与课程推荐

### 运营端

- 课程分类、章节及媒资管理
- 课程草稿编辑、发布与上下架
- 优惠活动与订单管理
- 学习数据及运营数据统计

## 技术架构

- 基础框架：Java 17、Spring Boot、MyBatis-Plus
- 微服务：Spring Cloud Alibaba、Nacos、Gateway、OpenFeign、Sentinel
- 数据存储：MySQL、Redis、MongoDB、Elasticsearch
- 消息与任务：RabbitMQ、XXL-JOB、DelayQueue
- 智能服务：Spring AI、DashScope
- 接口文档：Knife4j

## 系统架构

```mermaid
flowchart LR
    Web[学员端/运营端] --> Gateway[Gateway]
    Gateway --> Auth[认证服务]
    Gateway --> Course[内容服务]
    Gateway --> Trade[交易服务]
    Gateway --> Learning[学习服务]
    Gateway --> Search[搜索服务]
    Gateway --> AIGC[智能服务]

    Course --> MQ[RabbitMQ]
    MQ --> Search
    Search --> ES[Elasticsearch]

    Trade --> Pay[支付服务]
    Learning --> Redis[Redis]
    AIGC --> MongoDB[MongoDB]
    AIGC --> Model[DashScope]
