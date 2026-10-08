import { NetWorthSummaryCard } from "../components/networth/NetWorthSummaryCard";
import { DebtTab } from "./tabs/DebtTab";

export function AccountsRoute() {
  return (
    <DebtTab
      title="Accounts"
      description="Net worth, credit cards and loans, synced with Android via your Nostr relay."
      intro={
        <>
          <NetWorthSummaryCard />
          <h2 className="section-title pt-2">Credit &amp; loans</h2>
        </>
      }
    />
  );
}
