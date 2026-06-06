/** Biller (company/payee) aligned with Android `domain/model/Biller.kt`. */

export type Biller = {
  id: string;
  name: string;
  normalizedName: string;
  linkedBillId?: string | null;
  isArchived: boolean;
  updatedAt: number;
};

export const BILLER_D_TAG_PREFIX = "fiatlife/biller/";

export function billerDTag(id: string): string {
  return `${BILLER_D_TAG_PREFIX}${id}`;
}

export function normalizeCompanyName(name: string): string {
  return name.trim().toLowerCase().replace(/\s+/g, " ");
}

export function newBillerId(): string {
  return crypto.randomUUID();
}

export function parseBillerRecord(
  dTag: string,
  plaintext: string,
): Biller | null {
  try {
    const parsed = JSON.parse(plaintext) as Record<string, unknown>;
    if (parsed.deleted === true) return null;
    const id = String(parsed.id ?? dTag.replace(BILLER_D_TAG_PREFIX, ""));
    const name = String(parsed.name ?? "").trim();
    if (!name) return null;
    return {
      id,
      name,
      normalizedName: String(
        parsed.normalizedName ?? normalizeCompanyName(name),
      ),
      linkedBillId:
        parsed.linkedBillId != null ? String(parsed.linkedBillId) : null,
      isArchived: Boolean(parsed.isArchived),
      updatedAt: Number(parsed.updatedAt ?? 0),
    };
  } catch {
    return null;
  }
}

export function serializeBiller(biller: Biller): string {
  return JSON.stringify(biller);
}
