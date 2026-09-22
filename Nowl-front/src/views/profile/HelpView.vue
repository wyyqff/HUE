<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElCollapse, ElCollapseItem, ElButton } from 'element-plus'
import { BookOpen, Camera, MessagesSquare, Handshake } from 'lucide-vue-next'
import SubPageShell from '@/components/SubPageShell.vue'
const router = useRouter()
const open = ref('publish')
const steps = [
  { title: '拍清楚，说清楚', text: '展示商品实拍、成色、瑕疵和配件，自主定价。', icon: Camera },
  { title: '站内聊，约面交', text: '先确认商品情况，再约校内公共地点见面。', icon: MessagesSquare },
  { title: '先验货，再确认', text: '检查功能和外观，确认无误后完成订单。', icon: Handshake },
]
const questions = [
  {
    id: 'publish',
    title: '如何发布闲置商品？',
    text: '完成校园认证后，进入“发布闲置”，添加实拍图片、标题、分类、成色和价格。详细说明瑕疵与配件，提交后可在“我的商品”查看审核状态。未填写完的内容可保存为本机草稿。',
  },
  {
    id: 'search',
    title: '如何更快找到想要的商品？',
    text: '在“二手集市”输入关键词，按分类、预算和排序缩小范围。筛选条件会保留在网址中，刷新后可以继续浏览；点击“清空筛选”恢复全部结果。',
  },
  {
    id: 'meet',
    title: '面交前需要确认什么？',
    text: '通过“联系卖家”确认型号、功能、瑕疵、配件及价格。建议在校内公共场所碰面，现场检查后再确认收货，聊天和订单信息可留作交易凭据。',
  },
  {
    id: 'orders',
    title: '订单、退款和交易争议在哪里处理？',
    text: '在个人中心的“我买到的”或“我卖出的”查看订单，按当前订单显示的操作处理付款、交付、收货或退款。协商未能解决时，按订单中的争议入口提交事实说明与证据，在“交易争议”查看处理进度。',
  },
  {
    id: 'images',
    title: '如何查看商品细节？',
    text: '在商品详情点击图片可打开大图，支持缩放、旋转和切换。发布图片不会在前端压缩，请使用清晰实拍图，避免过度滤镜。',
  },
  {
    id: 'panorama',
    title: '校园实景导览如何使用？',
    text: '首页下方可以搜索校园场景并在本站打开学校全景。全景固定从主广场进入，进入后在画面内选择对应场景；需要保持联网。',
  },
]
</script>
<template>
  <SubPageShell
    title="交易帮助"
    subtitle="从发布闲置到当面交付，每一步都清楚"
    back-to="/profile"
    max-width="lg"
    :use-card="false"
  >
    <template #icon><BookOpen class="w-8 h-8 text-white" /></template>
    <div class="help-steps">
      <article v-for="(step, index) in steps" :key="step.title" class="um-card">
        <component :is="step.icon" :size="25" /><small>0{{ index + 1 }}</small>
        <h2>{{ step.title }}</h2>
        <p>{{ step.text }}</p>
      </article>
    </div>
    <section class="um-card help-faq">
      <h2>常见问题</h2>
      <ElCollapse v-model="open" accordion
        ><ElCollapseItem
          v-for="item in questions"
          :key="item.id"
          :name="item.id"
          :title="item.title"
          ><p>{{ item.text }}</p></ElCollapseItem
        ></ElCollapse
      >
    </section>
    <div class="help-actions">
      <ElButton type="primary" @click="router.push('/market')">逛二手集市</ElButton
      ><ElButton @click="router.push('/profile/my-orders')">查看我的订单</ElButton>
    </div>
  </SubPageShell>
</template>
<style scoped>
.help-steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.help-steps article {
  padding: 24px;
  position: relative;
}
.help-steps svg {
  color: var(--um-primary);
}
.help-steps small {
  position: absolute;
  right: 22px;
  top: 22px;
  color: #a9b9c7;
  font-size: 20px;
}
.help-steps h2 {
  font-size: 16px;
  margin: 20px 0 10px;
}
.help-steps p,
.help-faq p {
  font-size: 13px;
  line-height: 1.9;
  color: var(--um-muted);
}
.help-faq {
  padding: 24px;
}
.help-faq h2 {
  font-size: 18px;
  margin-bottom: 18px;
}
.help-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 24px;
}
.help-faq :deep(.el-collapse-item__header) {
  font-size: 14px;
  font-weight: 600;
  height: auto;
  min-height: 56px;
  line-height: 1.6;
  gap: 12px;
}
.help-faq :deep(.el-collapse) {
  --el-collapse-border-color: #e8eef3;
}
@media (max-width: 600px) {
  .help-steps {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .help-steps article {
    padding: 20px;
  }
  .help-steps h2 {
    margin-top: 12px;
  }
  .help-faq {
    padding: 18px;
  }
}
</style>
