import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as Icons from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './style.css'
import 'element-plus/dist/index.css'

const app = createApp(App)
Object.entries(Icons).forEach(([name, component]) => app.component(name, component))
app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.mount('#app')
