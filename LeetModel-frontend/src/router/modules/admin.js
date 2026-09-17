const directRedirect = (path, target) => ({
  path,
  redirect: target,
  meta: { hidden: true },
});

export default [
  {
    path: "/admin",
    component: () => import("@/views/admin/AdminLayout.vue"),
    redirect: "/admin/dashboard",
    meta: { requiresAuth: true },
    children: [
      // 1. 运行概览 (一级直达)
      {
        path: "dashboard",
        name: "AdminDashboard",
        component: () => import("@/views/admin/pages/DashboardPage.vue"),
        meta: { title: "运行概览", icon: "DataBoard" },
      },

      // 2. 业务管理 (二级展开)
      {
        path: "problems",
        name: "AdminProblems",
        component: () => import("@/views/admin/pages/ProblemListPage.vue"),
        meta: { title: "题目管理", group: "biz", icon: "Document" },
      },
      {
        path: "contests",
        name: "AdminContests",
        component: () => import("@/views/admin/pages/ContestListPage.vue"),
        meta: { title: "赛事管理", group: "biz", icon: "Trophy" },
      },
      {
        path: "tags",
        name: "AdminTags",
        component: () => import("@/views/admin/pages/TagListPage.vue"),
        meta: { title: "标签管理", group: "biz", icon: "CollectionTag" },
      },
      {
        path: "teams",
        name: "AdminTeams",
        component: () => import("@/views/admin/pages/TeamListPage.vue"),
        meta: { title: "队伍管理", group: "biz", icon: "UserFilled" },
      },
      {
        path: "submissions",
        name: "AdminSubmissions",
        component: () => import("@/views/admin/pages/SubmissionListPage.vue"),
        meta: { title: "提交管理", group: "biz", icon: "Files" },
      },
      {
        path: "rankings",
        name: "AdminRankings",
        component: () => import("@/views/admin/pages/RankingAdminPage.vue"),
        meta: { title: "榜单管理", group: "biz", icon: "Histogram" },
      },

      // 3. AI管理 (二级展开)
      {
        path: "ai-reviews",
        name: "AdminAiReviews",
        component: () => import("@/views/admin/pages/ReviewListPage.vue"),
        meta: { title: "AI评审", group: "ai", icon: "Checked" },
      },
      {
        path: "ai-suggestions",
        name: "AdminAiSuggestions",
        component: () => import("@/views/admin/pages/SuggestionListPage.vue"),
        meta: { title: "AI建议", group: "ai", icon: "ChatDotRound" },
      },
      {
        path: "ai-assistant",
        name: "AdminAiAssistant",
        component: () => import("@/views/admin/pages/ProductionWorkflowPage.vue"),
        meta: { title: "AI客服", group: "ai", icon: "Service" },
      },
      {
        path: "ai-evaluations",
        name: "AdminAiEvaluations",
        component: () => import("@/views/admin/pages/EvaluationPage.vue"),
        meta: { title: "AI评测", group: "ai", icon: "Aim" },
      },
      {
        path: "ai-calls",
        name: "AdminAiCalls",
        component: () => import("@/views/admin/pages/AiCallListPage.vue"),
        meta: { title: "AI调用", group: "ai", icon: "Connection" },
      },

      // 4. 资产管理 (二级展开)
      {
        path: "knowledge",
        name: "AdminKnowledge",
        component: () => import("@/views/admin/pages/KnowledgeManagerPage.vue"),
        meta: { title: "知识库管理", group: "asset", icon: "Notebook" },
      },
      {
        path: "storage",
        name: "AdminStorage",
        component: () => import("@/views/admin/pages/StorageConsolePage.vue"),
        meta: { title: "文件管理", group: "asset", icon: "FolderOpened" },
      },

      // 5. 运维管理 (二级展开)
      {
        path: "audit",
        name: "AdminAudit",
        component: () => import("@/views/admin/pages/AuditPage.vue"),
        meta: { title: "操作审计", group: "ops", icon: "DocumentChecked" },
      },
      {
        path: "messaging",
        name: "AdminMessaging",
        component: () => import("@/views/admin/pages/MessagingOperationsPage.vue"),
        meta: { title: "消息队列", group: "ops", icon: "MessageBox" },
      },

      // 6. 访问控制 (一级直达)
      {
        path: "access",
        name: "AdminAccess",
        component: () => import("@/views/admin/pages/AccessControlPage.vue"),
        meta: { title: "访问控制", icon: "Lock" },
      },

      // 兼容旧中心路径
      {
        path: "content",
        redirect: (to) => {
          const map = {
            problems: "/admin/problems",
            contests: "/admin/contests",
            tags: "/admin/tags",
            files: "/admin/storage",
            knowledge: "/admin/knowledge",
          };
          return map[to.query.view] || "/admin/problems";
        },
        meta: { hidden: true },
      },
      {
        path: "operations",
        redirect: (to) => {
          const map = {
            submissions: "/admin/submissions",
            teams: "/admin/teams",
            reviews: "/admin/ai-reviews",
            suggestions: "/admin/ai-suggestions",
            rankings: "/admin/rankings",
          };
          return map[to.query.view] || "/admin/teams";
        },
        meta: { hidden: true },
      },
      {
        path: "ai",
        redirect: (to) => {
          const map = {
            calls: "/admin/ai-calls",
            evaluations: "/admin/ai-evaluations",
            production: "/admin/ai-assistant",
            versions: "/admin/ai-assistant",
            messaging: "/admin/messaging",
          };
          return map[to.query.view] || "/admin/ai-reviews";
        },
        meta: { hidden: true },
      },

      // 兼容传统深链路径
      directRedirect("users/list", "/admin/access?view=users"),
      directRedirect("role/list", "/admin/access?view=roles"),
      directRedirect("permission/list", "/admin/access?view=permissions"),
      directRedirect("auth/index", "/admin/access?view=authorization"),
      directRedirect("problem/list", "/admin/problems"),
      directRedirect("tags/list", "/admin/tags"),
      directRedirect("contest/list", "/admin/contests"),
      directRedirect("submissions/list", "/admin/submissions"),
      directRedirect("teams/list", "/admin/teams"),
      directRedirect("reviews/list", "/admin/ai-reviews"),
      directRedirect("suggestions/list", "/admin/ai-suggestions"),
      directRedirect("rankings/list", "/admin/rankings"),
      directRedirect("ai-calls/list", "/admin/ai-calls"),
      directRedirect("evaluations/list", "/admin/ai-evaluations"),
      directRedirect("production-workflows/list", "/admin/ai-assistant"),
    ],
  },
];
