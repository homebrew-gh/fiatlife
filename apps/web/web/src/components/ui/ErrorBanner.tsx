type ErrorBannerProps = {
  message: string;
};

/** Consistent error presentation across tabs. */
export function ErrorBanner({ message }: ErrorBannerProps) {
  return (
    <p className="notice-error text-sm" role="alert">
      {message}
    </p>
  );
}
