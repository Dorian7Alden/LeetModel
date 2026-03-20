<template>
  <div class="leetcode-left-menu" :class="{ collapsed: isCollapsed }">
    <!-- 头部 -->
    <div class="menu-header">
      <span v-if="!isCollapsed">题库导航</span>
      <button class="collapse-btn" @click="toggleCollapse">
        {{ isCollapsed ? "→" : "←" }}
      </button>
    </div>

    <!-- 菜单 -->
    <div class="menu-list">
      <template v-for="item in menuList" :key="item.key">
        <!-- 一级菜单 -->
        <div
          class="menu-item"
          :class="{ active: activeKey === item.key }"
          @click="handleMenuClick(item)"
        >
          <span class="menu-icon">{{ item.icon }}</span>

          <span v-if="!isCollapsed" class="menu-label">
            {{ item.label }}
          </span>

          <span
            v-if="item.children && !isCollapsed"
            class="arrow"
            :class="{ open: item.open }"
          >
            ▾
          </span>
        </div>

        <!-- 子菜单 -->
        <div
          v-if="item.children && item.open && !isCollapsed"
          class="submenu-list"
        >
          <div
            v-for="sub in item.children"
            :key="sub.key"
            class="submenu-item"
            :class="{ active: activeKey === sub.key }"
            @click="handleMenuClick(sub)"
          >
            {{ sub.label }}
          </div>
        </div>
      </template>
    </div>

    <!-- 登录按钮 -->
    <router-link v-if="!userStore.isLogin" to="/register" class="login-btn">
      登录 / 注册
    </router-link>
  </div>
</template>

<script setup>
import { ref, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useUserStore } from "@/store/user";

/* ---------------- 状态 ---------------- */
const userStore = useUserStore();
const router = useRouter();
const route = useRoute();

const isCollapsed = ref(false);
const activeKey = ref("all");

/* ---------------- 菜单数据 ---------------- */
const menuList = ref([
  {
    key: "all",
    label: "全部题目",
    icon: "📋",
    path: "/problem/problemListPage",
  },
  { key: "hot", label: "热题 HOT 100", icon: "🔥", path: "/problem/hot" },
  { key: "top", label: "精选 TOP 200", icon: "⭐", path: "/problem/top" },
  { key: "leetbook", label: "LeetBook", icon: "📚", path: "/problem/leetbook" },
  {
    key: "training",
    label: "专项训练",
    icon: "🎯",
    open: false,
    children: [
      { key: "model", label: "建模手", path: "/problem/modeling" },
      { key: "code", label: "编程手", path: "/problem/coding" },
      { key: "paper", label: "论文手", path: "/problem/paper" },
    ],
  },
]);

/* ---------------- 方法 ---------------- */

// 折叠
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value;
};

// 点击菜单
const handleMenuClick = (item) => {
  // 有子菜单 → 展开
  if (item.children) {
    item.open = !item.open;
    return;
  }

  activeKey.value = item.key;

  // 路由跳转
  if (item.path) {
    router.push(item.path);
  }
};

/* ---------------- 路由联动 ---------------- */

// 根据路由自动高亮
watch(
  () => route.path,
  (path) => {
    menuList.value.forEach((item) => {
      if (item.path === path) {
        activeKey.value = item.key;
      }

      if (item.children) {
        item.children.forEach((sub) => {
          if (sub.path === path) {
            activeKey.value = sub.key;
            item.open = true;
          }
        });
      }
    });
  },
  { immediate: true },
);
</script>

<style scoped>
.leetcode-left-menu {
  width: 240px;
  height: 100vh;
  background: #fff;
  border-right: 1px solid #eee;
  padding: 16px 0;
  transition: 0.3s;
  position: sticky;
  top: 0;
}

.leetcode-left-menu.collapsed {
  width: 60px;
}

/* 头部 */
.menu-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px 16px;
  font-weight: 600;
}

.collapse-btn {
  border: none;
  background: none;
  cursor: pointer;
}

/* 菜单 */
.menu-list {
  padding: 0 8px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: 0.2s;
}

.menu-item:hover {
  background: #f5f5f5;
}

.menu-item.active {
  background: #e6f0ff;
  color: #409eff;
}

/* 子菜单 */
.submenu-list {
  padding-left: 30px;
}

.submenu-item {
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
}

.submenu-item:hover {
  background: #f5f5f5;
}

.submenu-item.active {
  background: #e6f0ff;
  color: #409eff;
}

/* 登录按钮 */
.login-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  margin: 16px;
  height: 44px;
  border-radius: 10px;
  background: #222;
  color: #fff;
  text-decoration: none;
}
</style>
