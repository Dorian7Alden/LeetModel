

<p align="center">
	<img src="https://gitee.com/kualk/pic-go/raw/master/imgs/image-20260423174510998.png" alt="LeetModel Logo" height="80px">
</p>
<h1 align="center">LeetModel</h1>

<div align="center" style="display: flex; justify-content: center; gap: 2px; flex-wrap: wrap;">
	<img src="https://img.shields.io/badge/开发中-8B0000?logoColor=white&color=8B0000" alt="开发中">
	<img src="https://img.shields.io/badge/Vue%203-1A237E?logo=vue.js&logoColor=4FC08D&color=1A237E" alt="Vue 3">
	<img src="https://img.shields.io/badge/Spring%20Boot%203-1B5E20?logo=springboot&logoColor=6DB33F&color=1B5E20" alt="Spring Boot 3">
	<img src="https://img.shields.io/badge/MySQL-01579B?logo=mysql&logoColor=F29111&color=01579B" alt="MySQL">
	<img src="https://img.shields.io/badge/AI-8B0000?logoColor=FF6A00&color=BF360C" alt="AI">
</div>




### 项目介绍

LeetModel（力模），是一款对标 LeetCode 的**数学建模**领域垂直在线实训平台，核心聚焦数学建模赛事全链路能力提升。

平台深耕数学建模赛事实训场景，以体系化训练为根基、AI 智能技术为赋能抓手，完整覆盖主流数学建模赛事全流程实践环节，为建模学习者、参赛团队提供从零基础入门到赛事高阶进阶的一站式成长解决方案。

​	

### 项目结构

```text
LeetModel/                                   # 项目根目录
├─ README.md                                 
├─ TODO.md                                   # 待办事项记录
├─ docs/                                     # 文档中心（重点）
│  ├─ README.md                              # 文档导航页
│  ├─ memo.md                                # 临时备忘录与灵感草稿，开发时不看这个，开发新功能时再看
│  ├─ LeetModelStarUML.mdj                   # UML 建模文件，可视化图
│  ├─ instructions/                          # 开发规范目录
│  │  ├─ 00-development-setup.md             # 开发前同步流程说明
│  │  ├─ 01-document-regulations.md          # 文档撰写规范
│  │  └─ 02-response-regulations.md          # HTTP 响应规范
│  └─ system-design/                         # 系统设计文档目录
│     ├─ v1/                                 # v1 定稿设计
│     └─ 草稿/                                # 设计草稿（开发时不参考）
├─ LeetModel-backend/                        # 后端工程
├─ LeetModel-vue/                            # 前端工程
├─ prompts/                                  # 提示词资源目录
└─ sqls/                                     # 数据库脚本目录
```



### 版本规划

> readme 中只描述大版本目标



#### v1.0.0

当前版本聚焦于题目的**提交**与**审核反馈**，暂不涉及用户训练提升功能。

即优先保障用户提交论文后能获得评价内容与相关建议，专项训练模块留待后续开发。



### 开发说明

当前版本进度：v0.1.0

#### 核心原则



为确保开发方向一致、减少无效返工，请遵循以下核心原则与流程：

- **版本聚焦原则**
  每个小版本应**只聚焦一个核心功能、目标或模块**，避免范围蔓延。

- **文档先行原则**
  项目以设计文档为唯一开发依据，**通过文档理解项目，而非分析源代码**。源代码必须向设计文档看齐。

- **设计文档定位：指引，而非约束**
  设计文档的核心是“指明方向”，而不是“规定细节”：
  
  - **说明做什么**：以自然语言描述功能、方法、模型或交互方式，提供方法论和方向。
  - **不限制怎么做**：不给出接口路径、参数校验逻辑等具体实现细节，至多包含辅助理解的代码示例。
  
    > 例如：描述“用户登录接口”、“用户注册接口”即可，无需指定具体路径或校验规则。



#### 要求



- **阅读规范与设计**：

  - 开发前，应先逐层浏览项目的目录结构，而不是一次性遍历所有目录。逐层了解后，根据任务目标，选择性地阅读必要的内容，再着手完成任务。

  - 开发规范说明：`docs/instructions/00-development-setup.md`

  - 对应功能的系统设计：`docs/system-design` 目录下的相关文档

- **制定版本计划**：先规划当前版本的任务安排，再开始编码实现，完成后迭代至下一版本。

- **关键目录速查**：
  - 开发规范：`docs/instructions/`
  - 功能设计：`docs/system-design`

- **文档与数据库版本同步**：同一版本的文档须对应同一版本的数据库。数据库的版本与对应的系统设计版本对齐。





