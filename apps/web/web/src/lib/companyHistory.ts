/**
 * Company history aggregation — mirrors Android `CompanyHistoryViewModel`.
 */
import type { Bill } from "./bill";
import { statementsOrderedByDate } from "./bill";
import { normalizeCompanyName, type Biller } from "./biller";

export type CompanyRef = { key: string; name: string };

export type CompanyHistoryRow = {
  key: string;
  name: string;
  isArchived: boolean;
  billCount: number;
  totalPaid: number;
  paymentCount: number;
  lastPaidDate: number | null;
  lastActivityAt: number;
};

export type CompanyPaymentRow = {
  id: string;
  companyKey: string;
  companyName: string;
  billId: string;
  billName: string;
  amount: number;
  paidDate: number;
  hasInvoiceOrStatement: boolean;
};

export type CompanyStatementRow = {
  id: string;
  companyKey: string;
  billId: string;
  billName: string;
  label: string;
  hash: string;
  addedAt: number;
};

export function companyForBill(
  bill: Bill,
  billersById: Map<string, Biller>,
): CompanyRef | null {
  const label = (bill.billerName || bill.accountName || "").trim();
  let key: string;
  if (bill.linkedBillerId) {
    key = `id:${bill.linkedBillerId}`;
  } else if (label) {
    key = `name:${normalizeCompanyName(label)}`;
  } else {
    return null;
  }
  let name: string;
  if (bill.linkedBillerId) {
    name =
      billersById.get(bill.linkedBillerId)?.name?.trim() || label || "Unknown company";
  } else {
    name = label || "Unknown company";
  }
  return { key, name };
}

export function buildCompanyHistory(
  allBills: Bill[],
  billers: Biller[],
  cypherBillIds: Set<string>,
): {
  companies: CompanyHistoryRow[];
  paymentsByCompanyKey: Map<string, CompanyPaymentRow[]>;
  statementsByCompanyKey: Map<string, CompanyStatementRow[]>;
  billsByCompanyKey: Map<string, Bill[]>;
  cypherBillIds: Set<string>;
} {
  const billersById = new Map(billers.map((b) => [b.id, b]));
  const archivedKeys = new Set(
    billers.filter((b) => b.isArchived).map((b) => `id:${b.id}`),
  );

  const billsByCompanyKey = new Map<string, Bill[]>();
  const paymentsByCompanyKey = new Map<string, CompanyPaymentRow[]>();
  const statementsByCompanyKey = new Map<string, CompanyStatementRow[]>();

  for (const bill of allBills) {
    const company = companyForBill(bill, billersById);
    if (!company) continue;

    const bills = billsByCompanyKey.get(company.key) ?? [];
    bills.push(bill);
    billsByCompanyKey.set(company.key, bills);

    const payments = bill.paymentHistory ?? [];
    for (let i = 0; i < payments.length; i++) {
      const p = payments[i];
      const row: CompanyPaymentRow = {
        id: `${bill.id}-${p.date}-${i}`,
        companyKey: company.key,
        companyName: company.name,
        billId: bill.id,
        billName: bill.name,
        amount: p.amount,
        paidDate: p.date,
        hasInvoiceOrStatement: statementsOrderedByDate(bill).length > 0,
      };
      const list = paymentsByCompanyKey.get(company.key) ?? [];
      list.push(row);
      paymentsByCompanyKey.set(company.key, list);
    }

    for (const stmt of statementsOrderedByDate(bill)) {
      const row: CompanyStatementRow = {
        id: `${bill.id}-${stmt.hash}`,
        companyKey: company.key,
        billId: bill.id,
        billName: bill.name,
        label: stmt.label || "Statement",
        hash: stmt.hash,
        addedAt: stmt.addedAt,
      };
      const list = statementsByCompanyKey.get(company.key) ?? [];
      list.push(row);
      statementsByCompanyKey.set(company.key, list);
    }
  }

  const companies: CompanyHistoryRow[] = [];
  for (const [key, bills] of billsByCompanyKey) {
    const name = companyForBill(bills[0], billersById)?.name ?? "Unknown";
    const allPayments = paymentsByCompanyKey.get(key) ?? [];
    const totalPaid = allPayments.reduce((s, p) => s + p.amount, 0);
    const lastPaidDate =
      allPayments.length > 0
        ? Math.max(...allPayments.map((p) => p.paidDate))
        : null;
    const lastActivityAt = Math.max(
      lastPaidDate ?? 0,
      ...bills.map((b) => b.updatedAt ?? 0),
    );
    companies.push({
      key,
      name,
      isArchived: archivedKeys.has(key),
      billCount: bills.length,
      totalPaid,
      paymentCount: allPayments.length,
      lastPaidDate,
      lastActivityAt,
    });
  }

  companies.sort((a, b) => b.lastActivityAt - a.lastActivityAt);

  for (const [, list] of paymentsByCompanyKey) {
    list.sort((a, b) => b.paidDate - a.paidDate);
  }
  for (const [, list] of statementsByCompanyKey) {
    list.sort((a, b) => b.addedAt - a.addedAt);
  }

  return {
    companies,
    paymentsByCompanyKey,
    statementsByCompanyKey,
    billsByCompanyKey,
    cypherBillIds,
  };
}
