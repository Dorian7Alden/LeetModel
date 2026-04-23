

<h1 align="center"><img src="https://gitee.com/kualk/pic-go/raw/master/imgs/image-20260423174510998.png" alt="LeetModel Logo" height="80px" style="vertical-align: middle; margin-right: 10px;">LeetModel</h1>

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



### 项目文件树

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



### 开发说明

- 项目的开发以文档设计为主，而不是通过分析源代码来获得项目的信息，源代码向设计文档靠齐。文档先行。
- 设计文档不需要完全具体的设计，主要是起指导开发作用，用什么方法、模型开发这个功能，这个功能应该是怎样交互的，指导开发，但是不限制开发，只给方法论，开发方向，以自然语言为主描述项目功能，不给出具体的代码，顶多给一个代码示例，例如：用户登录的接口；用户注册的接口这种描述，不给出这个接口的路径名等具体的内容

- 在进行文档设计或者写代码实现业务之前，必须阅读：`docs/instructions/00-development-setup.md `开发规范说明；`docs/system-design` 中对应的功能设计说明
- 开发之前，先制定对应版本的计划安排，然后再实现版本功能，再迭代到下一个版本
- 开发的时候，相同版本的文档对应相同版本的数据库
- 开发规范目录：`docs/instructions/`
- 功能设计目录：`docs/system-design`
- 约定：每次开始开发前，先查看相关文档同步信息，再进行开发

