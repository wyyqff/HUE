<script setup lang="ts">
import { useRouter } from 'vue-router'
import {
  Settings,
  LockKeyhole,
  UserRound,
  ShieldCheck,
  ChevronRight,
  BookOpen,
} from 'lucide-vue-next'
import SubPageShell from '@/components/SubPageShell.vue'
const router = useRouter()
const entries = [
  { title: '个人资料', text: '修改头像、昵称和校区信息', icon: UserRound, path: '/profile/edit' },
  { title: '校园认证', text: '查看认证状态与审核结果', icon: ShieldCheck, path: '/profile/auth' },
  {
    title: '重置登录密码',
    text: '通过手机验证重置密码',
    icon: LockKeyhole,
    path: '/forgot-password',
  },
  { title: '交易帮助', text: '发布、面交、退款与交易争议', icon: BookOpen, path: '/profile/help' },
]
</script>
<template>
  <SubPageShell
    title="账号设置"
    subtitle="管理个人资料与账号安全"
    back-to="/profile"
    max-width="lg"
  >
    <template #icon><Settings class="text-white w-8 h-8" /></template>
    <div class="settings-list">
      <button v-for="item in entries" :key="item.path" @click="router.push(item.path)">
        <span class="entry-icon"><component :is="item.icon" :size="21" /></span>
        <span class="entry-copy"
          ><strong>{{ item.title }}</strong
          ><span>{{ item.text }}</span></span
        >
        <ChevronRight :size="18" />
      </button>
    </div>
  </SubPageShell>
</template>
<style scoped>
.settings-list {
  display: grid;
  gap: 12px;
}
.settings-list button {
  display: flex;
  align-items: center;
  gap: 16px;
  text-align: left;
  padding: 20px;
  border: 1px solid var(--um-border);
  border-radius: 14px;
  transition:
    background 0.2s,
    border-color 0.2s;
}
.settings-list button:hover {
  background: var(--um-bg-soft);
  border-color: var(--um-primary);
}
.entry-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  background: var(--um-primary-100);
  color: var(--um-primary);
  border-radius: 12px;
  flex-shrink: 0;
}
.entry-copy {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 6px;
}
.entry-copy strong {
  font-size: 15px;
}
.entry-copy > span {
  font-size: 12px;
  color: var(--um-muted);
  line-height: 1.6;
}
</style>
