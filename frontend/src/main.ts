import { createApp } from 'vue'
import {
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElIcon,
  ElImage,
  ElInput,
  ElInputNumber,
  ElOption,
  ElProgress,
  ElRadioButton,
  ElRadioGroup,
  ElSegmented,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTimeline,
  ElTimelineItem,
} from 'element-plus'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/drawer/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/image/style/css'
import 'element-plus/es/components/image-viewer/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/input-number/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/progress/style/css'
import 'element-plus/es/components/radio-button/style/css'
import 'element-plus/es/components/radio-group/style/css'
import 'element-plus/es/components/segmented/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/timeline/style/css'
import 'element-plus/es/components/timeline-item/style/css'
import './styles.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
;[
  ElDialog, ElDrawer, ElForm, ElFormItem, ElIcon, ElImage, ElInput, ElInputNumber, ElOption,
  ElProgress, ElRadioButton, ElRadioGroup, ElSegmented, ElSelect, ElTable,
  ElTableColumn, ElTimeline, ElTimelineItem,
].forEach((component) => app.use(component))
app.mount('#app')
