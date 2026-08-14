import {
  effectiveAmountDue,
  newBillId,
  type Bill,
  type BillSource,
  type BillWithSource,
} from "./bill";
import {
  effectiveAmountDue as effectiveAccountAmountDue,
  effectiveMonthlyPayment,
  isAmortizingType,
  isRevolvingType,
  type CreditAccount,
} from "./creditAccount";

export type BillOps = {
  getBillById: (id: string) => BillWithSource | undefined;
  getBillsLinkedToAccount: (accountId: string) => BillWithSource[];
  getAllBills: () => Bill[];
  saveBill: (
    bill: Bill,
    source?: BillSource,
    dTag?: string,
  ) => Promise<void>;
  deleteBill: (item: BillWithSource) => Promise<void>;
};

function dayOfMonthFromMillis(ms: number): number {
  return new Date(ms).getDate();
}

function billSubcategoryForAccount(account: CreditAccount): string {
  if (account.type === "CREDIT_CARD") return "CREDIT_CARD";
  if (account.type === "STUDENT_LOAN") return "STUDENT_LOAN";
  return "OTHER_LOAN";
}

async function createAndLinkBill(
  account: CreditAccount,
  subcategory: string,
  ops: BillOps,
): Promise<CreditAccount> {
  const now = Date.now();
  const billId = newBillId();
  const bill: Bill = {
    id: billId,
    name: account.name,
    amount: effectiveMonthlyPayment(account),
    subcategory,
    frequency: "MONTHLY",
    isRecurring: true,
    dueDay: account.dueDay,
    isCancelled: false,
    isPaid: false,
    paymentHistory: [],
    linkedCreditAccountId: account.id,
    createdAt: now,
    updatedAt: now,
  };
  await ops.saveBill(bill, "NATIVE");
  return { ...account, linkedBillId: billId };
}

async function ensurePrimaryBillForAccount(
  account: CreditAccount,
  ops: BillOps,
): Promise<CreditAccount> {
  const subcategory = billSubcategoryForAccount(account);
  const allBills = ops.getAllBills();
  const updateLinkedBill = async (item: BillWithSource): Promise<void> => {
    await ops.saveBill(
      {
        ...item.bill,
        name: account.name,
        amount: account.currentBalance > 0 ? effectiveMonthlyPayment(account) : 0,
        dueDay: account.dueDay,
        subcategory,
        isRecurring: true,
        isCancelled: false,
        linkedCreditAccountId: account.id,
        updatedAt: Date.now(),
      },
      item.source,
      item.dTag,
    );
  };

  if (account.currentBalance > 0) {
    const existingByLinkedAccount = allBills.find(
      (b) => b.linkedCreditAccountId === account.id,
    );
    const existingLegacyByName = allBills.find(
      (b) =>
        !b.linkedCreditAccountId &&
        b.name.toLowerCase() === account.name.toLowerCase() &&
        b.subcategory === subcategory,
    );

    if (account.linkedBillId) {
      const existing = ops.getBillById(account.linkedBillId);
      if (existing) {
        await updateLinkedBill(existing);
        return account;
      }
    }

    const existingCandidate = existingByLinkedAccount ?? existingLegacyByName;
    if (existingCandidate) {
      const item = ops.getBillById(existingCandidate.id);
      if (item) await updateLinkedBill(item);
      if (account.linkedBillId !== existingCandidate.id) {
        return { ...account, linkedBillId: existingCandidate.id };
      }
      return account;
    }

    return createAndLinkBill(account, subcategory, ops);
  }

  // A paid-off account keeps its linked Bill and payment history. Reusing the
  // same reminder when the balance rises again avoids orphaned histories.
  const existingPaidOff =
    (account.linkedBillId && ops.getBillById(account.linkedBillId)) ||
    allBills
      .filter((bill) => bill.linkedCreditAccountId === account.id)
      .map((bill) => ops.getBillById(bill.id))
      .find((item): item is BillWithSource => item != null);
  if (existingPaidOff) {
    await updateLinkedBill(existingPaidOff);
    return account.linkedBillId === existingPaidOff.bill.id
      ? account
      : { ...account, linkedBillId: existingPaidOff.bill.id };
  }
  return account;
}

async function ensureAnnualFeeBill(
  account: CreditAccount,
  ops: BillOps,
): Promise<CreditAccount> {
  const hasAnnualFee =
    account.type === "CREDIT_CARD" &&
    account.annualFeeAmount > 0 &&
    account.annualFeeRenewalDateMillis != null;

  if (!hasAnnualFee) {
    if (account.annualFeeLinkedBillId) {
      const item = ops.getBillById(account.annualFeeLinkedBillId);
      if (item) await ops.deleteBill(item);
      return { ...account, annualFeeLinkedBillId: null };
    }
    return account;
  }

  const renewal = account.annualFeeRenewalDateMillis!;
  const dueDay = dayOfMonthFromMillis(renewal);
  const billName = `${account.name} Annual Fee`;

  const updateBill = (existing: Bill): Bill => ({
    ...existing,
    name: billName,
    amount: account.annualFeeAmount,
    frequency: account.annualFeeFrequency,
    dueDay,
    subcategory: "FINANCE",
    isRecurring: true,
    renewalDateMillis: renewal,
    initialPurchaseDateMillis: renewal,
    linkedCreditAccountId: null,
    accountName: account.name,
    billerName: account.name,
    updatedAt: Date.now(),
  });

  if (account.annualFeeLinkedBillId) {
    const linked = ops.getBillById(account.annualFeeLinkedBillId);
    if (linked) {
      await ops.saveBill(updateBill(linked.bill), linked.source, linked.dTag);
      return account;
    }
  }

  const existingByName = ops.getAllBills().find(
    (b) =>
      !b.linkedCreditAccountId &&
      b.name.toLowerCase() === billName.toLowerCase() &&
      (b.billerName ?? "").toLowerCase() === account.name.toLowerCase(),
  );

  if (existingByName) {
    const item = ops.getBillById(existingByName.id);
    if (item) {
      await ops.saveBill(updateBill(item.bill), item.source, item.dTag);
      return { ...account, annualFeeLinkedBillId: existingByName.id };
    }
  }

  const now = Date.now();
  const feeBillId = newBillId();
  const feeBill: Bill = {
    id: feeBillId,
    name: billName,
    amount: account.annualFeeAmount,
    subcategory: "FINANCE",
    frequency: account.annualFeeFrequency,
    dueDay,
    renewalDateMillis: renewal,
    initialPurchaseDateMillis: renewal,
    isRecurring: true,
    isCancelled: false,
    isPaid: false,
    paymentHistory: [],
    accountName: account.name,
    billerName: account.name,
    createdAt: now,
    updatedAt: now,
  };
  await ops.saveBill(feeBill, "NATIVE");
  return { ...account, annualFeeLinkedBillId: feeBillId };
}

export async function ensureBillsForAccount(
  account: CreditAccount,
  ops: BillOps,
): Promise<CreditAccount> {
  const primary = await ensurePrimaryBillForAccount(account, ops);
  return ensureAnnualFeeBill(primary, ops);
}

export async function inferCreditPaymentFromBalanceDrop(
  previous: CreditAccount | null,
  current: CreditAccount,
  ops: BillOps,
): Promise<void> {
  if (!previous) return;
  if (!isRevolvingType(current.type) && !isAmortizingType(current.type)) return;

  const delta = previous.currentBalance - current.currentBalance;
  if (delta <= 0) return;

  const linkedBillId = current.linkedBillId;
  if (!linkedBillId) return;

  const item = ops.getBillById(linkedBillId);
  if (!item) return;

  const now = Date.now();
  const bill = item.bill;
  const hasNearby = (bill.paymentHistory ?? []).some(
    (p) =>
      Math.abs(p.date - now) <= 90_000 &&
      Math.abs(p.amount - delta) <= 0.01,
  );
  if (hasNearby) return;

  const history = [...(bill.paymentHistory ?? [])];
  history.push({ date: now, amount: delta });
  await ops.saveBill(
    {
      ...bill,
      paymentHistory: history,
      isPaid: true,
      lastPaidDate: now,
      updatedAt: now,
    },
    item.source,
    item.dTag,
  );
}

export function findLinkedBill(
  account: CreditAccount,
  bills: BillWithSource[],
): BillWithSource | undefined {
  if (account.linkedBillId) {
    const byId = bills.find((b) => b.bill.id === account.linkedBillId);
    if (byId) return byId;
  }
  return bills.find((b) => b.bill.linkedCreditAccountId === account.id);
}

export function paymentAmountForBill(bill: Bill): number {
  return effectiveAmountDue(bill);
}

export function paymentAmountForLinkedAccount(account: CreditAccount): number {
  return effectiveAccountAmountDue(account);
}
