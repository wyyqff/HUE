<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  MapPin,
  Search,
  LoaderCircle,
  BookOpen,
  Laptop,
  Shirt,
  Volleyball,
  LampDesk,
  ShoppingBag,
} from 'lucide-vue-next'
import CampusGuide from '@/components/CampusGuide.vue'
import GoodsCard from '@/components/GoodsCard.vue'
import MarketEmpty from '@/components/MarketEmpty.vue'
import { getHomeRecommend } from '@/api/modules/recommend'
import { useUserStore } from '@/stores/user'
import { DEFAULT_CAMPUS } from '@/config/campus'
import { getCategoryTree } from '@/api/modules/goods'
import type { ItemCategory } from '@/types'
import type { RecommendItemVO } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const campusName = computed(() => userStore.currentCampus?.name || DEFAULT_CAMPUS.name)
const keyword = ref('')
const categories = ref<ItemCategory[]>([])
const categoryIcon = (name: string) =>
  /书|教材/.test(name)
    ? BookOpen
    : /电子|数码/.test(name)
      ? Laptop
      : /服饰/.test(name)
        ? Shirt
        : /运动/.test(name)
          ? Volleyball
          : /学习|办公/.test(name)
            ? LampDesk
            : ShoppingBag
onMounted(async () => {
  try {
    categories.value = (await getCategoryTree()).slice(0, 6)
  } catch {
    /* Search stays available when categories cannot load. */
  }
})
const goods = ref<RecommendItemVO[]>([])
const loading = ref(false)
const failed = ref(false)
let requestVersion = 0
async function fetchGoods() {
  const version = ++requestVersion
  loading.value = true
  failed.value = false
  try {
    const result = await getHomeRecommend(
      1,
      8,
      userStore.currentCampus?.schoolCode,
      userStore.currentCampus?.campusCode,
    )
    if (version === requestVersion)
      goods.value = (result.records || []).filter((item) => item.productId > 0)
  } catch {
    if (version === requestVersion) {
      goods.value = []
      failed.value = true
    }
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
function goToMarket() {
  router.push({ path: '/market', query: keyword.value.trim() ? { q: keyword.value.trim() } : {} })
}
function goToPublish() {
  router.push(userStore.isLoggedIn ? '/publish' : '/login?redirect=/publish')
}
watch(() => userStore.currentCampus, fetchGoods, { deep: true, immediate: true })
</script>

<template>
  <div class="home-page">
    <section class="hero">
      <div class="hero-text">
        <div class="campus-tag">
          <MapPin :size="14" />
          {{ campusName }} · 同校共享
        </div>
        <h1>
          河北工程大学<br />
          <span>让校园生活，更近一点</span>
        </h1>
        <p>
          崇德尚善 ·
          精工铸新。在这里发现闲置好物、发布跑腿需求、探索熟悉的校园，与河工程同学分享每一天。
        </p>
        <div class="hero-actions">
          <button type="button" class="hero-btn primary" @click="goToMarket">
            逛逛校园集市
            <ArrowRight :size="16" />
          </button>
          <button type="button" class="hero-btn secondary" @click="goToPublish">
            发布闲置好物
          </button>
        </div>
      </div>

      <div class="hero-visual">
        <figure class="campus-photo">
          <img
            src="/campus/wanxia.jpg"
            alt="河北工程大学湖畔晚霞与天鹅，7972像素高清校园实景"
            width="7972"
            height="3307"
            fetchpriority="high"
            decoding="async"
          />
          <figcaption>
            <span>HEBEU · CAMPUS MOMENTS</span><strong>湖畔晚霞 · 定格河工程的美好</strong>
          </figcaption>
        </figure>
      </div>
    </section>
    <section class="market-preview" aria-labelledby="market-preview-title">
      <div class="market-preview-head">
        <div>
          <p class="market-eyebrow">CAMPUS MARKET</p>
          <h2 id="market-preview-title">让闲置，遇见新主人</h2>
          <p>同校找好物，聊清楚再下单，见面验货更放心。</p>
        </div>
        <button class="all-goods" @click="goToMarket">浏览集市 <ArrowRight :size="16" /></button>
      </div>
      <form class="home-search" @submit.prevent="goToMarket">
        <Search :size="20" /><input
          v-model="keyword"
          aria-label="搜索校园二手商品"
          placeholder="搜索教材、数码、生活用品…"
          maxlength="100"
        /><button type="submit">找好物</button>
      </form>
      <nav v-if="categories.length" class="category-shortcuts" aria-label="商品分类快捷入口">
        <button
          v-for="category in categories"
          :key="category.categoryId"
          @click="router.push({ path: '/market', query: { c1: String(category.categoryId) } })"
        >
          <component :is="categoryIcon(category.categoryName)" :size="24" /><span>{{
            category.categoryName
          }}</span
          ><ArrowRight :size="14" />
        </button>
      </nav>
      <div v-if="loading" class="market-state" role="status">
        <LoaderCircle class="spin" :size="25" />
        <p>正在加载校内好物…</p>
      </div>
      <div v-else-if="failed" class="market-state" role="status">
        <p>商品暂时未加载成功</p>
        <button @click="fetchGoods">重新加载</button>
      </div>
      <MarketEmpty v-else-if="!goods.length" @publish="goToPublish" />
      <div v-else class="home-goods-grid">
        <GoodsCard
          v-for="item in goods"
          :key="item.productId"
          :product="item"
          :show-collect="false"
          @select="(id) => router.push('/product/' + id)"
        />
      </div>
      <div class="trade-steps">
        <span>01 · 如实描述成色与瑕疵</span><span>02 · 站内沟通约好面交</span
        ><span>03 · 当面检查后确认收货</span>
      </div>
    </section>
    <CampusGuide />
  </div>
</template>
<style scoped src="./HomeView.css"></style>
