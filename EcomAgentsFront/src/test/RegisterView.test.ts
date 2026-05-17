import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { NConfigProvider, NMessageProvider, zhCN, dateZhCN } from 'naive-ui'
import RegisterView from '../views/register/RegisterView.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ name: 'Register' }),
}))

vi.mock('../api/auth', () => ({
  loginApi: vi.fn(),
  registerApi: vi.fn(),
}))

describe('RegisterView', () => {
  function createWrapper() {
    const Wrapper = defineComponent({
      setup() {
        return () =>
          h(NConfigProvider, { locale: zhCN, 'date-locale': dateZhCN }, () =>
            h(NMessageProvider, null, () =>
              h(RegisterView),
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

  it('renders the registration form', () => {
    const wrapper = createWrapper()
    expect(wrapper.text()).toContain('注册账号')
    expect(wrapper.text()).toContain('创建你的 EcomAgents 账号')
  })

  it('renders without useMessage error', () => {
    const wrapper = createWrapper()
    expect(wrapper.find('.auth-card').exists()).toBe(true)
  })

  it('renders the register button', () => {
    const wrapper = createWrapper()
    expect(wrapper.text()).toContain('注 册')
  })

  it('renders login link', () => {
    const wrapper = createWrapper()
    expect(wrapper.text()).toContain('已有账号？')
    expect(wrapper.text()).toContain('返回登录')
  })
})
