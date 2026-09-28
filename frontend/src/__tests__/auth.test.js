import { beforeEach, describe, expect, it } from 'vitest'

const storage = new Map()
globalThis.localStorage = {
  getItem: (key) => storage.get(key) || null,
  setItem: (key, value) => storage.set(key, value),
  removeItem: (key) => storage.delete(key)
}

const { auth, canWrite, isAdmin } = await import('../auth')

describe('auth roles', () => {
  beforeEach(() => {
    auth.user = null
  })

  it('allows administrators to write and identifies them as admins', () => {
    auth.user = { role: 'ADMIN' }
    expect(canWrite()).toBe(true)
    expect(isAdmin()).toBe(true)
  })

  it('allows planners to write but not administer', () => {
    auth.user = { role: 'PLANNER' }
    expect(canWrite()).toBe(true)
    expect(isAdmin()).toBe(false)
  })

  it('keeps viewers and anonymous users read-only', () => {
    auth.user = { role: 'VIEWER' }
    expect(canWrite()).toBe(false)
    expect(isAdmin()).toBe(false)
    auth.user = null
    expect(canWrite()).toBe(false)
    expect(isAdmin()).toBe(false)
  })
})
