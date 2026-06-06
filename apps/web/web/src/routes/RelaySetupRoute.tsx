import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { AuthCard } from "../components/AuthCard";
import { ApiError, api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { hasRelayConfigured, isAllowedRelayUrl, RELAY_URL_POLICY } from "../lib/relayUrl";

export function RelaySetupRoute() {
  const { status, loading, refresh } = useAuth();
  const navigate = useNavigate();
  const [relayUrl, setRelayUrl] = useState(status?.relay_url ?? "wss://");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (loading) return null;
  if (!status?.has_state) return <Navigate to="/setup" replace />;
  if (!status.unlocked) return <Navigate to="/unlock" replace />;
  if (hasRelayConfigured(status)) return <Navigate to="/app" replace />;

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!isAllowedRelayUrl(relayUrl)) {
      setError(RELAY_URL_POLICY);
      return;
    }
    setSubmitting(true);
    try {
      await api.setRelay({ relay_urls: [relayUrl], relay_url: relayUrl });
      await refresh();
      navigate("/app", { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not save relay.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthCard
      title="Set Your Nostr Relay"
      subtitle="Use the same relay URL as your Android FiatLife app."
    >
      <form className="space-y-4" onSubmit={onSubmit}>
        <div>
          <label className="label" htmlFor="relay">
            Relay URL
          </label>
          <input
            id="relay"
            className="input"
            type="url"
            autoComplete="off"
            placeholder="wss://relay.example.com"
            value={relayUrl}
            onChange={(e) => setRelayUrl(e.target.value.trim())}
          />
          <p className="text-xs text-muted mt-1">{RELAY_URL_POLICY}</p>
        </div>
        {error ? (
          <p className="text-sm text-error" role="alert">
            {error}
          </p>
        ) : null}
        <button type="submit" className="btn-primary w-full" disabled={submitting}>
          {submitting ? "Saving…" : "Save relay"}
        </button>
      </form>
    </AuthCard>
  );
}
