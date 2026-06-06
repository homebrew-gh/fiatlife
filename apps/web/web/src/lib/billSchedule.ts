/**
 * Bill due-date and recurrence engine — ported from Android `Bill.kt`.
 */
import type { Bill, BillFrequency, BillRecurrenceUnit, CreditCardDetails } from "./bill";

const DAY_MS = 86_400_000;
const SEVEN_DAYS_MS = 7 * DAY_MS;

function creditMinimumDue(details: CreditCardDetails, balance: number): number {
  const type = details.minimumPaymentType ?? "PERCENT_OF_BALANCE";
  const value = details.minimumPaymentValue ?? 2;
  switch (type) {
    case "FIXED":
      return Math.max(0, value);
    case "FULL_BALANCE":
      return Math.max(0, balance);
    default:
      return Math.max(0, balance * (value / 100));
  }
}

export function effectiveAmountDue(bill: Bill): number {
  if (bill.creditCardDetails) {
    const balance = bill.creditCardDetails.currentBalance ?? 0;
    return creditMinimumDue(bill.creditCardDetails, balance);
  }
  return bill.amount;
}

export function isCreditCard(bill: Bill): boolean {
  return bill.creditCardDetails != null;
}

export function isCreditOrLoan(bill: Bill): boolean {
  return isCreditCard(bill) || Boolean(bill.linkedCreditAccountId);
}

function startOfDayMillis(millis: number): number {
  const d = new Date(millis);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function endOfDayMillis(dayStartMillis: number): number {
  return dayStartMillis + DAY_MS - 1;
}

function startOfMonthMillis(millis: number): number {
  const d = new Date(millis);
  d.setDate(1);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function endOfMonthMillis(millis: number): number {
  const d = new Date(millis);
  d.setDate(1);
  d.setMonth(d.getMonth() + 1);
  d.setHours(0, 0, 0, 0);
  return d.getTime() - 1;
}

function recurrenceConfig(bill: Bill): [BillRecurrenceUnit, number] {
  const explicitInterval = Math.max(bill.recurrenceIntervalCount ?? 1, 1);
  if (bill.recurrenceUnit) return [bill.recurrenceUnit, explicitInterval];
  const freq = bill.frequency ?? "MONTHLY";
  const map: Record<BillFrequency, [BillRecurrenceUnit, number]> = {
    WEEKLY: ["WEEK", 1],
    BIWEEKLY: ["WEEK", 2],
    MONTHLY: ["MONTH", 1],
    BIMONTHLY: ["MONTH", 2],
    QUARTERLY: ["MONTH", 3],
    SEMIANNUALLY: ["MONTH", 6],
    ANNUALLY: ["YEAR", 1],
  };
  return map[freq] ?? ["MONTH", 1];
}

function addRecurrence(
  baseMillis: number,
  unit: BillRecurrenceUnit,
  interval: number,
): number {
  const d = new Date(baseMillis);
  switch (unit) {
    case "DAY":
      d.setDate(d.getDate() + interval);
      break;
    case "WEEK":
      d.setDate(d.getDate() + interval * 7);
      break;
    case "MONTH":
      d.setMonth(d.getMonth() + interval);
      break;
    case "YEAR":
      d.setFullYear(d.getFullYear() + interval);
      break;
  }
  return d.getTime();
}

function applyDueDayForMonthBased(
  baseMillis: number,
  day: number,
  unit: BillRecurrenceUnit,
): number {
  if (unit !== "MONTH" && unit !== "YEAR") return baseMillis;
  const d = new Date(baseMillis);
  const maxDay = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  d.setDate(Math.min(Math.max(day, 1), maxDay));
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function dueDateForMonth(bill: Bill, anchorMillis: number): number {
  const d = new Date(anchorMillis);
  const maxDay = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  const day = Math.min(Math.max(bill.dueDay ?? 1, 1), maxDay);
  d.setDate(day);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function shiftCreditLoanCycle(bill: Bill, baseDue: number, months: number): number {
  const d = new Date(baseDue);
  d.setMonth(d.getMonth() + months);
  const maxDay = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  d.setDate(Math.min(Math.max(bill.dueDay ?? 1, 1), maxDay));
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

function oneTimeDueDateMillis(bill: Bill): number | null {
  if (bill.renewalDateMillis != null) return bill.renewalDateMillis;
  if (bill.initialPurchaseDateMillis != null) {
    return startOfDayMillis(bill.initialPurchaseDateMillis);
  }
  return null;
}

function firstDueDateFromAnchor(bill: Bill, anchorMillis: number): number {
  const [unit, interval] = recurrenceConfig(bill);
  const day = Math.min(Math.max(bill.dueDay ?? 1, 1), 31);
  let firstDue = applyDueDayForMonthBased(
    startOfDayMillis(anchorMillis),
    day,
    unit,
  );
  firstDue = addRecurrence(firstDue, unit, interval);
  return applyDueDayForMonthBased(firstDue, day, unit);
}

function nextDueFromFrequency(bill: Bill, now: number): number | null {
  const anchor = bill.initialPurchaseDateMillis;
  const day = Math.min(Math.max(bill.dueDay ?? 1, 1), 31);

  if (anchor != null) {
    const [unit, interval] = recurrenceConfig(bill);
    let next = applyDueDayForMonthBased(startOfDayMillis(anchor), day, unit);
    next = addRecurrence(next, unit, interval);
    next = applyDueDayForMonthBased(next, day, unit);
    while (next <= now) {
      next = addRecurrence(next, unit, interval);
      next = applyDueDayForMonthBased(next, day, unit);
    }
    return next;
  }

  const d = new Date(now);
  const freq = bill.frequency ?? "MONTHLY";

  if (freq === "MONTHLY") {
    const maxDay = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
    d.setDate(Math.min(day, maxDay));
    d.setHours(0, 0, 0, 0);
    if (d.getTime() <= now) {
      d.setMonth(d.getMonth() + 1);
      const max = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
      d.setDate(Math.min(day, max));
    }
    return d.getTime();
  }

  if (freq === "ANNUALLY") {
    d.setMonth(0);
    d.setDate(Math.min(day, 31));
    d.setHours(0, 0, 0, 0);
    if (d.getTime() <= now) d.setFullYear(d.getFullYear() + 1);
    return d.getTime();
  }

  const maxDay = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  d.setDate(Math.min(day, maxDay));
  d.setHours(0, 0, 0, 0);
  while (d.getTime() <= now) {
    switch (freq) {
      case "WEEKLY":
        d.setDate(d.getDate() + 7);
        break;
      case "BIWEEKLY":
        d.setDate(d.getDate() + 14);
        break;
      case "BIMONTHLY":
        d.setMonth(d.getMonth() + 2);
        break;
      case "QUARTERLY":
        d.setMonth(d.getMonth() + 3);
        break;
      case "SEMIANNUALLY":
        d.setMonth(d.getMonth() + 6);
        break;
      default:
        d.setMonth(d.getMonth() + 1);
    }
    const max = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
    d.setDate(Math.min(day, max));
  }
  return d.getTime();
}

function creditLoanUpcomingDue(bill: Bill, now: number): number | null {
  const currentMonthDue = dueDateForMonth(bill, now);
  return currentMonthDue > now
    ? currentMonthDue
    : shiftCreditLoanCycle(bill, currentMonthDue, 1);
}

function creditLoanDueForStatus(bill: Bill, now: number): number | null {
  const currentMonthDue = dueDateForMonth(bill, now);
  return now <= endOfDayMillis(currentMonthDue)
    ? currentMonthDue
    : shiftCreditLoanCycle(bill, currentMonthDue, 1);
}

function qualifyingPaymentMinimum(bill: Bill): number {
  if (isCreditOrLoan(bill)) return 0.01;
  const explicit = Math.max(bill.amount ?? 0, 0);
  if (explicit > 0) return explicit;
  return Math.max(effectiveAmountDue(bill), 0);
}

function hasQualifyingPaymentBetween(
  bill: Bill,
  startExclusive: number,
  endInclusive: number,
  minimumAmount: number,
): boolean {
  if (minimumAmount <= 0) return false;
  const history = bill.paymentHistory ?? [];
  return history.some(
    (payment) =>
      payment.date > startExclusive &&
      payment.date <= endOfDayMillis(endInclusive) &&
      payment.amount + 0.0001 >= minimumAmount,
  );
}

function hasQualifyingPaymentForCycle(bill: Bill, cycleDue: number): boolean {
  const previousCycleDue = shiftCreditLoanCycle(bill, cycleDue, -1);
  if (
    hasQualifyingPaymentBetween(
      bill,
      previousCycleDue,
      cycleDue,
      qualifyingPaymentMinimum(bill),
    )
  ) {
    return true;
  }
  const legacyPaidAt = bill.lastPaidDate;
  if (legacyPaidAt == null || !bill.isPaid) return false;
  return (
    legacyPaidAt > previousCycleDue &&
    legacyPaidAt <= endOfDayMillis(cycleDue)
  );
}

export function isPaidForCurrentCycle(bill: Bill, now = Date.now()): boolean {
  if (bill.isCancelled) return true;
  if (isCreditOrLoan(bill)) {
    const cycleDue = creditLoanDueForStatus(bill, now);
    if (cycleDue == null) return false;
    return hasQualifyingPaymentForCycle(bill, cycleDue);
  }
  if (bill.isRecurring === false) return Boolean(bill.isPaid);
  if (!bill.isPaid) return false;
  const paidAt = bill.lastPaidDate;
  if (paidAt == null) return true;
  return now < paidAt + SEVEN_DAYS_MS;
}

export function nextDueDateMillis(bill: Bill, now = Date.now()): number | null {
  if (bill.isRecurring === false) return oneTimeDueDateMillis(bill);
  if (isCreditOrLoan(bill)) return creditLoanUpcomingDue(bill, now);

  const explicitRenewal = bill.renewalDateMillis;
  if (explicitRenewal != null) {
    const [unit, interval] = recurrenceConfig(bill);
    const day = Math.min(Math.max(bill.dueDay ?? 1, 1), 31);
    let next = explicitRenewal;
    if (bill.isPaid) {
      const reference = bill.lastPaidDate ?? now;
      while (next <= reference) {
        next = addRecurrence(next, unit, interval);
        next = applyDueDayForMonthBased(next, day, unit);
      }
    }
    return next;
  }

  const next = nextDueFromFrequency(bill, now);
  if (next == null) return null;

  const [unit, interval] = recurrenceConfig(bill);
  const day = Math.min(Math.max(bill.dueDay ?? 1, 1), 31);
  const previous = addRecurrence(next, unit, -interval);
  const previousDue = applyDueDayForMonthBased(previous, day, unit);
  const currentCycleDue = previousDue <= now ? previousDue : next;

  const paidAndPastReset =
    bill.lastPaidDate != null && now >= bill.lastPaidDate + SEVEN_DAYS_MS;
  const paidForCycleInHistory = (bill.paymentHistory ?? []).some(
    (p) => p.date >= previousDue && p.amount > 0,
  );

  if (isPaidForCurrentCycle(bill, now)) return next;
  if (paidAndPastReset) return next;
  if (paidForCycleInHistory) return next;
  return currentCycleDue;
}

export function lastDueDateMillis(bill: Bill, now = Date.now()): number | null {
  if (isCreditOrLoan(bill)) {
    const currentDue = dueDateForMonth(bill, now);
    if (now > endOfDayMillis(currentDue) && !hasQualifyingPaymentForCycle(bill, currentDue)) {
      return currentDue;
    }
    const previous = shiftCreditLoanCycle(bill, currentDue, -1);
    return previous <= now ? previous : null;
  }
  if (bill.isRecurring === false) {
    const due = oneTimeDueDateMillis(bill);
    return due != null && due <= now ? due : null;
  }
  if (bill.renewalDateMillis != null) {
    if (!bill.isPaid) {
      const due = bill.renewalDateMillis;
      return due <= now ? due : null;
    }
    const next = nextDueDateMillis(bill, now);
    if (next == null) return null;
    const [unit, interval] = recurrenceConfig(bill);
    const previous = addRecurrence(next, unit, -interval);
    return previous <= now ? previous : null;
  }

  const next = nextDueDateMillis(bill, now);
  if (next == null) return null;
  const d = new Date(next);
  const freq = bill.frequency ?? "MONTHLY";
  switch (freq) {
    case "MONTHLY":
      d.setMonth(d.getMonth() - 1);
      break;
    case "WEEKLY":
      d.setDate(d.getDate() - 7);
      break;
    case "BIWEEKLY":
      d.setDate(d.getDate() - 14);
      break;
    case "BIMONTHLY":
      d.setMonth(d.getMonth() - 2);
      break;
    case "QUARTERLY":
      d.setMonth(d.getMonth() - 3);
      break;
    case "SEMIANNUALLY":
      d.setMonth(d.getMonth() - 6);
      break;
    case "ANNUALLY":
      d.setFullYear(d.getFullYear() - 1);
      break;
  }
  const lastDue = d.getTime();
  return lastDue <= now ? lastDue : null;
}

export function isPastDue(bill: Bill, now = Date.now()): boolean {
  if (bill.isCancelled) return false;
  if (isCreditOrLoan(bill)) {
    if ((bill.paymentHistory ?? []).length === 0 && bill.lastPaidDate == null) {
      return false;
    }
    const currentDue = dueDateForMonth(bill, now);
    if (now <= endOfDayMillis(currentDue)) return false;
    if (hasQualifyingPaymentForCycle(bill, currentDue)) return false;
    const paidAfterDue =
      (bill.paymentHistory ?? []).some(
        (p) =>
          p.amount > 0 &&
          p.date > endOfDayMillis(currentDue) &&
          p.date <= now,
      ) ||
      (bill.lastPaidDate != null &&
        bill.lastPaidDate > endOfDayMillis(currentDue) &&
        bill.lastPaidDate <= now);
    return !paidAfterDue;
  }
  if (isPaidForCurrentCycle(bill, now)) return false;
  if (bill.isRecurring === false) {
    const due = oneTimeDueDateMillis(bill);
    if (due == null) return false;
    return now > endOfDayMillis(due);
  }
  if (bill.initialPurchaseDateMillis != null) {
    const firstDue = firstDueDateFromAnchor(bill, bill.initialPurchaseDateMillis);
    if (now <= endOfDayMillis(firstDue)) return false;
  }
  const lastDue = lastDueDateMillis(bill, now);
  if (lastDue == null) return false;
  const startAnchor =
    bill.initialPurchaseDateMillis ??
    (bill.createdAt && bill.createdAt > 0 ? bill.createdAt : null);
  if (startAnchor != null && lastDue < startOfDayMillis(startAnchor)) return false;
  const paidForThisCycle =
    (bill.lastPaidDate != null && bill.lastPaidDate >= lastDue) ||
    (bill.paymentHistory ?? []).some((p) => p.date >= lastDue && p.amount > 0);
  if (paidForThisCycle) return false;
  return now > endOfDayMillis(lastDue);
}

export function dueOccurrencesInMonth(
  bill: Bill,
  monthAnchorMillis: number,
): number {
  if (bill.isCancelled) return 0;
  const monthStart = startOfMonthMillis(monthAnchorMillis);
  const monthEnd = endOfMonthMillis(monthAnchorMillis);

  if (bill.isRecurring === false) {
    const oneTimeDue = oneTimeDueDateMillis(bill);
    if (oneTimeDue == null) return 0;
    return oneTimeDue >= monthStart && oneTimeDue <= monthEnd ? 1 : 0;
  }

  const [unit, interval] = recurrenceConfig(bill);
  const day = Math.min(Math.max(bill.dueDay ?? 1, 1), 31);
  const seed =
    bill.renewalDateMillis ??
    (bill.initialPurchaseDateMillis != null
      ? firstDueDateFromAnchor(bill, bill.initialPurchaseDateMillis)
      : lastDueDateMillis(bill) ?? nextDueDateMillis(bill));
  if (seed == null) return 0;

  let occurrence = seed;
  while (occurrence > monthEnd) {
    occurrence = addRecurrence(occurrence, unit, -interval);
    occurrence = applyDueDayForMonthBased(occurrence, day, unit);
  }
  while (occurrence < monthStart) {
    occurrence = addRecurrence(occurrence, unit, interval);
    occurrence = applyDueDayForMonthBased(occurrence, day, unit);
  }

  let count = 0;
  let cursor = occurrence;
  while (cursor >= monthStart && cursor <= monthEnd) {
    count++;
    cursor = addRecurrence(cursor, unit, interval);
    cursor = applyDueDayForMonthBased(cursor, day, unit);
  }
  return count;
}

export function dueAmountInMonth(bill: Bill, monthAnchorMillis: number): number {
  return effectiveAmountDue(bill) * dueOccurrencesInMonth(bill, monthAnchorMillis);
}

export function dueAmountInYear(bill: Bill, yearAnchorMillis: number): number {
  const d = new Date(yearAnchorMillis);
  d.setMonth(0);
  d.setDate(1);
  d.setHours(0, 0, 0, 0);
  let total = 0;
  for (let i = 0; i < 12; i++) {
    total += dueAmountInMonth(bill, d.getTime());
    d.setMonth(d.getMonth() + 1);
  }
  return total;
}

export function skippedNextDueDateMillis(bill: Bill): number | null {
  if (bill.isRecurring === false) return null;
  const nextDue = nextDueDateMillis(bill);
  if (nextDue == null) return null;
  const [unit, interval] = recurrenceConfig(bill);
  const day = Math.min(Math.max(bill.dueDay ?? 1, 1), 31);
  let skipped = addRecurrence(nextDue, unit, interval);
  skipped = applyDueDayForMonthBased(skipped, day, unit);
  return skipped;
}

export function daysUntilDue(bill: Bill, now = Date.now()): number | null {
  const due = nextDueDateMillis(bill, now);
  if (due == null) return null;
  return Math.ceil((startOfDayMillis(due) - startOfDayMillis(now)) / DAY_MS);
}

export function formatDueCountdown(bill: Bill, now = Date.now()): string {
  if (isPaidForCurrentCycle(bill, now)) return "Paid";
  if (isPastDue(bill, now)) return "Past due";
  const days = daysUntilDue(bill, now);
  if (days == null) return "—";
  if (days === 0) return "Due today";
  if (days === 1) return "Due tomorrow";
  if (days < 0) return "Past due";
  return `Due in ${days} days`;
}
