<template>
  <div class="training-nav">
    <router-link to="/training/modeling" class="nav-item">
      建模训练
    </router-link>

    <router-link to="/training/paper" class="nav-item"> 论文训练 </router-link>

    <router-link to="/training/coding" class="nav-item"> 编程训练 </router-link>
  </div>
</template>

<style scoped>
.training-nav {
  display: flex;
  gap: 24px;

  padding: 14px 20px;
  margin-bottom: 25px;

  background: #ffffff;
  border-radius: 12px;

  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.06);
}

.nav-item {
  padding: 8px 18px;

  border-radius: 8px;

  text-decoration: none;

  color: #444;
  font-size: 16px;

  transition: all 0.2s;
}

/* hover效果 */
.nav-item:hover {
  background: #f2f6fc;

  color: #409eff;
}

/* 当前页面 */
.nav-item.router-link-active {
  background: #409eff;

  color: #fff;

  font-weight: 600;
}
</style>
