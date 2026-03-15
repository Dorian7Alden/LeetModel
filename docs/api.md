# LeetModel API 接口文档

本文档描述 LeetModel（力模）平台的后端接口设计，采用 RESTful 风格。

- **Base URL**: `/api/v1`
- **Protocol**: HTTP/HTTPS
- **Data Format**: JSON
- **Authentication**: JWT (Put token in Header: `Authorization: Bearer <token>`)

## 1. 通用响应结构

所有接口默认返回以下 JSON 结构：

```json
{
  "code": 200,, // 业务状态码，200 表示成功
  "message": "success", // 提示信息
  "data": { ... } // 业务数据
}
```

## 2. 认证模块 (Auth)

### 2.1 用户注册
- **URL**: `/auth/register`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "username": "user1",
    "email": "user1@example.com",
    "password": "secure_password",
    "role": "modeler" // 初始角色偏好: modeler(建模手), programmer(编程手), writer(论文手)
  }
  ```

### 2.2 用户登录
- **URL**: `/auth/login`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "email": "user1@example.com",
    "password": "secure_password"
  }
  ```
- **Response**:
  ```json
  {
    "token": "eyJhbGciOiJIUzI1...",
    "expires_in": 3600
  }
  ```

## 3. 用户模块 (User)

### 3.1 获取当前用户信息
- **URL**: `/users/me`
- **Method**: `GET`
- **Response**:
  ```json
  {
    "id": 1,
    "username": "user1",
    "email": "user1@example.com",
    "avatar": "url",
    "current_role": "modeler", // 当前激活的角色身份
    "level": 5,
    "title": "建模新星"
  }
  ```

### 3.2 更新用户信息
- **URL**: `/users/me`
- **Method**: `PUT`
- **Body**:
  ```json
  {
    "username": "new_name",
    "avatar": "new_url",
    "current_role": "programmer" // 切换角色
  }
  ```

### 3.3 获取用户能力雷达图数据
- **URL**: `/users/{id}/stats`
- **Method**: `GET`
- **Response**:
  ```json
  {
    "overall_score": 85,
    "dimensions": [
      { "name": "建模能力", "score": 80 },
      { "name": "编程能力", "score": 60 },
      { "name": "论文写作", "score": 75 },
      { "name": "团队协作", "score": 90 }
    ]
  }
  ```

### 3.4 获取用户成长轨迹
- **URL**: `/users/{id}/growth-history`
- **Method**: `GET`

## 4. 题库模块 (Problem)

### 4.1 获取题目列表
- **URL**: `/problems`
- **Method**: `GET`
- **Query Params**:
  - `page`: 页码 (default: 1)
  - `size`: 每页数量 (default: 20)
  - `type`: 题目类型 (model, code, paper)
  - `tag`: 标签 ID
  - `difficulty`: 难度 (easy, medium, hard)
  - `keyword`: 搜索关键词
- **Response**:
  ```json
  {
    "total": 100,
    "items": [
      {
        "id": 101,
        "title": "人口增长模型预测",
        "difficulty": "medium",
        "tags": ["预测模型", "国赛真题"],
        "pass_rate": "45%"
      }
    ]
  }
  ```

### 4.2 获取题目详情
- **URL**: `/problems/{id}`
- **Method**: `GET`
- **Response**:
  ```json
  {
    "id": 101,
    "title": "人口增长模型预测",
    "content": "题目详细描述 Markdown...",
    "files": ["url_to_data.csv"], // 附件数据
    "limitations": {
      "time": "1000ms",
      "memory": "256MB"
    }
  }
  ```

## 5. 训练与提交模块 (Submission)

### 5.1 提交题目答案
- **URL**: `/problems/{id}/submit`
- **Method**: `POST`
- **Content-Type**: `multipart/form-data` (支持文件上传)
- **Body**:
  - `code`: 代码内容 (如果是编程题)
  - `content`: 文本内容/论文摘要
  - `file`: 附件文件 (论文 PDF 或 结果数据)
  - `language`: 语言 (python, matlab, latex 等)

### 5.2 获取提交记录列表
- **URL**: `/submissions`
- **Method**: `GET`
- **Query Params**:
  - `problem_id`: 题目 ID
  - `user_id`: 用户 ID (仅管理员或本人)

### 5.3 获取提交详情与 AI 评阅结果
- **URL**: `/submissions/{id}`
- **Method**: `GET`
- **Response**:
  ```json
  {
    "id": 5001,
    "status": "reviewed", // pending, processing, reviewed, failed
    "score": 88,
    "ai_review": {
      "summary": "模型建立较为合理，但在参数敏感性分析上略显不足。",
      "dimensions": [
        { "name": "逻辑性", "score": 90, "comment": "..." },
        { "name": "规范性", "score": 85, "comment": "..." }
      ],
      "suggestions": "建议增加对参数 alpha 的扰动测试..."
    }
  }
  ```

## 6. 赛事模拟模块 (Competition)

### 6.1 获取赛事/模拟列表
- **URL**: `/competitions`
- **Method**: `GET`
- **Query Params**:
  - `status`: upcoming, ongoing, ended

### 6.2 报名/加入模拟赛
- **URL**: `/competitions/{id}/join`
- **Method**: `POST`

### 6.3 获取赛事详情（含试题）
- **URL**: `/competitions/{id}`
- **Method**: `GET`
- **Note**: 只有已报名的用户在赛事开始后可见试题。

### 6.4 赛事提交
- **URL**: `/competitions/{id}/submit`
- **Method**: `POST`
- **Note**: 类似题目提交，但关联到赛事 ID。

## 7. 组队模块 (Team)

### 7.1 获取组队招募列表
- **URL**: `/teams/recruitments`
- **Method**: `GET`
- **Query Params**:
  - `competition_target`: 目标赛事 (国赛/美赛)
  - `role_needed`: 缺少的角色

### 7.2 发布组队招募
- **URL**: `/teams`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "name": "冲刺国赛一等奖队",
    "target": "2024国赛",
    "description": "已有建模和编程，缺论文手...",
    "requirements": "有获奖经历优先"
  }
  ```

### 7.3 申请加入队伍
- **URL**: `/teams/{id}/apply`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "message": "我擅长 LaTeX 排版..."
  }
  ```

### 7.4 获取我的队伍信息
- **URL**: `/teams/my`
- **Method**: `GET`

## 8. 社区交流模块 (Community)

### 8.1 获取帖子列表
- **URL**: `/posts`
- **Method**: `GET`
- **Query Params**:
  - `category`: 经验分享, 提问, 资料
  - `sort`: new, hot

### 8.2 发布帖子
- **URL**: `/posts`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "title": "美赛 O 奖经验分享",
    "content": "Markdown content...",
    "category": "experience"
  }
  ```

### 8.3 发表评论
- **URL**: `/posts/{id}/comments`
- **Method**: `POST`
