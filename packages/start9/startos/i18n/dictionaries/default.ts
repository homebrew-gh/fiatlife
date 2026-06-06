export const DEFAULT_LANG = 'en_US'

const dict = {
  'Starting FiatLife': 0,
  'Web UI': 1,
  'The FiatLife web UI is ready': 2,
  'The FiatLife web UI is not ready': 3,
  'FiatLife web interface': 4,
} as const

export type I18nKey = keyof typeof dict
export type LangDict = Record<(typeof dict)[I18nKey], string>
export default dict
