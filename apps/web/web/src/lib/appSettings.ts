export const APP_SETTINGS_D_TAG = "fiatlife/settings/app";

export type AppSettings = {
  schemaVersion: number;
  blossomUrl: string;
  flags: Record<string, string>;
  updatedAt: number;
};

export function defaultAppSettings(
  partial?: Partial<AppSettings>,
): AppSettings {
  return {
    schemaVersion: partial?.schemaVersion ?? 1,
    blossomUrl: partial?.blossomUrl ?? "",
    flags: partial?.flags ?? {},
    updatedAt: partial?.updatedAt ?? Date.now(),
  };
}

export function parseAppSettings(plaintext: string): AppSettings | null {
  try {
    const parsed = JSON.parse(plaintext) as Record<string, unknown>;
    return defaultAppSettings({
      schemaVersion: Number(parsed.schemaVersion ?? 1),
      blossomUrl: String(parsed.blossomUrl ?? "").trim(),
      flags:
        parsed.flags != null && typeof parsed.flags === "object"
          ? (parsed.flags as Record<string, string>)
          : {},
      updatedAt: Number(parsed.updatedAt ?? 0),
    });
  } catch {
    return null;
  }
}

export function serializeAppSettings(settings: AppSettings): string {
  return JSON.stringify(settings);
}
