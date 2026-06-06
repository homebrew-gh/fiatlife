import { ApiError, api } from "./api";

export type BlobDescriptor = {
  url: string;
  sha256: string;
  size: number;
  type: string;
  uploaded: number;
};

export type BlossomStatus = {
  configured: boolean;
  url: string | null;
};

export class BlossomNotConfiguredError extends Error {
  constructor() {
    super("Blossom server is not configured. Set the URL in Android Settings to sync.");
    this.name = "BlossomNotConfiguredError";
  }
}

export async function fetchBlossomStatus(): Promise<BlossomStatus> {
  try {
    return await api.blossomStatus();
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) {
      return { configured: false, url: null };
    }
    throw e;
  }
}

export async function uploadBlob(
  file: File,
): Promise<BlobDescriptor> {
  const status = await fetchBlossomStatus();
  if (!status.configured) {
    throw new BlossomNotConfiguredError();
  }
  return api.blossomUpload(file);
}

export async function downloadBlob(sha256: string): Promise<Blob> {
  const status = await fetchBlossomStatus();
  if (!status.configured) {
    throw new BlossomNotConfiguredError();
  }
  return api.blossomDownload(sha256);
}

export function blobObjectUrl(blob: Blob): string {
  return URL.createObjectURL(blob);
}
