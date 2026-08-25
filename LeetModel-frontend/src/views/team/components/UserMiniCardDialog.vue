<template>
  <Teleport to="body">
    <Transition name="user-card-slide">
      <div v-if="modelValue" class="user-card-mask" role="presentation" @click.self="close">
        <section class="user-card-dialog" role="dialog" aria-modal="true" aria-label="用户简约名片">
          <button class="user-card-close" type="button" aria-label="关闭个人名片" @click="close">×</button>
          <div class="user-card-avatar" :class="{ 'has-image': member?.avatarUrl }">
            <img v-if="member?.avatarUrl" :src="member.avatarUrl" :alt="`${displayName}的头像`" />
            <span v-else>{{ displayName.charAt(0) }}</span>
          </div>
          <h3>{{ displayName }}</h3>
          <p>{{ member?.role === 'leader' ? '队长 · 创建者' : '队伍成员' }}</p>
          <div class="user-card-roles">
            <span v-for="role in roles" :key="role">{{ role }}</span>
            <span v-if="roles.length === 0" class="role-empty">暂未分配职责</span>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  member: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue'])

const displayName = computed(() => props.member?.nickname || `用户 ${props.member?.userId || ''}`)
const roles = computed(() => [
  props.member?.modeler && '建模',
  props.member?.programmer && '编程',
  props.member?.writer && '论文',
].filter(Boolean))

function close() { emit('update:modelValue', false) }
function handleKeydown(event) { if (event.key === 'Escape' && props.modelValue) close() }

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<style scoped>
.user-card-mask { position: fixed; z-index: 3000; inset: 0; display: flex; align-items: flex-start; justify-content: center; padding-top: min(18vh, 150px); background: rgba(15, 23, 42, .38); backdrop-filter: blur(3px); }
.user-card-dialog { position: relative; width: min(360px, calc(100vw - 32px)); padding: 34px 30px 30px; border: 1px solid rgba(255,255,255,.72); border-radius: 22px; background: #fff; text-align: center; box-shadow: 0 24px 70px rgba(15,23,42,.24); }
.user-card-close { position: absolute; top: 14px; right: 16px; width: 32px; height: 32px; border: 0; border-radius: 50%; background: #f1f5f9; color: #64748b; cursor: pointer; font-size: 22px; line-height: 1; }
.user-card-close:hover,.user-card-close:focus-visible { outline: 2px solid #93c5fd; background: #e0edff; color: #1d4ed8; }
.user-card-avatar { display: flex; width: 88px; height: 88px; align-items: center; justify-content: center; margin: 0 auto 16px; border: 4px solid #dbeafe; border-radius: 50%; background: linear-gradient(135deg,#60a5fa,#1d4ed8); color: #fff; font-size: 34px; font-weight: 800; box-shadow: 0 0 0 7px #eff6ff; }
.user-card-avatar img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.user-card-dialog h3 { margin: 0; color: #0f172a; font-size: 22px; }
.user-card-dialog p { margin: 7px 0 18px; color: #64748b; font-size: 13px; }
.user-card-roles { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; }
.user-card-roles span { padding: 5px 10px; border: 1px solid #dbeafe; border-radius: 999px; background: #eff6ff; color: #31558a; font-size: 12px; }
.user-card-roles .role-empty { border-color: #e2e8f0; background: #f8fafc; color: #94a3b8; }
.user-card-slide-enter-active,.user-card-slide-leave-active { transition: opacity .28s ease; }
.user-card-slide-enter-active .user-card-dialog,.user-card-slide-leave-active .user-card-dialog { transition: transform .32s cubic-bezier(.2,.8,.2,1), opacity .26s ease; }
.user-card-slide-enter-from,.user-card-slide-leave-to { opacity: 0; }
.user-card-slide-enter-from .user-card-dialog { opacity: 0; transform: translateY(-170px); }
.user-card-slide-leave-to .user-card-dialog { opacity: 0; transform: translateY(-170px); }
@media (prefers-reduced-motion: reduce) { .user-card-slide-enter-active .user-card-dialog,.user-card-slide-leave-active .user-card-dialog { transition: opacity .18s ease; } .user-card-slide-enter-from .user-card-dialog,.user-card-slide-leave-to .user-card-dialog { transform: none; } }
</style>
