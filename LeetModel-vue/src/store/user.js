import { defineStore } from "pinia";
import { logout as logoutApi } from "@/api/user";

export const useUserStore = defineStore("user", {
  state: () => ({
    token: localStorage.getItem("token") || "",
    username: "",
  }),

  getters: {
    isLogin: (state) => !!state.token,
  },

  actions: {
    login(token, username) {
      this.token = token;
      this.username = username;

      localStorage.setItem("token", token);
    },

    // 🔥 改这里
    async logout() {
      try {
        await logoutApi(); // ✅ 调后端
      } catch (err) {
        console.log("退出接口异常", err);
      } finally {
        // ✅ 无论如何都清理前端状态
        this.token = "";
        this.username = "";

        localStorage.removeItem("token");
      }
    },
  },
});
