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
      <p>没有找到你想要的答案？<router-link to="/contact">联系我们</router-link>，我们随时为你提供帮助。</p>
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
  { title: '如何注册账号？', content: '点击页面右上角的<strong>"注册"</strong>按钮，填写用户名、邮箱和密码即可完成注册。注册后建议完善个人资料（学校、专业方向等），以便系统为你推荐合适的题目和队伍。' },
  { title: '平台有哪些核心功能？', content: '<ul><li><strong>题库训练：</strong>按赛事、年份、语言和难度浏览数学建模题目</li><li><strong>组队练习：</strong>组建三人以内队伍并分配建模、编程和论文职责</li><li><strong>限时提交：</strong>在题目时限内提交可追溯的 PDF 论文版本</li><li><strong>AI 评审：</strong>异步评审论文并向练习队员共享结果</li></ul>' },
]

const training = [
  { title: '如何开始训练？', content: '进入<strong>"题库"</strong>页面，浏览题目列表。你可以通过左侧的标签筛选（模型类型、难度等级、算法方法等）找到适合自己的题目。点击题目卡片进入详情页，查看题目描述、提交要求和过往作品。准备好后点击<strong>"提交作品"</strong>即可开始。' },
  { title: 'AI 评审是如何工作的？', content: '系统会从<strong>五个维度</strong>对你的作品进行自动评估：<br><br><strong>1. 模型设计 (30%)：</strong>模型选择是否合理，是否具有创新性<br><strong>2. 算法实现 (25%)：</strong>代码质量、效率和正确性<br><strong>3. 数据处理 (15%)：</strong>数据预处理和特征工程的质量<br><strong>4. 结果分析 (20%)：</strong>可视化和分析的完整性<br><strong>5. 论文质量 (10%)：</strong>论文结构和表达的规范性<br><br>每个维度会给出具体分数和改进建议。' },
  { title: '题目难度是如何分级的？', content: '题目分为<strong>入门、中等、困难、挑战</strong>四个等级：<br><br><strong>入门：</strong>适合初学者，覆盖基础知识<br><strong>中等：</strong>需要一定建模基础，综合应用多个知识点<br><strong>困难：</strong>面向有经验的建模者，要求深入分析与优化<br><strong>挑战：</strong>竞赛级别难度，接近国赛/美赛真题水平' },
  { title: '作品提交有什么要求？', content: '提交作品时请确保包含以下内容：<br><br>1. <strong>模型描述：</strong>简要说明你采用的建模思路和方法<br>2. <strong>算法代码：</strong>结构清晰、注释完整的实现代码<br>3. <strong>数据与结果：</strong>数据预处理说明 + 计算结果 + 可视化图表<br>4. <strong>模型评估：</strong>对结果的合理性分析和模型优缺点总结' },
]

const account = [
  { title: '如何修改个人资料？', content: '登录后进入<strong>"个人中心"</strong>，点击左侧<strong>"账号设置"</strong>即可修改用户名、学校等信息。邮箱和注册时间暂不支持修改。' },
  { title: '忘记密码怎么办？', content: '在登录页面点击<strong>"忘记密码"</strong>链接，输入注册邮箱后系统会发送重置密码邮件。请检查收件箱和垃圾箱。如未收到，请联系客服。' },
  { title: '如何注销账号？', content: '进入<strong>"个人中心 > 账号设置"</strong>，在页面底部有<strong>"注销账号"</strong>按钮。注意：注销后所有数据将被永久删除且无法恢复，请谨慎操作。' },
]
</script>

<style scoped>
@import './style.css';
</style>
