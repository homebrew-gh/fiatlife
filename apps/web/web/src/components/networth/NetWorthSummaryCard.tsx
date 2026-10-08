import { useMemo } from "react";
import { Link } from "react-router-dom";
import clsx from "clsx";
import { useBankAccountsData } from "../../lib/bankAccountsData";
import { useBitcoinData } from "../../lib/bitcoinData";
import { useDebtData } from "../../lib/debtData";
import { formatUsd } from "../../lib/format";
import { computeNetWorth } from "../../lib/netWorth";

function Line({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3 text-sm">
      <span className="text-muted">{label}</span>
      <span className="money">{value}</span>
    </div>
  );
}

/** Compact net worth for the Accounts page; links to the full breakdown. */
export function NetWorthSummaryCard() {
  const bank = useBankAccountsData();
  const debt = useDebtData();
  const btc = useBitcoinData();
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

  return (
    <Link
      to="/app/net-worth"
      className="card block p-5 hover:ring-1 hover:ring-outline transition-shadow"
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs tracking-wider text-muted font-medium">Net worth</p>
          <p
            className={clsx(
              "money text-2xl mt-1",
              summary.total >= 0 ? "text-success" : "text-error",
            )}
          >
            {formatUsd(summary.total)}
          </p>
        </div>
        <span className="text-muted shrink-0 text-sm">Breakdown ›</span>
      </div>
      <div className="mt-3 space-y-1">
        <Line label="Cash" value={formatUsd(summary.cash)} />
        <Line label="Investments" value={formatUsd(summary.invested)} />
        <Line
          label="Bitcoin"
          value={summary.bitcoinUsd != null ? formatUsd(summary.bitcoinUsd) : "—"}
        />
        {summary.homeValue > 0 ? (
          <Line label="Home equity" value={formatUsd(summary.homeEquity)} />
        ) : null}
        <Line label="Debts" value={`−${formatUsd(summary.otherDebt)}`} />
      </div>
      {summary.bitcoinUsd == null ? (
        <p className="text-xs text-error mt-2">
          BTC price unavailable — bitcoin isn&apos;t included yet.
        </p>
      ) : null}
    </Link>
  );
}
