<template>
  <div class="help-page">
    <PageHeader title="使用帮助" description="快速上手 LeetModel，开启你的数学建模之旅" />

    <el-collapse v-model="activeNames" accordion class="faq-collapse">
      <!-- 快速入门 -->
      <div class="faq-category">
        <h3 class="category-title">
          <el-icon><Guide /></el-icon>
          快速入门
        </h3>
        <el-collapse-item v-for="item in gettingStarted" :key="item.title" :name="item.title">
          <template #title>
            <span class="faq-title">{{ item.title }}</span>
          </template>
          <div class="faq-content" v-html="item.content"></div>
        </el-collapse-item>
      </div>

      <!-- 训练与刷题 -->
      <div class="faq-category">
        <h3 class="category-title">
          <el-icon><EditPen /></el-icon>
          训练与刷题
        </h3>
        <el-collapse-item v-for="item in training" :key="item.title" :name="item.title">
          <template #title>
            <span class="faq-title">{{ item.title }}</span>
          </template>
          <div class="faq-content" v-html="item.content"></div>
        </el-collapse-item>
      </div>

      <!-- 账号相关 -->
      <div class="faq-category">
        <h3 class="category-title">
          <el-icon><UserFilled /></el-icon>
          账号与安全
        </h3>
        <el-collapse-item v-for="item in account" :key="item.title" :name="item.title">
          <template #title>
            <span class="faq-title">{{ item.title }}</span>
          </template>
          <div class="faq-content" v-html="item.content"></div>
        </el-collapse-item>
      </div>
    </el-collapse>

    <div class="help-footer">
      <p>没有找到答案？登录后可打开右下角 AI 客服查询平台操作，或前往<router-link to="/contact">支持渠道</router-link>。</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Guide, EditPen, UserFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'

const activeNames = ref('')

const gettingStarted = [
  { title: 'LeetModel 是什么？', content: 'LeetModel 是一个在线数学建模训练与评测平台。你可以浏览公开赛题、组建练习队伍、限时提交 PDF 论文并获得 AI 评审反馈。我们的核心理念是<strong>以模型会友，以算法相知</strong>。' },
  { title: '如何注册和登录？', content: '未登录时点击页面右上角的<strong>“注册”</strong>，填写用户名、昵称（选填）和密码即可创建账号；已有账号可直接进入<a href="/login">登录页</a>。当前版本暂不提供邮件找回密码，请妥善保管密码。' },
  { title: '平台有哪些核心功能？', content: '<ul><li><strong>题库：</strong>按赛事、年份、语言、难度和标签寻找题目</li><li><strong>组队练习：</strong>创建三人以内队伍，招募成员并分配建模、编程和论文职责</li><li><strong>论文提交：</strong>练习开始后上传 20MB 以内的 PDF，保留版本历史并确定最终稿</li><li><strong>AI 评审与建议：</strong>查看评审状态、四维结果和结构化论文建议</li><li><strong>排行榜与客服：</strong>查看已完成评审的最终稿排名，并通过 AI 客服查询平台操作</li></ul>' },
]

const training = [
  { title: '如何开始一次练习？', content: '进入<a href="/problem">题库</a>选择题目，在详情页点击<strong>“创建队伍”</strong>，或点击<strong>“寻找队伍”</strong>申请加入现有招募。队长需要让建模、编程、论文三类职责都有成员负责，然后在队伍详情中开始练习。练习开始后才会出现论文上传入口。' },
  { title: '如何提交并确定最终论文？', content: '练习开始后进入<strong>我的队伍 → 队伍详情</strong>，上传 PDF 论文。文件必须是 PDF 且不超过 20MB。你可以保留多个提交版本；练习结束后由队长从版本历史中确定最终稿，只有最终稿及其已完成评审会参与排行榜。' },
  { title: 'AI 评审是如何工作的？', content: '论文上传后会创建异步评审任务。基础评审从<strong>假设合理性、建模创造性、结果正确性、表达清晰性</strong>四个维度给出分数与评语，并列出优点、问题和改进建议。页面会展示等待、运行、完成或失败状态；失败且允许重试时会提供重试入口。' },
  { title: '题目难度是如何分级的？', content: '当前题目分为<strong>简单、中等、困难</strong>三级。难度用于辅助筛选，不代表固定得分；建议同时阅读题面要求、完成时长和所需方法后再选择。' },
  { title: '如何获取论文改进建议和查看排名？', content: '评审完成后，可在提交记录中点击论文建议入口生成结构化建议。确定最终稿且评审完成后，可从题目详情或<a href="/ranking">排行榜</a>查看本题排名。建议与评审是两个独立结果，生成失败时页面会说明原因并在允许时提供重试。' },
]

const account = [
  { title: '如何修改个人资料？', content: '登录后进入<a href="/profile/settings">个人设置</a>，可以修改头像、昵称和邮箱；用户名与注册时间只读。保存成功后，顶部头像和昵称会同步更新。' },
  { title: '如何修改密码？', content: '进入<a href="/profile/settings">个人设置</a>的“修改密码”区域，填写旧密码、新密码和确认密码。新密码长度为 6–32 位。修改成功后请使用新密码登录。' },
  { title: '当前不支持哪些账号操作？', content: '当前 MVP 暂不提供邮件找回密码、手机号与学校资料、账号注销。页面不会展示不可操作的按钮；如遗忘密码，只能联系项目维护者处理。' },
]
</script>

<style scoped>
@import './style.css';
</style>
