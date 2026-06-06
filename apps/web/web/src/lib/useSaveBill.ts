import { useCallback } from "react";
import type { BillSheetInput } from "../components/BillSheet";
import type { Bill, BillWithSource } from "./bill";
import { useBillsData } from "./billsData";
import { useBillersData } from "./billersData";

function mapInputToBill(
  input: BillSheetInput,
  linkedBillerId: string | null,
): Omit<Bill, "id" | "createdAt" | "updatedAt"> {
  return {
    name: input.name,
    amount: input.amount,
    frequency: input.frequency,
    subcategory: input.subcategory,
    dueDay: input.dueDay,
    notes: input.notes,
    autoPay: input.autoPay,
    isRecurring: input.isRecurring,
    billerName: input.billerName,
    linkedBillerId,
    renewalDateMillis: input.renewalDateMillis,
    initialPurchaseDateMillis: input.initialPurchaseDateMillis,
    recurrenceUnit: input.recurrenceUnit,
    recurrenceIntervalCount: input.recurrenceIntervalCount,
    rateValidUntilMillis: input.rateValidUntilMillis,
    accountName: input.accountName,
    payFromBankAccountId: input.payFromBankAccountId,
    payFromCreditAccountId: input.payFromCreditAccountId,
    creditCardDetails: input.creditCardDetails,
  };
}

/**
 * Saves a bill (add or edit) and reconciles its biller link:
 * creates/renames the biller, links it to the saved bill, and unlinks the
 * previous biller when it changed or the name was cleared. Mirrors Android's
 * `reconcileNativeBillerForSave` + link/unlink steps.
 *
 * Returns the saved bill id.
 */
export function useSaveBill() {
  const { addBill, saveBill } = useBillsData();
  const { getOrCreateByName, linkToBill, unlinkFromBill } = useBillersData();

  return useCallback(
    async (
      input: BillSheetInput,
      editing: BillWithSource | null,
    ): Promise<string> => {
      const previousBillerId = editing?.bill.linkedBillerId ?? null;
      const requestedName = input.billerName.trim();

      let linkedBillerId: string | null = null;
      if (requestedName) {
        const biller = await getOrCreateByName(requestedName);
        linkedBillerId = biller.id;
      }

      const fields = mapInputToBill(input, linkedBillerId);

      let savedId: string;
      let source = editing?.source ?? "NATIVE";
      if (editing) {
        await saveBill(
          { ...editing.bill, ...fields, updatedAt: Date.now() } as Bill,
          editing.source,
          editing.dTag,
          editing.preservedTags,
        );
        savedId = editing.bill.id;
      } else {
        source = input.showInCypherLog ? "CYPHERLOG" : "NATIVE";
        const bill = await addBill(fields, source);
        savedId = bill.id;
      }

      if (
        previousBillerId &&
        previousBillerId !== linkedBillerId &&
        source !== "CYPHERLOG"
      ) {
        await unlinkFromBill(previousBillerId, savedId);
      }
      if (linkedBillerId && source !== "CYPHERLOG") {
        await linkToBill(linkedBillerId, savedId);
      }

      return savedId;
    },
    [addBill, saveBill, getOrCreateByName, linkToBill, unlinkFromBill],
  );
}
