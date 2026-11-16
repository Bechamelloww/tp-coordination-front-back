import {demarrerTraces} from './tracing';demarrerTraces();// avant createApp : le premier fetch est déjà tracé
import {createApp} from 'vue';import {createPinia} from 'pinia';import App from './App.vue';import './style.css';import {startFlushTriggers} from './offline/flush';createApp(App).use(createPinia()).mount('#app');startFlushTriggers();
