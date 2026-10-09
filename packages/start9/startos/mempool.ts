/** StartOS Mempool package (optional dependency) — local BTC/USD price source. */
export const mempoolPackageId = 'mempool'
export const mempoolInterfaceId = 'webui'
export const mempoolInternalPort = 8080
export const mempoolInternalUrl = `http://${mempoolPackageId}.startos:${mempoolInternalPort}`
