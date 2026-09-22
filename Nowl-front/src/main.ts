import './assets/main.css'
import 'element-plus/dist/index.css'
import './assets/experience.css'

// Retire the unused preferences without touching login, school or draft data.
try { localStorage.removeItem('um:profile-settings') } catch { /* Private browsing. */ }

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

import App from './App.vue'
import router from './router'

const app = createApp(App)

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

app.use(pinia)
app.use(router)

app.mount('#app')
