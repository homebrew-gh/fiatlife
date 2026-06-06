import type { ReactNode } from "react";
import { Logo } from "./Logo";

export function AuthCard({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  return (
    <div className="min-h-full w-full flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">
        <div className="flex items-center justify-center mb-6">
          <Logo />
        </div>
        <div className="card p-6">
          <h1 className="page-title">{title}</h1>
          {subtitle ? (
            <p className="text-muted text-sm mt-1">{subtitle}</p>
          ) : null}
          <div className="mt-5">{children}</div>
        </div>
        <p className="text-muted text-xs text-center mt-6">
          Personal finance synced over your own Nostr relay — same data as the Android app.
        </p>
      </div>
    </div>
  );
}
