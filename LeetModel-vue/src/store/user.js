import { defineStore } from "pinia";
import { logout as logoutApi } from "@/api/user";

export const useUserStore = defineStore("user", {
  state: () => ({
    token: localStorage.getItem("token") || "",
    username: localStorage.getItem("username") || "",
    email: localStorage.getItem("email") || "",
    avatarUrl: localStorage.getItem("avatarUrl") || "",
    role: localStorage.getItem("role") || "",
  }),

  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => state.role === "ADMIN" || state.role === "SUPER_ADMIN",
  },

  actions: {
    login(token, username, email, role, avatarUrl) {
      this.token = token;
      this.username = username;
      this.email = email;
      this.role = role;
      this.avatarUrl = avatarUrl || "";

      localStorage.setItem("token", token);
      localStorage.setItem("role", role);
      localStorage.setItem("username", username);
      localStorage.setItem("email", email);
      if (avatarUrl) localStorage.setItem("avatarUrl", avatarUrl);
    },

    updateProfile({ username, avatarUrl }) {
      if (username) {
        this.username = username;
        localStorage.setItem("username", username);
      }
      if (avatarUrl !== undefined) {
        this.avatarUrl = avatarUrl;
        localStorage.setItem("avatarUrl", avatarUrl || "");
      }
    },

    async logout() {
      try {
        await logoutApi();
      } catch (err) {
        console.log("退出接口异常", err);
      } finally {
        this.token = "";
        this.username = "";
        this.email = "";
        this.role = "";

        localStorage.removeItem("token");
        localStorage.removeItem("role");
        localStorage.removeItem("userId");
        localStorage.removeItem("username");
        localStorage.removeItem("email");
        localStorage.removeItem("avatarUrl");
      }
    },
  },
});
