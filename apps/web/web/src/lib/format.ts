export function formatUsd(amount: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function truncateNpub(npub: string, head = 12, tail = 8): string {
  if (npub.length <= head + tail + 3) return npub;
  return `${npub.slice(0, head)}…${npub.slice(-tail)}`;
}
