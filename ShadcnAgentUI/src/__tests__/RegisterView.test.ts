import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import RegisterView from '@/views/register/RegisterView.vue'
import i18n from '@/locales'

const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ name: 'Register' }),
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
  return mount(RegisterView, {
    global: {
      plugins: [setActivePinia(createPinia()), i18n],
      stubs: {
        'router-link': { template: '<a class="stub-router-link"><slot /></a>' },
        Input: { template: '<input class="stub-input" :type="type" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />', props: ['modelValue', 'type'] },
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

describe('RegisterView', () => {
  it('renders the register form', () => {
    const wrapper = createWrapper()
    expect(wrapper.text()).toContain('EcomAgents')
    expect(wrapper.text()).toContain('注册 EcomAgents')
  })

  it('renders all input fields', () => {
    const wrapper = createWrapper()
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBe(4) // username, password, email, inviteCode
  })

  it('shows login link', () => {
    const wrapper = createWrapper()
    expect(wrapper.text()).toContain('已有账号？')
    expect(wrapper.text()).toContain('立即登录')
  })

  it('password input defaults to type="password"', () => {
    const wrapper = createWrapper()
    const inputs = wrapper.findAll('input')
    const passwordInput = inputs[1]
    expect(passwordInput.attributes('type')).toBe('password')
  })

  it('renders eye toggle button inside password field', () => {
    const wrapper = createWrapper()
    const toggleBtn = wrapper.find('.relative').find('button')
    expect(toggleBtn.exists()).toBe(true)
    expect(toggleBtn.attributes('type')).toBe('button')
  })

  it('toggles password type on eye icon click', async () => {
    const wrapper = createWrapper()
    const inputs = wrapper.findAll('input')
    const passwordInput = inputs[1]
    const toggleBtn = wrapper.find('.relative').find('button')

    // Initially password hidden
    expect(passwordInput.attributes('type')).toBe('password')

    // Click to show
    await toggleBtn.trigger('click')
    expect(passwordInput.attributes('type')).toBe('text')

    // Click to hide again
    await toggleBtn.trigger('click')
    expect(passwordInput.attributes('type')).toBe('password')
  })

  it('shows error when registering with empty required fields', async () => {
    const wrapper = createWrapper()
    // RegisterView triggers validation on button click (no keyup.enter on inputs)
    const buttons = wrapper.findAll('button')
    const registerBtn = buttons.find(b => b.text().includes('注册'))
    expect(registerBtn).toBeDefined()

    await registerBtn!.trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('请填写所有必填字段')
  })
})
