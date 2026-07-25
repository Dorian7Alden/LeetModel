export default [
  {
    path: '/admin',
    component: () => import("@/views/admin/AdminLayout.vue"),
    redirect: '/admin/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        meta: { title: '首页概览', icon: 'House' },
        children: [
          {
            path: 'dashboard',
            name: 'AdminDashboard',
            component: () => import("@/views/admin/pages/DashboardPage.vue"),
            meta: { title: '首页概览' }
          }
        ]
      },
      {
        path: 'problem',
        meta: { title: '题目管理', icon: 'Document' },
        children: [
          {
            path: 'list',
            name: 'AdminProblemList',
            component: () => import("@/views/admin/pages/ProblemListPage.vue"),
            meta: { title: '题目管理' }
          }
        ]
      },
      {
        path: 'submission',
        meta: { title: '作品管理', icon: 'UploadFilled' },
        children: [
          {
            path: 'list',
            name: 'AdminSubmissionList',
            component: () => import("@/views/admin/pages/SubmissionListPage.vue"),
            meta: { title: '作品管理' }
          }
        ]
      },
      {
        path: 'role',
        meta: { title: '角色管理', icon: 'UserFilled' },
        children: [
          {
            path: 'list',
            name: 'AdminRoleList',
            component: () => import("@/views/admin/pages/RoleListPage.vue"),
            meta: { title: '角色管理' }
          }
        ]
      },
      {
        path: 'permission',
        meta: { title: '权限管理', icon: 'Key' },
        children: [
          {
            path: 'list',
            name: 'AdminPermissionList',
            component: () => import("@/views/admin/pages/PermissionListPage.vue"),
            meta: { title: '权限管理' }
          }
        ]
      },
      {
        path: 'auth',
        meta: { title: '授权管理', icon: 'Lock' },
        children: [
          {
            path: 'index',
            name: 'AdminAuth',
            component: () => import("@/views/admin/pages/AuthIndexPage.vue"),
            meta: { title: '授权管理' }
          }
        ]
      }
    ]
  }
]
