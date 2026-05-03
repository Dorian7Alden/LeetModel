export default [
  {
    path: "community",
    name: "Community",
    component: () => import("@/views/community/CommunityPage.vue"),
  },
  {
    path: "post/create",
    name: "CreatePost",
    component: () => import("@/views/community/pages/CreatePostPage.vue"),
  },
  {
    path: "post/:id",
    name: "PostDetail",
    component: () => import("@/views/community/pages/PostDetailPage.vue"),
  },
]
