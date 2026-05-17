import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { NConfigProvider, NMessageProvider, zhCN, dateZhCN } from 'naive-ui'
import LoginView from '../views/login/LoginView.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ name: 'Login' }),
}))

vi.mock('../api/auth', () => ({
  loginApi: vi.fn(),
  registerApi: vi.fn(),
}))

describe('LoginView', () => {
  function createWrapper() {
    const Wrapper = defineComponent({
      setup() {
        return () =>
          h(NConfigProvider, { locale: zhCN, 'date-locale': dateZhCN }, () =>
            h(NMessageProvider, null, () =>
              h(LoginView),
            ),
          )
      },
    })
    return mount(Wrapper, {
      global: {
        plugins: [setActivePinia(createPinia())],
        stubs: {
          'n-dialog-provider': { template: '<div><slot /></div>' },
          'n-notification-provider': { template: '<div><slot /></div>' },
        },
      },
    })
  }

  it('renders the login form', () => {
    const wrapper = createWrapper()
    expect(wrapper.text()).toContain('EcomAgents')
    expect(wrapper.text()).toContain('企业电商智能体管理平台')
  })

  it('renders without useMessage error', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.auth-card').exists()).toBe(true)
  })

  it('shows register link', () => {
    const wrapper = createWrapper()
    expect(wrapper.text()).toContain('还没有账号？')
    expect(wrapper.text()).toContain('立即注册')
  })
})
