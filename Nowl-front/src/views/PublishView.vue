<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { AlertTriangle, DollarSign, Loader2, ShoppingBag } from 'lucide-vue-next'
import { publishGoods, getCategoryTree, getGoodsDetail, updateGoods } from '@/api/modules/goods'
import { useUserStore } from '@/stores/user'
import { ElMessage } from '@/utils/feedback'
import { showSuccess, showWarning, showNeedAuth } from '@/utils/modal'
import type { ItemCategory } from '@/types'
import ImageUpload from '@/components/ImageUpload.vue'
import SubPageShell from '@/components/SubPageShell.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const readFirstQueryValue = (raw: unknown) => (Array.isArray(raw) ? raw[0] : raw)
const parsePositiveQueryId = (raw: unknown) => {
  const value = readFirstQueryValue(raw)
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

const publishForm = ref({
  title: '',
  desc: '',
  price: '',
  condition: '9',
  categoryId: undefined as number | undefined,
  tradeType: 0,
  images: [] as string[],
  deliveryFee: '',
})

const categoryTree = ref<ItemCategory[]>([])
const selectedFirstCategoryId = ref<number | undefined>(undefined)
const selectedSecondCategoryId = ref<number | undefined>(undefined)
const firstLevelCategories = computed(() => categoryTree.value)
const secondLevelCategories = computed(() => {
  const parent = categoryTree.value.find(cat => cat.categoryId === selectedFirstCategoryId.value)
  return parent?.children || []
})
const loading = ref(false)

const productId = computed(() => parsePositiveQueryId(route.query.id))
const hasProductIdParam = computed(() => route.query.id !== undefined)
const isEditMode = computed(() => hasProductIdParam.value)
const relistMode = computed(() => {
  const value = readFirstQueryValue(route.query.relist)
  return value !== undefined && value !== '0' && value !== 'false'
})
const isRejected = computed(() => {
  const value = readFirstQueryValue(route.query.rejected)
  return value !== undefined && value !== '0' && value !== 'false'
})

const pageTitle = computed(() =>
  isRejected.value ? '重新编辑商品' : (relistMode.value ? '重新上架商品' : (isEditMode.value ? '编辑商品' : '发布闲置好物'))
)

const pageSubtitle = computed(() =>
  isRejected.value ? '修改商品信息后将重新进行审核' : (relistMode.value ? '修改商品信息后重新上架，将重新进行审核' : '写清成色与瑕疵，自主定价，校内当面交易')
)

const backTarget = computed(() => (isEditMode.value ? '/profile/my-goods' : '/market'))
const submitButtonText = computed(() =>
  relistMode.value ? '确认重新上架' : (isEditMode.value ? '确认修改' : '立即上架'),
)

// 格式化价格为 X.00 格式
const formatPrice = (value: string): string => {
  if (!value) return ''
  const num = parseFloat(value)
  if (isNaN(num)) return ''
  return num.toFixed(2)
}

// 获取分类列表
const fetchCategories = async () => {
  try {
    const res = await getCategoryTree()
    categoryTree.value = res || []
  } catch (error) {
    console.error('获取分类失败', error)
  }
}

// 获取现有商品详情（编辑模式）
const fetchGoodsDetail = async (id: number) => {
  try {
    const res = await getGoodsDetail(id)
    const goods = res
    
    // 优先从imageList加载多图,如果没有则使用封面图
    let images: string[] = []
    if (goods.imageList) {
      try {
        images = JSON.parse(goods.imageList)
      } catch {
        images = goods.image ? [goods.image] : []
      }
    } else if (goods.image) {
      images = [goods.image]
    }
    
    publishForm.value = {
      title: goods.title,
      desc: goods.description || '',
      price: goods.price.toFixed(2),
      condition: goods.itemCondition.toString(),
      categoryId: goods.categoryId,
      tradeType: goods.tradeType,
      images: images,
      deliveryFee: goods.deliveryFee ? goods.deliveryFee.toString() : '',
    }

  } catch {
    ElMessage.error('获取商品详情失败')
    router.push('/market')
  }
}

const applyCategorySelection = (categoryId?: number) => {
  if (!categoryId) return
  const direct = categoryTree.value.find(cat => cat.categoryId === categoryId)
  if (direct) {
    selectedFirstCategoryId.value = direct.categoryId
    selectedSecondCategoryId.value = undefined
    publishForm.value.categoryId = direct.categoryId
    return
  }
  for (const parent of categoryTree.value) {
    const child = parent.children?.find(item => item.categoryId === categoryId)
    if (child) {
      selectedFirstCategoryId.value = parent.categoryId
      selectedSecondCategoryId.value = child.categoryId
      publishForm.value.categoryId = child.categoryId
      return
    }
  }
}

const handleFirstCategoryChange = () => {
  selectedSecondCategoryId.value = undefined
  const parent = categoryTree.value.find(cat => cat.categoryId === selectedFirstCategoryId.value)
  const hasChildren = !!parent?.children?.length
  publishForm.value.categoryId = hasChildren ? undefined : selectedFirstCategoryId.value
}

const handleSecondCategoryChange = () => {
  publishForm.value.categoryId = selectedSecondCategoryId.value
}

// 价格输入失焦时格式化
const handlePriceBlur = () => {
  if (publishForm.value.price) {
    publishForm.value.price = formatPrice(publishForm.value.price)
  }
}

// 发布商品
const handlePublish = async () => {
  if (isEditMode.value && !productId.value) {
    ElMessage.error('商品编号无效')
    router.push('/profile/my-goods')
    return
  }
  if (!userStore.isLoggedIn) {
    showWarning('请先登录后再发布商品', '需要登录')
    router.push('/login')
    return
  }

  // 检查是否认证
  if (userStore.userInfo?.authStatus !== 2) {
    showNeedAuth('为保障交易安全，请先完成实名认证')
    router.push('/profile/auth')
    return
  }

  if (!publishForm.value.title || !publishForm.value.categoryId || !publishForm.value.price) {
    ElMessage.warning('请填写完整信息')
    return
  }

  const price = Number(publishForm.value.price)
  if (!Number.isFinite(price) || price <= 0) {
    ElMessage.warning('请输入大于 0 的有效价格')
    return
  }

  if (publishForm.value.images.length === 0) {
    ElMessage.warning('请至少上传一张商品图片')
    return
  }

  loading.value = true

  try {
    const goodsData = {
      title: publishForm.value.title,
      categoryId: publishForm.value.categoryId,
      price: Number(publishForm.value.price),
      description: publishForm.value.desc,
      image: publishForm.value.images[0],
      imageList: JSON.stringify(publishForm.value.images),
      itemCondition: Number(publishForm.value.condition),
      tradeType: publishForm.value.tradeType,
      deliveryFee: publishForm.value.tradeType === 0 ? 0 : (Number(publishForm.value.deliveryFee) || 0),
    }

    if (isEditMode.value) {
      // 重新上架模式下，设置tradeStatus=0使商品重新变为在售
      const updateData = relistMode.value
        ? { ...goodsData, tradeStatus: 0 }
        : goodsData
      await updateGoods(productId.value!, updateData)
    } else {
      await publishGoods(goodsData)
    }

    // 发布后使用轻提示，不阻断流程
    const title = relistMode.value ? '重新上架成功' : (isEditMode.value ? '修改成功' : '发布成功')
    showSuccess('您的商品已提交，系统正在后台进行智能审核，审核结果将通过系统消息通知您。', title)
    router.push('/market')

  } catch (err) {
    console.error('发布失败', err)
    ElMessage.error(isEditMode.value ? '更新失败' : '发布失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await fetchCategories()
  if (isEditMode.value && !productId.value) {
    ElMessage.error('商品编号无效')
    router.replace('/profile/my-goods')
    return
  }
  if (productId.value) {
    await fetchGoodsDetail(productId.value)
    applyCategorySelection(publishForm.value.categoryId)
  }
})
</script>

<template>
  <SubPageShell :title="pageTitle" :subtitle="pageSubtitle" :back-to="backTarget" max-width="lg">
    <template #icon>
      <ShoppingBag class="text-white w-8 h-8" stroke-width="2.5" />
    </template>

    <div class="space-y-6">
      <!-- 驳回提示横幅 -->
      <div v-if="isRejected" class="flex items-center gap-2 px-4 py-3 bg-orange-50 border border-orange-200 rounded-xl text-sm text-orange-700">
        <AlertTriangle :size="16" class="text-orange-500 shrink-0" />
        <span>该商品之前被驳回，修改后将重新进行审核</span>
      </div>

      <!-- 图片上传 -->
      <div>
        <label class="text-xs font-bold text-slate-400 uppercase ml-2">商品图片</label>
        <div class="mt-2">
          <ImageUpload v-model="publishForm.images" :max-count="9" :max-size="10" />
        </div>
      </div>

      <div class="space-y-4">
        <div>
          <label class="text-xs font-bold text-slate-400 uppercase ml-2">标题</label>
          <input
            type="text"
            class="w-full mt-1"
            placeholder="品牌、型号、品类..."
            v-model="publishForm.title"
          />
        </div>

        <div>
          <label class="text-xs font-bold text-slate-400 uppercase ml-2">分类</label>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-1">
            <div>
              <select
                v-model="selectedFirstCategoryId"
                class="w-full"
                @change="handleFirstCategoryChange"
              >
                <option :value="undefined" disabled>请选择一级分类</option>
                <option v-for="cat in firstLevelCategories" :key="cat.categoryId" :value="cat.categoryId">
                  {{ cat.categoryName }}
                </option>
              </select>
            </div>
            <div>
              <select
                v-model="selectedSecondCategoryId"
                class="w-full"
                :disabled="!selectedFirstCategoryId || secondLevelCategories.length === 0"
                @change="handleSecondCategoryChange"
              >
                <option :value="undefined" disabled>请选择二级分类</option>
                <option v-for="cat in secondLevelCategories" :key="cat.categoryId" :value="cat.categoryId">
                  {{ cat.categoryName }}
                </option>
              </select>
            </div>
          </div>
        </div>

        <div>
          <label class="text-xs font-bold text-slate-400 uppercase ml-2">描述</label>
          <textarea
            rows="4"
            class="w-full mt-1"
            placeholder="例如：购买时间、使用情况、已有瑕疵、包含配件，以及方便面交的校内地点。"
            v-model="publishForm.desc"
          ></textarea>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <!-- 自主定价 -->
          <div>
            <label class="text-xs font-bold text-slate-400 uppercase ml-2">价格</label>
            <div class="relative mt-1">
              <DollarSign class="absolute left-3 top-3.5 text-slate-400" :size="16" />
              <input
                type="text"
                class="w-full um-input-with-prefix pr-4 py-3 transition-colors"
                placeholder="0.00"
                v-model="publishForm.price"
                @blur="handlePriceBlur"
              />
            </div>
          </div>

          <div>
            <label class="text-xs font-bold text-slate-400 uppercase ml-2"
              >成色 ({{ publishForm.condition }}成新)</label
            >
            <input
              type="range"
              min="1"
              max="10"
              class="w-full mt-4 accent-warm-500"
              v-model="publishForm.condition"
            />
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="text-xs font-bold text-slate-400 uppercase ml-2">交易方式</label>
            <div class="flex gap-4 mt-2">
              <label class="flex items-center gap-2">
                <input
                  v-model="publishForm.tradeType"
                  type="radio"
                  :value="0"
                  class="text-warm-500"
                />
                <span class="text-sm">仅线下</span>
              </label>
              <label class="flex items-center gap-2">
                <input
                  v-model="publishForm.tradeType"
                  type="radio"
                  :value="1"
                  class="text-warm-500"
                />
                <span class="text-sm">仅邮寄</span>
              </label>
              <label class="flex items-center gap-2">
                <input
                  v-model="publishForm.tradeType"
                  type="radio"
                  :value="2"
                  class="text-warm-500"
                />
                <span class="text-sm">皆可</span>
              </label>
            </div>
          </div>
          
          <div>
            <label class="text-xs font-bold text-slate-400 uppercase ml-2">
              邮费
              <span v-if="publishForm.tradeType === 0" class="text-slate-300 font-normal normal-case">(线下不需要)</span>
            </label>
            <div class="relative mt-1">
              <span class="absolute left-3 top-3 text-slate-400 text-sm">¥</span>
              <input
                type="text"
                class="w-full um-input-with-prefix pr-4 py-3 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                placeholder="0.00"
                v-model="publishForm.deliveryFee"
                :disabled="publishForm.tradeType === 0"
              />
            </div>
          </div>
        </div>
      </div>

      <div class="pt-4">
        <button
          @click="handlePublish"
          :disabled="loading"
          class="w-full py-4 um-btn um-btn-primary disabled:opacity-50 flex items-center justify-center gap-2"
        >
          <Loader2 v-if="loading" class="animate-spin" :size="18" />
          <span v-if="loading">处理中...</span>
          <span v-else>{{ submitButtonText }}</span>
        </button>
      </div>
    </div>
  </SubPageShell>
</template>
