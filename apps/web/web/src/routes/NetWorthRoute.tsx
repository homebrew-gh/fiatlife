import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import clsx from "clsx";
import { BitcoinWalletSheet } from "../components/bitcoin/BitcoinWalletSheet";
import { ErrorBanner, HeroCard, PageHeader } from "../components/ui";
import {
  BANK_ACCOUNT_TYPE_LABELS,
  bankAccountType,
  isCashAccount,
  type BankAccount,
} from "../lib/bankAccount";
import { useBankAccountsData } from "../lib/bankAccountsData";
import {
  formatBtc,
  satsToUsd,
  totalSats,
  walletDetail,
  type BitcoinWallet,
} from "../lib/bitcoin";
import { useBitcoinData } from "../lib/bitcoinData";
import {
  CREDIT_ACCOUNT_TYPE_LABELS,
  type CreditAccount,
} from "../lib/creditAccount";
import { useDebtData } from "../lib/debtData";
import { formatUsd } from "../lib/format";
import { computeNetWorth } from "../lib/netWorth";
import { formatBalanceAsOf } from "../lib/simplefin";

type BreakdownItem = {
  key: string;
  label: string;
  detail?: string;
  value: string;
  negative?: boolean;
  to?: string;
  onClick?: () => void;
};

function BreakdownItemRow({ item }: { item: BreakdownItem }) {
  const content = (
    <>
      <span className="min-w-0">
        <span className="block text-sm truncate">{item.label}</span>
        {item.detail ? (
          <span className="block text-xs text-muted truncate">{item.detail}</span>
        ) : null}
      </span>
      <span
        className={clsx("money text-sm shrink-0", item.negative && "text-error")}
      >
        {item.value}
      </span>
    </>
  );
  const className =
    "w-full flex items-start justify-between gap-3 px-3 py-2 text-left hover:bg-surfaceVariant/50 transition-colors";
  if (item.onClick) {
    return (
      <button type="button" className={className} onClick={item.onClick}>
        {content}
      </button>
    );
  }
  if (item.to) {
    return (
      <Link to={item.to} className={className}>
        {content}
      </Link>
    );
  }
  return <div className={className}>{content}</div>;
}

function BreakdownRow({
  label,
  detail,
  value,
  negative = false,
  items,
  emptyText,
}: {
  label: string;
  detail?: string;
  value: string;
  negative?: boolean;
  items: BreakdownItem[];
  emptyText: string;
}) {
  const [open, setOpen] = useState(false);
  return (
    <li className="divider-line last:border-0">
      <button
        type="button"
        className="w-full flex items-start justify-between gap-3 py-2 text-left"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
      >
        <span className="min-w-0 flex items-start gap-2">
          <span
            className={clsx(
              "text-muted inline-block w-3 transition-transform",
              open && "rotate-90",
            )}
            aria-hidden="true"
          >
            ›
          </span>
          <span className="min-w-0">
            <span className="block text-body">{label}</span>
            {detail ? (
              <span className="block text-xs text-muted">{detail}</span>
            ) : null}
          </span>
        </span>
        <span
          className={clsx("money text-base shrink-0", negative && "text-error")}
        >
          {value}
        </span>
      </button>
      {open ? (
        <div className="pb-3 pl-5">
          {items.length === 0 ? (
            <p className="text-xs text-muted">{emptyText}</p>
          ) : (
            <ul className="rounded-lg border border-outline divide-y divide-outline">
              {items.map((item) => (
                <li key={item.key}>
                  <BreakdownItemRow item={item} />
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : null}
    </li>
  );
}

function syncedDetail(typeLabel: string, syncedAt: number | null | undefined): string {
  return syncedAt ? `${typeLabel} · synced ${formatBalanceAsOf(syncedAt)}` : typeLabel;
}

function byValueDesc<T>(rows: T[], value: (row: T) => number): T[] {
  return [...rows].sort((a, b) => value(b) - value(a));
}

function bankItems(accounts: BankAccount[], cash: boolean): BreakdownItem[] {
  const rows = accounts.filter((a) => isCashAccount(a) === cash);
  const withBalance = byValueDesc(
    rows.filter((a) => a.balance != null),
    (a) => a.balance ?? 0,
  );
  const withoutBalance = rows.filter((a) => a.balance == null);
  return [...withBalance, ...withoutBalance].map((a) => ({
    key: a.id,
    label: a.name,
    detail: syncedDetail(
      BANK_ACCOUNT_TYPE_LABELS[bankAccountType(a)],
      a.simplefinAccountKey ? a.balanceAsOf : null,
    ),
    value: a.balance != null ? formatUsd(a.balance) : "No balance",
    negative: (a.balance ?? 0) < 0,
    to: "/app/settings",
  }));
}

export function NetWorthRoute() {
  const bank = useBankAccountsData();
  const debt = useDebtData();
  const btc = useBitcoinData();
  const [refreshing, setRefreshing] = useState(false);
  const [editing, setEditing] = useState<BitcoinWallet | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);

  const usdPerBtc = btc.price?.usd ?? null;
  const summary = useMemo(
    () =>
      computeNetWorth({
        bankAccounts: bank.accounts,
        wallets: btc.wallets,
        usdPerBtc,
        creditAccounts: debt.accounts,
      }),
    [bank.accounts, btc.wallets, usdPerBtc, debt.accounts],
  );

  const items = useMemo(() => {
    const isPricedMortgage = (a: CreditAccount) =>
      a.type === "MORTGAGE" && (a.homePrice ?? 0) > 0;
    const home = debt.accounts.filter(isPricedMortgage).map((a) => {
      const owed = Math.max(0, a.currentBalance);
      const equity = (a.homePrice ?? 0) - owed;
      return {
        key: a.id,
        label: a.name,
        detail: `${formatUsd(a.homePrice ?? 0)} purchase price − ${formatUsd(owed)} owed`,
        value: formatUsd(equity),
        negative: equity < 0,
        to: `/app/debt/${a.id}`,
      };
    });
    const debts = byValueDesc(
      debt.accounts.filter((a) => !isPricedMortgage(a) && a.currentBalance > 0),
      (a) => a.currentBalance,
    ).map((a) => ({
      key: a.id,
      label: a.name,
      detail: syncedDetail(
        CREDIT_ACCOUNT_TYPE_LABELS[a.type],
        a.simplefinAccountKey ? a.simplefinBalanceAsOf : null,
      ),
      value: `−${formatUsd(a.currentBalance)}`,
      negative: true,
      to: `/app/debt/${a.id}`,
    }));
    const wallets = byValueDesc(btc.wallets, (w) => w.sats).map((w) => ({
      key: w.id,
      label: w.name,
      detail: walletDetail(w),
      value: usdPerBtc != null ? formatUsd(satsToUsd(w.sats, usdPerBtc)) : "—",
      onClick: () => {
        setEditing(w);
        setSheetOpen(true);
      },
    }));
    return {
      cash: bankItems(bank.accounts, true),
      invested: bankItems(bank.accounts, false),
      wallets,
      home,
      debts,
    };
  }, [bank.accounts, debt.accounts, btc.wallets, usdPerBtc]);

  const retirementSats = totalSats(btc.wallets.filter((w) => w.retirement));

  const loading = bank.loading || debt.loading || btc.loading;
  const error = bank.error ?? debt.error ?? btc.error;

  const onRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      await Promise.all([
        bank.reload(),
        debt.reload(),
        btc.reload(),
        btc.refreshPrice(),
      ]);
    } finally {
      setRefreshing(false);
    }
  };

  const openWallet = (wallet: BitcoinWallet | null) => {
    setEditing(wallet);
    setSheetOpen(true);
  };

  return (
    <div className="space-y-5">
      <PageHeader
        title="Net Worth"
        description="Everything you own minus everything you owe, with bitcoin at the live price."
        refreshing={refreshing}
        onRefresh={() => void onRefresh()}
      />

      {loading ? <p className="text-muted text-sm">Loading net worth…</p> : null}
      {error ? <ErrorBanner message={error} /> : null}

      <HeroCard>
        <p className="text-xs tracking-wider text-muted font-medium">Net worth</p>
        <p
          className={clsx(
            "money text-3xl mt-2",
            summary.total >= 0 ? "text-success" : "text-error",
          )}
        >
          {formatUsd(summary.total)}
        </p>
        <p className="text-xs text-muted mt-2">
          Assets {formatUsd(summary.assets)} · Debts {formatUsd(summary.debt)}
        </p>
        {summary.bitcoinUsd == null ? (
          <p className="text-xs text-error mt-1">
            BTC price unavailable — bitcoin isn&apos;t included yet.
          </p>
        ) : null}
      </HeroCard>

      <section className="card p-5">
        <h2 className="section-title">Breakdown</h2>
        <ul className="mt-2">
          <BreakdownRow
            label="Cash"
            detail="Checking & savings"
            value={formatUsd(summary.cash)}
            items={items.cash}
            emptyText="No checking or savings accounts yet. Add them in Settings."
          />
          <BreakdownRow
            label="Investments"
            detail="Retirement & brokerage"
            value={formatUsd(summary.invested)}
            items={items.invested}
            emptyText="No retirement or investment accounts yet. Add them in Settings."
          />
          <BreakdownRow
            label="Bitcoin"
            detail={
              retirementSats > 0
                ? `${formatBtc(summary.bitcoinSats)} BTC · ${formatBtc(retirementSats)} in retirement`
                : `${formatBtc(summary.bitcoinSats)} BTC`
            }
            value={summary.bitcoinUsd != null ? formatUsd(summary.bitcoinUsd) : "—"}
            items={items.wallets}
            emptyText="No wallets yet. Add one below."
          />
          {summary.homeValue > 0 ? (
            <BreakdownRow
              label="Home equity"
              detail={`${formatUsd(summary.homeValue)} purchase price − ${formatUsd(summary.mortgageDebt)} owed`}
              value={formatUsd(summary.homeEquity)}
              negative={summary.homeEquity < 0}
              items={items.home}
              emptyText="No mortgages with a home price."
            />
          ) : null}
          <BreakdownRow
            label="Debts"
            detail={
              summary.homeValue > 0 ? "Cards & loans" : "Cards, loans & mortgage"
            }
            value={`−${formatUsd(summary.otherDebt)}`}
            negative={summary.otherDebt > 0}
            items={items.debts}
            emptyText="No balances owed."
          />
        </ul>
        {summary.missingHomePrice ? (
          <p className="text-xs text-muted mt-2">
            Your mortgage has no home price, so it only counts as debt. Add the
            purchase price on the{" "}
            <Link to="/app/debt" className="underline">
              Debt
            </Link>{" "}
            page to count your equity.
          </p>
        ) : null}
      </section>

      <section className="card p-5 space-y-3">
        <div className="flex items-center justify-between gap-2">
          <h2 className="section-title">Bitcoin</h2>
          <button
            type="button"
            className="btn-ghost text-sm py-1.5"
            onClick={() => openWallet(null)}
            disabled={btc.saving}
          >
            + Add wallet
          </button>
        </div>
        <p className="text-xs text-muted">
          {btc.price
            ? `BTC ${formatUsd(btc.price.usd)} · ${btc.price.source} · ${formatBalanceAsOf(btc.price.fetched_at_ms)}${btc.price.stale ? " (couldn't refresh)" : ""}`
            : (btc.priceError ?? "Loading BTC price…")}
        </p>
        {btc.wallets.length === 0 && !btc.loading ? (
          <p className="text-sm text-muted">
            No wallets yet. Add one per wallet or exchange and update the amount
            whenever it changes.
          </p>
        ) : (
          <ul className="divide-y divide-outline rounded-lg border border-outline">
            {btc.wallets.map((wallet) => (
              <li key={wallet.id}>
                <button
                  type="button"
                  className="w-full flex items-start justify-between gap-3 px-4 py-3 text-left hover:bg-surfaceVariant/50 transition-colors"
                  onClick={() => openWallet(wallet)}
                >
                  <span className="min-w-0">
                    <span className="block font-medium truncate">{wallet.name}</span>
                    <span className="block text-xs text-muted">
                      {walletDetail(wallet)}
                      {wallet.updatedAt
                        ? ` · updated ${formatBalanceAsOf(wallet.updatedAt)}`
                        : ""}
                    </span>
                  </span>
                  <span className="money text-sm shrink-0">
                    {usdPerBtc != null
                      ? formatUsd(satsToUsd(wallet.sats, usdPerBtc))
                      : "—"}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <BitcoinWalletSheet
        open={sheetOpen}
        wallet={editing}
        usdPerBtc={usdPerBtc}
        saving={btc.saving}
        onClose={() => {
          setSheetOpen(false);
          setEditing(null);
        }}
        onSave={async ({ name, sats, notes, retirement }) => {
          const base =
            btc.wallets.find((w) => w.id && w.id === editing?.id) ?? editing;
          await btc.saveWallet({
            id: base?.id ?? "",
            createdAt: base?.createdAt ?? 0,
            updatedAt: 0,
            name,
            sats,
            notes,
            retirement,
          });
        }}
        onDelete={
          editing?.id
            ? async () => {
                await btc.deleteWallet(editing);
              }
            : undefined
        }
      />
    </div>
  );
}
