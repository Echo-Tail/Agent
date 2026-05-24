import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import LoginView from '@/views/login/LoginView.vue'
import i18n from '@/locales'

const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ name: 'Login' }),
}))

vi.mock('@/api/auth', () => ({
  loginApi: vi.fn(),
  registerApi: vi.fn(),
  getCurrentUserApi: vi.fn(),
}))

beforeEach(() => {
  i18n.global.locale.value = 'zh-CN' as 'zh-CN' | 'en'
})

function createWrapper() {
  return mount(LoginView, {
    global: {
      plugins: [setActivePinia(createPinia()), i18n],
      stubs: {
        'router-link': { template: '<a class="stub-router-link"><slot /></a>' },
        Input: { template: '<input class="stub-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />', props: ['modelValue'] },
        Label: { template: '<label><slot /></label>' },
        Card: { template: '<div class="stub-card"><slot /></div>' },
        CardHeader: { template: '<div class="stub-card-header"><slot /></div>' },
        CardContent: { template: '<div class="stub-card-content"><slot /></div>' },
        CardFooter: { template: '<div class="stub-card-footer"><slot /></div>' },
        CardTitle: { template: '<div class="stub-card-title"><slot /></div>' },
        CardDescription: { template: '<div class="stub-card-desc"><slot /></div>' },
      },
    },
  })
}

beforeEach(() => {
  mockPush.mockClear()
})

describe('LoginView', () => {
  it('renders the login form', () => {
    const wrapper = createWrapper()
    expect(wrapper.text()).toContain('EcomAgents')
    expect(wrapper.text()).toContain('登录 EcomAgents')
  })

  it('renders username and password inputs', () => {
    const wrapper = createWrapper()
    expect(wrapper.text()).toContain('用户名')
    expect(wrapper.text()).toContain('密码')
  })

  it('shows register link', () => {
    const wrapper = createWrapper()
    expect(wrapper.text()).toContain('没有账号？')
    expect(wrapper.text()).toContain('立即注册')
  })

  it('shows error when logging in with empty fields', async () => {
    const wrapper = createWrapper()
    // Call handleLogin directly by mounting and interacting through the exposed function
    // Set empty values and trigger the keyup.enter event
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThanOrEqual(2)

    // type empty strings (already default) and trigger keyup.enter on the password field
    await inputs[1].trigger('keyup.enter')
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('请输入用户名和密码')
  })
})
