import { createRouter, createWebHistory } from "vue-router"
import MainLayout from "../layouts/MainLayout.vue"

const routes = [
{
path:"/",
component:MainLayout,
children:[

/* ================= 首页模块 ================= */

{
path:"",
name:"Home",
component:()=>import("../views/home/Home.vue")
},


/* ================= 训练系统 ================= */

{
path:"training",
name:"Training",
component:()=>import("../views/training/Training.vue"),

children: [

    {
      path: "modeling",
      name: "ModelingTraining",
      component: () =>
        import("../views/training/ModelingTraining.vue")
    },

    {
      path: "paper",
      name: "PaperTraining",
      component: () =>
        import("../views/training/PaperTraining.vue")
    },

    {
      path: "coding",
      name: "CodingTraining",
      component: () =>
        import("../views/training/CodingTraining.vue")
    }

  ]

},


/* ================= 题库系统 ================= */

{
path:"problem",
name:"ProblemBank",
component:()=>import("../views/problem/ProblemBank.vue")
},

{
path:"problem/list",
name:"ProblemList",
component:()=>import("../views/problem/ProblemList.vue")
},

{
path:"problem/:id",
name:"ProblemDetail",
component:()=>import("../views/problem/ProblemDetail.vue")
},

{
path:"problem/tags",
name:"TagFilter",
component:()=>import("../views/problem/TagFilter.vue")
},

{
path:"problem/model-category",
name:"ModelCategory",
component:()=>import("../views/problem/ModelCategory.vue")
},

{
path:"problem/industry-category",
name:"IndustryCategory",
component:()=>import("../views/problem/IndustryCategory.vue")
},

{
path:"problem/discussion",
name:"ProblemDiscussion",
component:()=>import("../views/problem/ProblemDiscussion.vue")
},

{
path:"problem/submissions",
name:"SubmissionList",
component:()=>import("../views/problem/SubmissionList.vue")
},
/* ================= 赛事系统 ================= */

{
path:"contest",
name:"ContestList",
component:()=>import("../views/contest/ContestList.vue")
},

{
path:"contest/:id",
name:"ContestDetail",
component:()=>import("../views/contest/ContestDetail.vue")
},

{
path:"contest/workspace/:id",
name:"ContestWorkspace",
component:()=>import("../views/contest/ContestWorkspace.vue")
},

{
path:"contest/problem/:id",
name:"ContestProblem",
component:()=>import("../views/contest/ContestProblem.vue")
},

{
path:"contest/dataset",
name:"DatasetDownload",
component:()=>import("../views/contest/DatasetDownload.vue")
},

{
path:"contest/submit",
name:"FileSubmit",
component:()=>import("../views/contest/FileSubmit.vue")
},

{
path:"contest/rank",
name:"ContestRank",
component:()=>import("../views/contest/ContestRank.vue")
},

{
path:"contest/result",
name:"ContestResult",
component:()=>import("../views/contest/ContestResult.vue")
},

/* ================= 社区系统 ================= */

{
path:"community",
name:"Community",
component:()=>import("../views/community/Community.vue")
},

{
  path:"post/create",
  name:"CreatePost",
  component:()=>import("../views/community/CreatePost.vue")
},
{
  path:"post/:id",
  name:"PostDetail",
  component:()=>import("../views/community/PostDetail.vue")
},

/* ================= 组队系统 ================= */

{
path:"team",
name:"TeamList",
component:()=>import("../views/team/TeamList.vue")
},

{
path:"team/:id",
name:"TeamDetail",
component:()=>import("../views/team/TeamDetail.vue")
},

{
path:"team/create",
name:"TeamCreate",
component:()=>import("../views/team/TeamCreate.vue")
},

{
path:"team/match",
name:"TeamMatch",
component:()=>import("../views/team/TeamMatch.vue")
},

/* ================= 成长系统 ================= */

{
path:"profile",
name:"Profile",
component:()=>import("../views/profile/Profile.vue")
},

{
path:"profile/analysis",
name:"SkillAnalysis",
component:()=>import("../views/profile/SkillAnalysis.vue")
},

{
path:"profile/training-stats",
name:"TrainingStats",
component:()=>import("../views/profile/TrainingStats.vue")
},

{
path:"profile/achievement",
name:"Achievement",
component:()=>import("../views/profile/Achievement.vue")
},

{
path:"profile/history",
name:"History",
component:()=>import("../views/profile/History.vue")
},
{
path:"profile/settings",
name:"settings",
component:()=>import("../views/profile/Settings.vue")
},
{
path:"submission/:id",
name:"SubmissionDetail",
component:()=>import("../views/profile/SubmissionDetail.vue")
},

/* ================= 用户系统 ================= */

{
path:"login",
name:"Login",
component:()=>import("../views/user/Login.vue")
},

{
path:"register",
name:"Register",
component:()=>import("../views/user/Register.vue")
},

{
path:"forgot-password",
name:"ForgotPassword",
component:()=>import("../views/user/ForgotPassword.vue")
},

{
path:"settings",
name:"Settings",
component:()=>import("../views/user/Settings.vue")
}

]
},

/* ================= 404 ================= */

{
path:"/:pathMatch(.*)*",
name:"NotFound",
component:()=>import("../views/NotFound.vue")
}

]

const router = createRouter({
history:createWebHistory(),
routes
})

export default router