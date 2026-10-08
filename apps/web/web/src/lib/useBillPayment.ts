import { useCallback } from "react";
import type { BillWithSource } from "./bill";
import { useBillsData } from "./billsData";
import { useDebtData } from "./debtData";
import { balanceAfterPayment } from "./mortgage";

/**
 * Records a bill payment and, when the bill is linked to a Debt account,
 * reflects the payment in that account's balance. Mirrors Android's
 * `recordPaymentInternal`, which updates both the bill and the linked
 * credit/loan account.
 */
export function useRecordBillPayment() {
  const { recordPayment } = useBillsData();
  const { getAccountById, setBalanceFromPayment } = useDebtData();

  return useCallback(
    async (
      item: BillWithSource,
      amount: number,
      newBalance?: number,
      paymentDate?: number,
    ) => {
      await recordPayment(item, amount, newBalance, paymentDate);

      const accountId = item.bill.linkedCreditAccountId;
      if (!accountId) return;
      const account = getAccountById(accountId);
      if (!account) return;

      const target = newBalance ?? balanceAfterPayment(account, amount);
      await setBalanceFromPayment(accountId, target);
    },
    [recordPayment, getAccountById, setBalanceFromPayment],
  );
}
