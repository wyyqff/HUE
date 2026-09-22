<script setup lang="ts">
import { ElImage, ElSkeletonItem } from 'element-plus'
import { ImageOff } from 'lucide-vue-next'
withDefaults(defineProps<{src: string; alt?: string; placeholder?: string; lazy?: boolean}>(), { alt: '', lazy: true })
const emit = defineEmits<{(e: 'load', event: Event): void; (e: 'error', event: Event): void}>()
</script>
<template>
  <ElImage :src="src" :alt="alt" :lazy="lazy" fit="cover" class="campus-image" @load="event => emit('load', event)" @error="event => emit('error', event)">
    <template #placeholder><ElSkeletonItem variant="image" class="image-state" /></template>
    <template #error><div class="image-state image-error"><ImageOff :size="25" /><span>图片暂不可用</span></div></template>
  </ElImage>
</template>
<style scoped>
.campus-image{display:block;width:100%;height:100%}.image-state{width:100%;height:100%;min-height:100px}.image-error{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#7892a5;background:#edf2f6;font-size:12px}.campus-image :deep(img){display:block;width:100%;height:100%;transition:opacity .3s ease}@media(prefers-reduced-motion:reduce){.campus-image :deep(img){transition:none}}
</style>
