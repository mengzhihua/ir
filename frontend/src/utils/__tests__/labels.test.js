import { describe, expect, it } from 'vitest'
import { actionStatusLabels, orderStatusLabels, scenarioKindLabels, labelOf } from '../labels'

describe('labelOf', () => {
  it('returns translated labels from different maps', () => {
    expect(labelOf('CREATED', orderStatusLabels)).toBe('已创建')
    expect(labelOf('AUTO', scenarioKindLabels)).toBe('系统自动')
    expect(labelOf('SUCCESS', actionStatusLabels)).toBe('成功')
  })

  it('falls back to the original value for unknown labels', () => {
    expect(labelOf('CUSTOM', orderStatusLabels)).toBe('CUSTOM')
    expect(labelOf('', orderStatusLabels)).toBe('-')
  })
})
