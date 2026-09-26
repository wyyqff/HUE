<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  MapPin,
  Search,
  Maximize2,
  RotateCcw,
  Copy,
  View,
  Map,
  LoaderCircle,
} from 'lucide-vue-next'
import type * as Leaflet from 'leaflet'
import 'leaflet/dist/leaflet.css'
import {
  campusMap,
  campusPlaces,
  searchCampusPlaces,
  type CampusPlace,
} from '@/config/campusPlaces'
import { ElMessage } from '@/utils/feedback'

const keyword = ref('')
const selected = ref<CampusPlace | null>(null)
const mode = ref<'map' | 'panorama'>('map')
const loading = ref(true)
const loadError = ref(false)
const ready = ref(false)
const copied = ref(false)
const panel = ref<HTMLElement | null>(null)
const mapElement = ref<HTMLElement | null>(null)
const matches = computed(() => searchCampusPlaces(keyword.value))
let map: Leaflet.Map | undefined
let mapTask: Promise<void> | undefined
let observer: IntersectionObserver | undefined
let resizeObserver: ResizeObserver | undefined
let disposed = false
const markers = new globalThis.Map<string, Leaflet.Marker>()
const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

function initializeMap() {
  if (mapTask) return mapTask
  mapTask = (async () => {
    const L = await import('leaflet')
    if (!mapElement.value || disposed) return
    map = L.map(mapElement.value, {
      crs: L.CRS.Simple,
      minZoom: -3,
      maxZoom: 1.5,
      zoomSnap: 0.25,
      zoomDelta: 0.5,
      scrollWheelZoom: false,
      attributionControl: false,
      maxBoundsViscosity: 0.85,
      zoomControl: false,
    })
    const bounds = L.latLngBounds([
      [0, 0],
      [campusMap.height, campusMap.width],
    ])
    const base = L.imageOverlay(campusMap.image, bounds, {
      alt: '河北工程大学校园立体沙盘导览图',
    }).addTo(map)
    base.on('load', () => {
      loading.value = false
    })
    base.on('error', () => {
      loading.value = false
      loadError.value = true
    })
    // Fill the panel without empty horizontal bands.
    const fittedZoom = map.getBoundsZoom(bounds, true)
    map.setMinZoom(fittedZoom)
    map.setView(bounds.getCenter(), fittedZoom)
    map.setMaxBounds(bounds)
    L.control.zoom({ zoomInTitle: '放大地图', zoomOutTitle: '缩小地图' }).addTo(map)
    for (const place of campusPlaces) {
      const marker = L.marker([campusMap.height - place.y, place.x], {
        title: place.name,
        icon: L.divIcon({
          className: 'campus-marker',
          html: '<span></span>',
          iconSize: [24, 24],
          iconAnchor: [12, 12],
        }),
        keyboard: true,
      }).addTo(map)
      marker.bindTooltip(place.name, {
        direction: 'top',
        offset: [0, -9],
        className: 'campus-map-label',
        permanent: true,
      })
      marker.on('click', () => {
        void selectPlace(place)
      })
      markers.set(place.id, marker)
    }
    ready.value = true
    resizeObserver = new ResizeObserver(() => {
      if (!map || !mapElement.value?.clientHeight) return
      map.invalidateSize({ pan: false })
      map.setMinZoom(map.getBoundsZoom(bounds, true))
      map.panInsideBounds(bounds, { animate: false })
    })
    resizeObserver.observe(mapElement.value)
  })().catch(() => {
    loadError.value = true
    loading.value = false
    mapTask = undefined
  })
  return mapTask
}

async function selectPlace(place: CampusPlace) {
  selected.value = place
  copied.value = false
  mode.value = 'map'
  await nextTick()
  await initializeMap()
  if (!map || disposed) return
  map.invalidateSize({ pan: false })
  for (const [id, marker] of markers) {
    marker.getElement()?.classList.toggle('is-selected', id === place.id)
    marker.setZIndexOffset(id === place.id ? 1000 : 0)
  }
  map.stop()
  map.flyTo([campusMap.height - place.y, place.x], 0.5, {
    duration: 0.9,
    animate: !reducedMotion(),
  })
}

async function search(value = keyword.value) {
  keyword.value = value.trim()
  const match = matches.value[0]
  if (match) await selectPlace(match)
}

async function reset() {
  mode.value = 'map'
  keyword.value = ''
  selected.value = null
  await nextTick()
  await initializeMap()
  if (!map) return
  map.invalidateSize({ pan: false })
  for (const marker of markers.values()) marker.getElement()?.classList.remove('is-selected')
  map.flyTo(
    [campusMap.height / 2, campusMap.width / 2],
    map.getBoundsZoom(
      [
        [0, 0],
        [campusMap.height, campusMap.width],
      ],
      true,
    ),
    { duration: 0.8, animate: !reducedMotion() },
  )
}

async function switchMode(value: 'map' | 'panorama') {
  mode.value = value
  if (value === 'map') {
    await nextTick()
    await initializeMap()
    map?.invalidateSize({ pan: false })
  }
}

async function copyPlace() {
  if (!selected.value) return
  try {
    await navigator.clipboard.writeText(
      `河北工程大学（太极路19号） · ${selected.value.name}。具体入口与时间请在聊天中确认。`,
    )
    copied.value = true
  } catch {
    ElMessage.info('复制未成功，可直接选择地点名称复制')
  }
}

async function fullscreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await panel.value?.requestFullscreen()
  } catch {
    ElMessage.info('当前浏览器不支持全屏，可使用地图缩放按钮查看')
  }
}

onMounted(() => {
  if (!panel.value) return
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        void initializeMap()
        observer?.disconnect()
      }
    },
    { rootMargin: '200px' },
  )
  observer.observe(panel.value)
})
onBeforeUnmount(() => {
  disposed = true
  observer?.disconnect()
  resizeObserver?.disconnect()
  map?.remove()
  markers.clear()
})
</script>

<template>
  <section id="campus-guide" class="campus-guide" aria-labelledby="guide-title">
    <div class="guide-heading">
      <div>
        <p class="eyebrow">MEET ON CAMPUS</p>
        <h2 id="guide-title">找个地方，见面交易</h2>
        <p>输入地标名称，地图直接带你找到它。</p>
      </div>
      <p class="address"><MapPin :size="16" /> 太极路19号 · 河北工程大学</p>
    </div>
    <div class="guide-layout">
      <aside>
        <form class="guide-search" @submit.prevent="search()">
          <Search :size="17" /><input
            v-model="keyword"
            aria-label="搜索校园地点"
            placeholder="图书馆、一教、南门…"
            maxlength="40"
          /><button type="submit">定位</button>
        </form>
        <div class="popular">
          <button
            v-for="name in ['图书馆', '教学楼', '精工湖', '南门']"
            :key="name"
            @click="search(name)"
          >
            {{ name }}
          </button>
        </div>
        <p class="count" role="status">
          {{ keyword ? `找到 ${matches.length} 个地点，点击即可定位` : '常用地标 · 点击直达' }}
        </p>
        <div class="results">
          <button
            v-for="place in matches"
            :key="place.id"
            :class="{ selected: selected?.id === place.id }"
            :aria-pressed="selected?.id === place.id"
            @click="selectPlace(place)"
          >
            <MapPin :size="15" /><span
              >{{ place.name }}<small>{{ place.category }}</small></span
            >
          </button>
          <p v-if="!matches.length">
            暂未标注这个地点。试试图书馆、教学楼或南门。<button
              class="clear-search"
              @click="keyword = ''"
            >
              查看全部地标
            </button>
          </p>
        </div>
        <p class="note">沙盘标记为建筑或场地区域，实际入口以现场标识为准。建议约在公共区域面交。</p>
      </aside>
      <div ref="panel" class="viewer-panel" :data-selected-place="selected?.id || ''">
        <div class="viewer-tools">
          <div class="view-tabs" aria-label="校园导览视图">
            <button
              :aria-pressed="mode === 'map'"
              :class="{ active: mode === 'map' }"
              @click="switchMode('map')"
            >
              <Map :size="15" /> 定位导览</button
            ><button
              :aria-pressed="mode === 'panorama'"
              :class="{ active: mode === 'panorama' }"
              @click="switchMode('panorama')"
            >
              <View :size="15" /> 实景全景
            </button>
          </div>
          <div class="tools">
            <button @click="reset" aria-label="回到校园总览" title="回到总览">
              <RotateCcw :size="17" /></button
            ><button @click="fullscreen" aria-label="全屏查看校园导览" title="全屏">
              <Maximize2 :size="17" />
            </button>
          </div>
        </div>
        <div v-show="mode === 'map'" class="map-stage">
          <div
            ref="mapElement"
            class="campus-map"
            role="region"
            aria-label="河北工程大学可交互校园导览"
          />
          <div v-if="(loading || !ready) && !loadError" class="map-loading" role="status">
            <LoaderCircle :size="22" class="spin" /> 正在加载校园导览…
          </div>
          <div v-if="loadError" class="map-loading" role="alert">
            地图暂未加载成功，请刷新页面重试。
          </div>
          <div class="map-caption">立体沙盘 · 支持拖动、双指缩放</div>
          <div v-if="selected" class="place-card" role="status">
            <div>
              <span>{{ selected.category }}</span>
              <h3>{{ selected.name }}</h3>
              <p>{{ selected.detail }}</p>
            </div>
            <button @click="copyPlace">
              <Copy :size="14" /> {{ copied ? '已复制' : '复制地点' }}
            </button>
          </div>
        </div>
        <div v-if="mode === 'panorama'" class="panorama-stage">
          <p>全景可旋转、缩放，在画面内切换场景；搜索地点会回到定位导览。</p>
          <iframe
            :src="campusMap.source"
            title="河北工程大学校园全景"
            allow="fullscreen; gyroscope; accelerometer"
            allowfullscreen
          />
        </div>
        <div class="map-footer">
          <span>{{ mode === 'map' ? '校园位置示意，非实时导航' : '学校公开全景 · 需要联网' }}</span
          ><a :href="campusMap.source" target="_blank" rel="noopener noreferrer"
            >底图来源：学校公开全景</a
          >
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.campus-guide {
  padding: 64px min(8%, 110px);
  background: #fff;
  color: #183047;
  scroll-margin-top: 100px;
}
.guide-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 20px;
  margin-bottom: 26px;
}
.eyebrow {
  font-size: 10px !important;
  letter-spacing: 3px;
  color: #a82634 !important;
  font-weight: 700;
  margin-bottom: 12px;
}
h2 {
  font-size: 30px;
  font-weight: 700;
  margin-bottom: 12px;
}
.guide-heading p {
  font-size: 13px;
  color: #63788b;
  line-height: 1.8;
}
.address {
  display: flex;
  gap: 6px;
  align-items: center;
  white-space: nowrap;
}
.guide-layout {
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr);
  gap: 24px;
}
.guide-layout aside {
  min-width: 0;
}
.guide-search {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #cddbe5;
  border-radius: 12px;
  padding: 7px 7px 7px 12px;
}
.guide-search input {
  min-width: 0;
  flex: 1;
  outline: none;
  font-size: 12px;
  background: transparent;
}
.guide-search:focus-within {
  outline: 2px solid #254b68;
  outline-offset: 2px;
}
.guide-search button {
  background: #173b58;
  color: white;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 12px;
  white-space: nowrap;
}
.popular {
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
  margin: 12px 0;
}
.popular button {
  background: #edf2f6;
  color: #254b68;
  border-radius: 20px;
  padding: 6px 10px;
  font-size: 11px;
}
.count {
  font-size: 11px;
  color: #63788b;
  margin: 18px 0 8px;
}
.results {
  max-height: 332px;
  overflow: auto;
  scrollbar-width: thin;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.results > button {
  display: flex;
  gap: 10px;
  align-items: center;
  text-align: left;
  padding: 11px;
  border-radius: 10px;
  font-size: 12px;
  transition: background 0.18s;
}
.results > button:hover,
.results > button.selected {
  background: #edf2f6;
  color: #a82634;
}
.results small {
  display: block;
  color: #82909d;
  font-size: 10px;
  margin-top: 3px;
}
.note,
.results p {
  font-size: 11px;
  line-height: 1.8;
  color: #63788b;
  margin-top: 15px;
}
.clear-search {
  display: block;
  color: #a82634;
  text-decoration: underline;
  margin-top: 8px;
}
.viewer-panel {
  height: 568px;
  min-width: 0;
  position: relative;
  overflow: hidden;
  border-radius: 18px;
  border: 1px solid #e1e7ec;
  background: #e8eeed;
  display: flex;
  flex-direction: column;
}
.viewer-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  background: #fff;
  flex-shrink: 0;
}
.view-tabs {
  display: flex;
  gap: 4px;
  padding: 3px;
  background: #f1f4f7;
  border-radius: 10px;
}
.view-tabs button {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 8px 12px;
  color: #64758a;
  border-radius: 8px;
}
.view-tabs button.active {
  background: white;
  color: #163d59;
  box-shadow: 0 2px 8px #1e3b5710;
}
.tools {
  display: flex;
  gap: 8px;
}
.tools button {
  padding: 8px;
  color: #36536d;
  border-radius: 8px;
}
.tools button:hover {
  background: #edf2f6;
}
.map-stage {
  position: relative;
  flex: 1;
  min-height: 0;
}
.campus-map {
  width: 100%;
  height: 100%;
  background: #e8eeed;
  z-index: 0;
}
.map-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: #edf2f6;
  z-index: 4;
  font-size: 13px;
}
.map-caption {
  position: absolute;
  top: 15px;
  right: 12px;
  z-index: 1;
  font-size: 10px;
  border-radius: 20px;
  padding: 7px 10px;
  background: #ffffffed;
  color: #486174;
  pointer-events: none;
}
.place-card {
  position: absolute;
  bottom: 14px;
  left: 14px;
  right: 14px;
  z-index: 1;
  background: #ffffffed;
  backdrop-filter: blur(12px);
  box-shadow: 0 6px 24px #142c3820;
  border-radius: 14px;
  padding: 15px 17px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}
.place-card span {
  font-size: 10px;
  color: #9e2635;
}
.place-card h3 {
  font-size: 17px;
  margin: 4px 0 6px;
  font-weight: 650;
}
.place-card p {
  font-size: 11px;
  line-height: 1.7;
  color: #657a8b;
  max-width: 390px;
}
.place-card button {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 9px 12px;
  border: 1px solid #d8e2e9;
  border-radius: 8px;
  font-size: 11px;
  background: #fff;
}
.map-footer {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 14px;
  font-size: 10px;
  color: #71808b;
  background: #fff;
  flex-shrink: 0;
}
.map-footer a {
  text-decoration: underline;
  text-underline-offset: 3px;
}
.panorama-stage {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #173b58;
}
.panorama-stage p {
  font-size: 11px;
  line-height: 1.7;
  color: #e4edf4;
  padding: 9px 16px;
}
.panorama-stage iframe {
  width: 100%;
  flex: 1;
  min-height: 0;
  border: 0;
}
.viewer-panel:fullscreen {
  border-radius: 0;
  width: 100vw;
  height: 100dvh;
}
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
:deep(.campus-marker span) {
  display: block;
  width: 18px;
  height: 18px;
  margin: 3px;
  border: 3px solid white;
  background: #1a5277;
  border-radius: 50%;
  box-shadow: 0 2px 7px #142c3870;
  transition:
    transform 0.2s,
    background 0.2s;
}
:deep(.campus-marker.is-selected span) {
  background: #b9253c;
  transform: scale(1.35);
  box-shadow: 0 0 0 8px #b9253c30;
}
:deep(.campus-map-label) {
  font-family: inherit;
  font-size: 10px;
  border: 0;
  border-radius: 6px;
  padding: 4px 7px;
  box-shadow: 0 2px 8px #173b5830;
  color: #163d59;
}
:deep(.leaflet-control-zoom) {
  border: 0 !important;
  box-shadow: 0 2px 12px #173b5825;
}
:deep(.leaflet-control-zoom a) {
  color: #183e59;
  border-color: #e4e9ee;
}
@media (max-width: 850px) {
  .campus-guide {
    padding: 38px 24px;
  }
  .guide-heading {
    display: block;
  }
  .address {
    margin-top: 10px;
  }
  .guide-layout {
    grid-template-columns: 1fr;
    gap: 18px;
  }
  .results {
    max-height: 152px;
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  .note {
    margin-top: 10px;
  }
  .viewer-panel {
    height: 490px;
  }
  h2 {
    font-size: 24px;
  }
  .place-card {
    padding: 12px;
    align-items: start;
  }
  .place-card h3 {
    font-size: 15px;
  }
  .place-card p {
    font-size: 10px;
  }
  .place-card button {
    font-size: 10px;
    padding: 8px;
  }
  .map-caption {
    font-size: 9px;
  }
  .view-tabs button {
    padding: 8px;
    font-size: 11px;
  }
  .tools {
    gap: 2px;
  }
  .map-footer {
    font-size: 9px;
    padding: 8px;
    gap: 6px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .spin {
    animation: none;
  }
  .results > button,
  :deep(.campus-marker span) {
    transition: none;
  }
}
</style>
