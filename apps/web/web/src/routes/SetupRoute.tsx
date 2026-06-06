import { useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { AuthCard } from "../components/AuthCard";
import { DetectedRelayNotice, hasDetectedRelay } from "../components/DetectedRelayNotice";
import { SecretInput } from "../components/SecretInput";
import { ApiError, api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { isAllowedRelayUrl, RELAY_URL_POLICY } from "../lib/relayUrl";

export function SetupRoute() {
  const { status, loading, refresh } = useAuth();
  const navigate = useNavigate();
  const autoRelay = hasDetectedRelay(status);

  const [nsec, setNsec] = useState("");
  const [passphrase, setPassphrase] = useState("");
  const [confirm, setConfirm] = useState("");
  const [relayUrl, setRelayUrl] = useState("wss://");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setError(null);
  }, [nsec, passphrase, confirm, relayUrl]);

  if (loading) return null;
  if (status?.has_state) {
    return <Navigate to={status.unlocked ? "/app" : "/unlock"} replace />;
  }

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!nsec.startsWith("nsec1")) {
      setError("Secret key must be an nsec1… string (same key as your Android app).");
      return;
    }
    if (passphrase.length < 8) {
      setError("Passphrase must be at least 8 characters.");
      return;
    }
    if (passphrase !== confirm) {
      setError("Passphrases do not match.");
      return;
    }
    if (!autoRelay && !isAllowedRelayUrl(relayUrl)) {
      setError(RELAY_URL_POLICY);
      return;
    }

    setSubmitting(true);
    try {
      await api.authSetup({
        nsec,
        passphrase,
        ...(autoRelay ? {} : { relay_url: relayUrl }),
      });
      await refresh();
      navigate("/app", { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Setup failed.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthCard
      title="Set Up FiatLife"
      subtitle="Paste the same nsec you use on Android. Your passphrase encrypts it on this server only."
    >
      <form className="space-y-4" onSubmit={onSubmit}>
        {status && autoRelay ? <DetectedRelayNotice status={status} /> : null}
        <div>
          <label className="label" htmlFor="nsec">
            Nostr secret key (nsec)
          </label>
          <SecretInput
            id="nsec"
            autoComplete="off"
            placeholder="nsec1…"
            value={nsec}
            onChange={(e) => setNsec(e.target.value.trim())}
          />
        </div>
        {!autoRelay ? (
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
        ) : null}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label className="label" htmlFor="pass">
              Passphrase
            </label>
            <SecretInput
              id="pass"
              autoComplete="new-password"
              value={passphrase}
              onChange={(e) => setPassphrase(e.target.value)}
            />
          </div>
          <div>
            <label className="label" htmlFor="confirm">
              Confirm passphrase
            </label>
            <SecretInput
              id="confirm"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
            />
          </div>
        </div>
        {error ? (
          <p className="text-sm text-error" role="alert">
            {error}
          </p>
        ) : null}
        <button type="submit" className="btn-primary w-full" disabled={submitting}>
          {submitting ? "Saving…" : "Save and unlock"}
        </button>
      </form>
    </AuthCard>
  );
}
