/**
 * Turns the internal transaction key into a stable, customer-facing reference.
 * The internal key remains available for API calls and routing, but is never
 * printed as customer-facing text.
 */
export function claimReference(transactionId: string, processedAt?: string | null): string {
  const year = referenceYear(processedAt);
  let hash = 0x811c9dc5;

  for (let index = 0; index < transactionId.length; index += 1) {
    hash ^= transactionId.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }

  const numericReference = String(hash >>> 0).padStart(10, '0').slice(-8);
  return `SIN-${year}-${numericReference}`;
}

export function transactionRouteKey(transactionId: string): string {
  const bytes = new TextEncoder().encode(transactionId);
  const binary = Array.from(bytes, byte => String.fromCharCode(byte)).join('');
  const encoded = btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `case-${encoded}`;
}

export function transactionIdFromRouteKey(routeKey: string): string {
  if (!routeKey.startsWith('case-')) {
    return routeKey;
  }

  try {
    const encoded = routeKey.slice(5).replace(/-/g, '+').replace(/_/g, '/');
    const padded = encoded.padEnd(Math.ceil(encoded.length / 4) * 4, '=');
    const bytes = Uint8Array.from(atob(padded), character => character.charCodeAt(0));
    return new TextDecoder().decode(bytes);
  } catch {
    return routeKey;
  }
}

function referenceYear(processedAt?: string | null): number {
  if (processedAt) {
    const parsedDate = new Date(processedAt);
    if (!Number.isNaN(parsedDate.getTime())) {
      return parsedDate.getFullYear();
    }
  }

  return new Date().getFullYear();
}
