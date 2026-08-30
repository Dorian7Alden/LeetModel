export default [
  {
    path: "/admin",
    component: () => import("@/views/admin/AdminLayout.vue"),
    redirect: "/admin/dashboard",
    meta: { requiresAuth: true },
    children: [
      {
        path: "",
        meta: { title: "首页概览", icon: "House" },
        children: [
          {
            path: "dashboard",
            name: "AdminDashboard",
            component: () => import("@/views/admin/pages/DashboardPage.vue"),
            meta: { title: "首页概览" },
          },
        ],
      },
      {
        path: "users",
        meta: { title: "用户管理", icon: "User" },
        children: [
          {
            path: "list",
            name: "AdminUserList",
            component: () => import("@/views/admin/pages/UserListPage.vue"),
            meta: { title: "用户管理" },
          },
        ],
      },
      {
        path: "contest",
        meta: { title: "赛事数据", icon: "Collection" },
        children: [
          {
            path: "list",
            name: "AdminContestList",
            component: () => import("@/views/admin/pages/ContestListPage.vue"),
            meta: { title: "赛事数据" },
          },
        ],
      },
      {
        path: "problem",
        meta: { title: "题目管理", icon: "Document" },
        children: [
          {
            path: "list",
            name: "AdminProblemList",
            component: () => import("@/views/admin/pages/ProblemListPage.vue"),
            meta: { title: "题目管理" },
          },
        ],
      },
      {
        path: "tags",
        meta: { title: "标签管理", icon: "CollectionTag" },
        children: [
          {
            path: "list",
            name: "AdminTagList",
            component: () => import("@/views/admin/pages/TagListPage.vue"),
            meta: { title: "标签管理" },
          },
        ],
      },
      {
        path: "role",
        meta: { title: "角色管理", icon: "UserFilled" },
        children: [
          {
            path: "list",
            name: "AdminRoleList",
            component: () => import("@/views/admin/pages/RoleListPage.vue"),
            meta: { title: "角色管理" },
          },
        ],
      },
      {
        path: "permission",
        meta: { title: "权限管理", icon: "Key" },
        children: [
          {
            path: "list",
            name: "AdminPermissionList",
            component: () => import("@/views/admin/pages/PermissionListPage.vue"),
            meta: { title: "权限管理" },
          },
        ],
      },
      {
        path: "auth",
        meta: { title: "授权管理", icon: "Lock" },
        children: [
          {
            path: "index",
            name: "AdminAuth",
            component: () => import("@/views/admin/pages/AuthIndexPage.vue"),
            meta: { title: "授权管理" },
          },
        ],
      },
      {
        path: "submissions",
        meta: { title: "提交管理", icon: "Upload" },
        children: [
          {
            path: "list",
            name: "AdminSubmissionList",
            component: () => import("@/views/admin/pages/SubmissionListPage.vue"),
            meta: { title: "提交管理" },
          },
        ],
      },
      {
        path: "teams",
        meta: { title: "队伍管理", icon: "UserFilled" },
        children: [
          {
            path: "list",
            name: "AdminTeamList",
            component: () => import("@/views/admin/pages/TeamListPage.vue"),
            meta: { title: "队伍管理" },
          },
        ],
      },
      {
        path: "reviews",
        meta: { title: "评审管理", icon: "DataAnalysis" },
        children: [
          {
            path: "list",
            name: "AdminReviewList",
            component: () => import("@/views/admin/pages/ReviewListPage.vue"),
            meta: { title: "评审管理" },
          },
        ],
      },
      {
        path: "suggestions",
        meta: { title: "建议管理", icon: "ChatDotRound" },
        children: [
          {
            path: "list",
            name: "AdminSuggestionList",
            component: () => import("@/views/admin/pages/SuggestionListPage.vue"),
            meta: { title: "建议管理" },
          },
        ],
      },
      {
        path: "rankings",
        meta: { title: "排行榜管理", icon: "Trophy" },
        children: [
          {
            path: "list",
            name: "AdminRankingList",
            component: () => import("@/views/admin/pages/RankingAdminPage.vue"),
            meta: { title: "排行榜管理" },
          },
        ],
      },
      {
        path: "ai-calls",
        meta: { title: "AI 调用", icon: "Cpu" },
        children: [
          {
            path: "list",
            name: "AdminAiCallList",
            component: () => import("@/views/admin/pages/AiCallListPage.vue"),
            meta: { title: "AI 调用" },
          },
        ],
      },
      {
        path: "evaluations",
        meta: { title: "质量评价", icon: "Histogram" },
        children: [
          {
            path: "list",
            name: "AdminEvaluation",
            component: () => import("@/views/admin/pages/EvaluationPage.vue"),
            meta: { title: "质量评价" },
          },
        ],
      },
      {
        path: "production-workflows",
        meta: { title: "生产版本", icon: "SetUp" },
        children: [
          {
            path: "list",
            name: "AdminProductionWorkflow",
            component: () => import("@/views/admin/pages/ProductionWorkflowPage.vue"),
            meta: { title: "生产版本" },
          },
        ],
      },
    ],
  },
];
