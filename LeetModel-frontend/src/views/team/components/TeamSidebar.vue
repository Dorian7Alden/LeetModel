<template>
  <aside class="team-sidebar" :class="{ collapsed: isCollapsed }">
    <div class="team-sidebar-content">
      <div v-for="item in teamSections" :key="item.key" class="sidebar-status-group">
        <button
          type="button"
          class="sidebar-group-trigger"
          :class="{ active: activeSection === item.key }"
          :aria-expanded="expandedSection === item.key"
          @click="selectGroup(item.key)"
        >
          <component :is="item.icon" :size="16" :stroke-width="1.9" :class="item.iconClass" />
          <span>{{ item.label }}</span>
          <span class="group-meta">
            <span class="sidebar-count" :class="{ loading }">{{ countText(item.key) }}</span>
            <ChevronRight class="group-chevron" :class="{ expanded: expandedSection === item.key }" :size="13" />
          </span>
        </button>

        <transition name="sidebar-group">
          <div v-show="expandedSection === item.key && (teams[item.key] || []).length" class="sidebar-submenu">
            <button
              v-for="team in teams[item.key] || []"
              :key="team.id"
              type="button"
              class="sidebar-team-item"
              :class="{ active: activeSection === item.key && String(activeTeamId) === String(team.id) }"
              :title="team.name"
              @click="emit('select-team', { status: item.key, team })"
            >
              <span class="team-dot" />
              <span>{{ team.name }}</span>
            </button>
          </div>
        </transition>
      </div>

      <div class="sidebar-divider" />
      <button
        type="button"
        class="sidebar-item"
        :class="{ active: activeSection === 'SQUARE' }"
        @click="emit('select', 'SQUARE')"
      >
        <Compass :size="16" :stroke-width="1.9" />
        <span>队伍广场</span>
        <span class="sidebar-count" :class="{ loading }">{{ countText('SQUARE') }}</span>
      </button>

      <button type="button" class="create-team-button" @click="emit('create')">
        <Plus :size="16" :stroke-width="2" />
        <span>创建队伍</span>
      </button>
    </div>

    <div class="sidebar-boundary">
      <button
        type="button"
        class="sidebar-collapse-button"
        :aria-label="isCollapsed ? '展开队伍导航' : '折叠队伍导航'"
        :title="isCollapsed ? '展开队伍导航' : '折叠队伍导航'"
        @click="toggleCollapse"
      >
        <ChevronRight v-if="isCollapsed" :size="14" />
        <ChevronLeft v-else :size="14" />
      </button>
    </div>
  </aside>
</template>

<script setup>
import { ref, watch } from 'vue'
import {
  CircleCheck,
  Compass,
  Plus,
  Timer,
  UsersRound,
  ChevronLeft,
  ChevronRight,
} from '@lucide/vue'

const props = defineProps({
  activeSection: { type: String, default: 'PREPARING' },
  activeTeamId: { type: String, default: '' },
  counts: { type: Object, required: true },
  teams: { type: Object, required: true },
  loading: { type: Boolean, default: false },
})
const emit = defineEmits(['select', 'select-team', 'create'])
const isCollapsed = ref(false)
const expandedSection = ref(props.activeSection === 'SQUARE' ? null : props.activeSection)

const teamSections = [
  { key: 'PREPARING', label: '正在组建', icon: UsersRound, iconClass: 'preparing-icon' },
  { key: 'IN_PROGRESS', label: '练习中', icon: Timer, iconClass: 'active-icon' },
  { key: 'ENDED', label: '已结束', icon: CircleCheck, iconClass: 'ended-icon' },
]

function countText(key) {
  const value = props.counts[key]
  return value === null || value === undefined ? '—' : String(value)
}

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}

function selectGroup(section) {
  expandedSection.value = expandedSection.value === section ? null : section
  emit('select', section)
}

watch(
  () => props.activeSection,
  section => {
    if (section !== 'SQUARE') expandedSection.value = section
  },
)
</script>

<style scoped>
.team-sidebar {
  position: sticky;
  top: 56px;
  z-index: 20;
  display: flex;
  width: 208px;
  height: calc(100vh - 56px);
  flex-shrink: 0;
  background: #fff;
  transition: width 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.team-sidebar-content {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
  overflow: hidden;
  padding: 16px 14px 20px;
  opacity: 1;
  transition: opacity 0.15s ease, transform 0.22s ease;
}

.sidebar-group-trigger,
.sidebar-item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 9px 11px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--lm-text-secondary);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: color 0.16s ease, background 0.16s ease;
}

.sidebar-status-group {
  display: flex;
  flex-direction: column;
}

.group-meta {
  display: flex;
  align-items: center;
  gap: 5px;
}

.sidebar-group-trigger:hover,
.sidebar-group-trigger.active,
.sidebar-item:hover,
.sidebar-item.active {
  background: #f4f4f5;
  color: var(--lm-text-primary);
}

.sidebar-group-trigger.active,
.sidebar-item.active {
  font-weight: 700;
}

.sidebar-submenu {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 2px 0 3px 9px;
  padding-left: 8px;
  border-left: 1px solid var(--lm-border-light);
}

.sidebar-team-item {
  display: grid;
  grid-template-columns: 5px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--lm-text-muted);
  font: inherit;
  font-size: 11px;
  text-align: left;
  cursor: pointer;
}

.sidebar-team-item > span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-team-item:hover,
.sidebar-team-item.active {
  background: #f4f4f5;
  color: var(--lm-text-primary);
}

.sidebar-team-item.active {
  font-weight: 650;
}

.team-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #d4d4d8;
}

.sidebar-team-item.active .team-dot {
  background: #18181b;
}

.group-chevron {
  color: var(--lm-text-muted);
  transition: transform 0.18s ease;
}

.group-chevron.expanded {
  transform: rotate(90deg);
}

.sidebar-group-enter-active,
.sidebar-group-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.sidebar-group-enter-from,
.sidebar-group-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.preparing-icon { color: #2563eb; }
.active-icon { color: #d97706; }
.ended-icon { color: #16a34a; }

.sidebar-count {
  min-width: 22px;
  padding: 2px 6px;
  border: 1px solid #e4e4e7;
  border-radius: 999px;
  background: #fafafa;
  color: #52525b;
  font-family: var(--lm-code-font-family);
  font-size: 10px;
  line-height: 1.2;
  text-align: center;
}

.sidebar-count.loading {
  color: #a1a1aa;
}

.sidebar-divider {
  height: 1px;
  margin: 8px 8px 4px;
  background: var(--lm-border-light);
}

.create-team-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  margin-top: auto;
  padding: 10px 12px;
  border: 0;
  border-radius: 7px;
  background: #18181b;
  color: #fff;
  font: inherit;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  transition: background 0.16s ease, transform 0.16s ease;
}

.create-team-button:hover {
  background: #27272a;
  transform: translateY(-1px);
}

.sidebar-boundary {
  position: absolute;
  top: 0;
  right: -7px;
  bottom: 0;
  width: 14px;
  border-left: 1px solid var(--lm-border);
}

.sidebar-collapse-button {
  position: absolute;
  top: 112px;
  left: -12px;
  display: flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--lm-border);
  border-radius: 50%;
  background: #fff;
  color: var(--lm-text-secondary);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.1);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.16s ease, color 0.16s ease, background 0.16s ease;
}

.sidebar-boundary:hover .sidebar-collapse-button,
.sidebar-collapse-button:focus-visible {
  opacity: 1;
}

.sidebar-collapse-button:hover {
  background: #18181b;
  color: #fff;
}

.team-sidebar.collapsed {
  width: 0;
}

.team-sidebar.collapsed .team-sidebar-content {
  pointer-events: none;
  opacity: 0;
  transform: translateX(-12px);
}

.team-sidebar.collapsed .sidebar-boundary {
  border-left-color: transparent;
}

.team-sidebar.collapsed .sidebar-collapse-button {
  left: 8px;
  opacity: 1;
}

@media (max-width: 860px) {
  .team-sidebar,
  .team-sidebar.collapsed {
    position: sticky;
    top: 56px;
    width: 100%;
    height: auto;
    border-bottom: 1px solid var(--lm-border);
  }

  .team-sidebar-content,
  .team-sidebar.collapsed .team-sidebar-content {
    display: flex;
    width: 100%;
    flex-direction: row;
    gap: 6px;
    overflow-x: auto;
    padding: 10px 14px;
    pointer-events: auto;
    opacity: 1;
    transform: none;
  }

  .sidebar-group-trigger,
  .sidebar-divider,
  .create-team-button,
  .sidebar-boundary {
    display: none;
  }

  .sidebar-item {
    display: flex;
    width: auto;
    flex: 0 0 auto;
    padding: 8px 11px;
  }

  .sidebar-status-group {
    flex: 0 0 auto;
    flex-direction: row;
  }

  .sidebar-group-trigger {
    display: grid;
  }

  .sidebar-submenu {
    display: flex;
    flex: 0 0 auto;
    flex-direction: row;
    gap: 6px;
    margin: 0;
    padding: 0;
    border-left: 0;
  }

  .sidebar-team-item {
    width: auto;
    max-width: 180px;
    flex: 0 0 auto;
    padding: 8px 10px;
    border: 1px solid var(--lm-border-light);
  }

  .sidebar-empty-team {
    display: none;
  }

  .sidebar-count {
    min-width: 19px;
  }
}
</style>
