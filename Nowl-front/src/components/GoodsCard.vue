<script setup lang="ts">
import { computed } from 'vue'
import { Heart } from 'lucide-vue-next'
import LazyImage from './LazyImage.vue'
interface CardGoods {
  productId: number; title: string; price: number; image?: string; sellerName?: string;
  categoryName?: string; campusName?: string; schoolName?: string; isCollected?: boolean; collectCount?: number;
}
const props = withDefaults(defineProps<{product: CardGoods; showCollect?: boolean; pending?: boolean}>(), { showCollect: true, pending: false })
defineEmits<{(e:'select', id:number):void; (e:'collect'):void}>()
const title = computed(() => props.product.title.replace(/<\/?em>/gi, ''))
const price = computed(() => Number(props.product.price).toFixed(2))
</script>
<template>
  <article class="goods-card">
    <button class="card-content" :aria-label="'查看商品：' + title" @click="$emit('select', product.productId)">
      <div class="card-cover"><LazyImage :src="product.image || '/campus/item-placeholder.svg'" :alt="title" /></div>
      <div class="card-info"><h3>{{ title }}</h3><div class="card-price"><strong>¥{{ price }}</strong><span v-if="product.collectCount">{{ product.collectCount }}人收藏</span></div><p><span>{{ product.sellerName || '河工程同学' }}</span><span>{{ product.categoryName || '校园闲置' }}</span></p><small>{{ product.campusName || product.schoolName || '河北工程大学' }}</small></div>
    </button>
    <button v-if="showCollect" class="card-favorite" :class="{ collected: product.isCollected }" :aria-label="product.isCollected ? '取消收藏' : '收藏商品'" :aria-pressed="Boolean(product.isCollected)" :disabled="pending" @click="$emit('collect')"><Heart :size="17" :fill="product.isCollected ? 'currentColor' : 'none'" /></button>
  </article>
</template>
<style scoped>
.goods-card{position:relative;background:white;min-width:0;border:1px solid #e3eaf0;border-radius:16px;overflow:hidden;transition:transform .25s ease,box-shadow .25s ease}.goods-card:hover{transform:translateY(-4px);box-shadow:0 12px 28px #173b5817}.card-content{display:block;text-align:left;width:100%;height:100%}.card-cover{aspect-ratio:4/3;overflow:hidden;background:#edf2f6}.card-info{padding:16px}.card-info h3{color:#183047;font-size:14px;font-weight:650;line-height:1.6;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;min-height:45px}.card-price{display:flex;align-items:baseline;justify-content:space-between;gap:6px;margin:10px 0}.card-price strong{color:#a82634;font-size:21px}.card-price span,.card-info small{font-size:10px;color:#63788b}.card-info p{display:flex;justify-content:space-between;gap:8px;font-size:11px;color:#63788b;margin-bottom:8px}.card-info p span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.card-favorite{position:absolute;right:10px;top:10px;border-radius:50%;padding:10px;background:#ffffffef;color:#63788b;box-shadow:0 2px 8px #173b5814}.card-favorite.collected{color:#a82634}.card-favorite:disabled{opacity:.5}.card-content:focus-visible,.card-favorite:focus-visible{outline:3px solid #a82634;outline-offset:-3px}@media(max-width:600px){.card-info{padding:12px}.card-info h3{font-size:13px}.card-price strong{font-size:18px}.card-price span{display:none}}@media(prefers-reduced-motion:reduce){.goods-card{transition:none}.goods-card:hover{transform:none}}
</style>
