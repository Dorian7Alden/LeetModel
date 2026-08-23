import { defineStore } from "pinia";
import { logout as logoutApi } from "@/api/user";

export const useUserStore = defineStore("user", {
  state: () => ({
    token: localStorage.getItem("token") || "",
    userId: localStorage.getItem("userId") || "",
    username: localStorage.getItem("username") || "",
    email: localStorage.getItem("email") || "",
    nickname: localStorage.getItem("nickname") || "",
    avatarUrl: localStorage.getItem("avatarUrl") || "",
    roles: JSON.parse(localStorage.getItem("roles") || "[]"),
    permissions: JSON.parse(localStorage.getItem("permissions") || "[]"),
  }),

  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => state.roles.includes("admin"),
    primaryRole: (state) => {
      if (state.roles.includes("admin")) return "admin";
      if (state.roles.includes("vip")) return "vip";
      return "user";
    },
    roleLabel() {
      const labels = { admin: "管理员", vip: "VIP 用户", user: "普通用户" };
      return labels[this.primaryRole];
    },
  },

  actions: {
    login({ token, userId, username, roles = [], permissions = [] }) {
      this.token = token;
      this.userId = String(userId);
      this.username = username;
      this.email = "";
      this.roles = roles;
      this.permissions = permissions;
      this.avatarUrl = "";

      localStorage.setItem("token", token);
      localStorage.setItem("userId", String(userId));
      localStorage.setItem("username", username);
      localStorage.setItem("roles", JSON.stringify(roles));
      localStorage.setItem("permissions", JSON.stringify(permissions));
      localStorage.removeItem("role");
      localStorage.removeItem("email");
      localStorage.removeItem("avatarUrl");
    },

    updateProfile({ username, nickname, email, avatarUrl }) {
      if (username) {
        this.username = username;
        localStorage.setItem("username", username);
      }
      if (nickname !== undefined) {
        this.nickname = nickname || "";
        localStorage.setItem("nickname", nickname || "");
      }
      if (email !== undefined) {
        this.email = email || "";
        localStorage.setItem("email", email || "");
      }
      if (avatarUrl !== undefined) {
        this.avatarUrl = avatarUrl;
        localStorage.setItem("avatarUrl", avatarUrl || "");
      }
    },

    updateAuthorization({ roles = [], permissions = [] }) {
      this.roles = roles;
      this.permissions = permissions;
      localStorage.setItem("roles", JSON.stringify(roles));
      localStorage.setItem("permissions", JSON.stringify(permissions));
    },

    async logout() {
      try {
        await logoutApi();
      } catch (err) {
        console.log("退出接口异常", err);
      } finally {
        this.clearSession();
      }
    },

    clearSession() {
      this.token = "";
      this.userId = "";
      this.username = "";
      this.email = "";
      this.nickname = "";
      this.avatarUrl = "";
      this.roles = [];
      this.permissions = [];

      localStorage.removeItem("token");
      localStorage.removeItem("role");
      localStorage.removeItem("roles");
      localStorage.removeItem("permissions");
      localStorage.removeItem("userId");
      localStorage.removeItem("username");
      localStorage.removeItem("email");
      localStorage.removeItem("nickname");
      localStorage.removeItem("avatarUrl");
    },
  },
});
