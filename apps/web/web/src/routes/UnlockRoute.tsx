import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { AuthCard } from "../components/AuthCard";
import { SecretInput } from "../components/SecretInput";
import { ApiError, api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { hasRelayConfigured } from "../lib/relayUrl";

export function UnlockRoute() {
  const { status, loading, refresh } = useAuth();
  const navigate = useNavigate();
  const [passphrase, setPassphrase] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (loading) return null;
  if (!status?.has_state) return <Navigate to="/setup" replace />;
  if (status.unlocked) {
    return (
      <Navigate
        to={hasRelayConfigured(status) ? "/app" : "/relay-setup"}
        replace
      />
    );
  }

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const next = await api.authUnlock({ passphrase });
      await refresh();
      navigate(hasRelayConfigured(next) ? "/app" : "/relay-setup", { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Unlock failed.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthCard
      title="Unlock"
      subtitle="Enter your passphrase to connect to your Nostr relay."
    >
      <form className="space-y-4" onSubmit={onSubmit}>
        <div>
          <label className="label" htmlFor="pass">
            Passphrase
          </label>
          <SecretInput
            id="pass"
            autoComplete="current-password"
            autoFocus
            value={passphrase}
            onChange={(e) => setPassphrase(e.target.value)}
          />
        </div>
        {error ? (
          <p className="text-sm text-error" role="alert">
            {error}
          </p>
        ) : null}
        <button
          type="submit"
          className="btn-primary w-full"
          disabled={submitting || passphrase.length === 0}
        >
          {submitting ? "Unlocking…" : "Unlock"}
        </button>
      </form>
    </AuthCard>
  );
}
