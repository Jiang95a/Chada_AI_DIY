import './assets/main.css'
import './assets/studio-base.css'
import './assets/studio-workspace.css'
import './assets/studio-projects.css'
import './assets/studio-new-project.css'
import './assets/studio-editor.css'
import './assets/studio-activity.css'
import './assets/studio-data.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
