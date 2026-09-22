<script setup lang="ts">
import { ElEmpty, ElButton } from 'element-plus'
withDefaults(defineProps<{filtered?: boolean}>(), { filtered: false })
defineEmits<{(e: 'reset'): void; (e: 'publish'): void}>()
</script>
<template>
  <ElEmpty :image-size="95" :description="filtered ? '没有符合条件的商品，试试放宽预算或清空筛选' : '第一件好物，等你来发布'">
    <div class="empty-actions"><ElButton v-if="filtered" @click="$emit('reset')">清空筛选</ElButton><ElButton type="primary" @click="$emit('publish')">发布我的闲置</ElButton></div>
  </ElEmpty>
</template>
<style scoped>.empty-actions{display:flex;gap:10px}.empty-actions :deep(.el-button--primary){--el-color-primary:#173b58;--el-color-primary-light-3:#315b7a;--el-color-primary-dark-2:#10283b}</style>
