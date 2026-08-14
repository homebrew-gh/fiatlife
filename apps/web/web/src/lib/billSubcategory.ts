import type { BillGeneralCategory } from "./bill";

/** Specific subcategory aligned with Android `BillSubcategory`. */
export type BillSubcategory =
  | "MORTGAGE_RENT"
  | "HOME_INSURANCE"
  | "INTERNET"
  | "PHONE"
  | "HOA"
  | "PROPERTY_TAX"
  | "ELECTRIC"
  | "GAS_HEATING"
  | "WATER_SEWER"
  | "TRASH"
  | "CAR_PAYMENT"
  | "CAR_INSURANCE"
  | "GAS_FUEL"
  | "CREDIT_CARD"
  | "STUDENT_LOAN"
  | "OTHER_LOAN"
  | "EDUCATION"
  | "FINANCE"
  | "FOOD"
  | "GAMING"
  | "HEALTH_WELLNESS"
  | "SUB_HOME"
  | "MUSIC"
  | "NEWS_MEDIA"
  | "PET_CARE"
  | "TRAVEL"
  | "SHOPPING"
  | "SOFTWARE"
  | "STREAMING"
  | "FIREARM"
  | "VEHICLE"
  | "OTHER_SUBSCRIPTION"
  | "GYM_FITNESS"
  | "MEDICAL"
  | "GROCERIES"
  | "CHILDCARE"
  | "PET"
  | "OTHER";

export const SUBCATEGORY_GENERAL: Record<BillSubcategory, BillGeneralCategory> = {
  MORTGAGE_RENT: "HOME",
  HOME_INSURANCE: "HOME",
  INTERNET: "HOME",
  PHONE: "HOME",
  HOA: "HOME",
  PROPERTY_TAX: "HOME",
  ELECTRIC: "UTILITIES",
  GAS_HEATING: "UTILITIES",
  WATER_SEWER: "UTILITIES",
  TRASH: "UTILITIES",
  CAR_PAYMENT: "AUTO",
  CAR_INSURANCE: "AUTO",
  GAS_FUEL: "AUTO",
  CREDIT_CARD: "CREDIT_LOANS",
  STUDENT_LOAN: "CREDIT_LOANS",
  OTHER_LOAN: "CREDIT_LOANS",
  EDUCATION: "SUBSCRIPTION",
  FINANCE: "SUBSCRIPTION",
  FOOD: "SUBSCRIPTION",
  GAMING: "SUBSCRIPTION",
  HEALTH_WELLNESS: "SUBSCRIPTION",
  SUB_HOME: "SUBSCRIPTION",
  MUSIC: "SUBSCRIPTION",
  NEWS_MEDIA: "SUBSCRIPTION",
  PET_CARE: "SUBSCRIPTION",
  TRAVEL: "SUBSCRIPTION",
  SHOPPING: "SUBSCRIPTION",
  SOFTWARE: "SUBSCRIPTION",
  STREAMING: "SUBSCRIPTION",
  FIREARM: "SUBSCRIPTION",
  VEHICLE: "SUBSCRIPTION",
  OTHER_SUBSCRIPTION: "SUBSCRIPTION",
  GYM_FITNESS: "HEALTH",
  MEDICAL: "HEALTH",
  GROCERIES: "PERSONAL",
  CHILDCARE: "PERSONAL",
  PET: "PERSONAL",
  OTHER: "OTHER",
};

export const SUBCATEGORY_LABELS: Record<BillSubcategory, string> = {
  MORTGAGE_RENT: "Mortgage/Rent",
  HOME_INSURANCE: "Home Insurance",
  INTERNET: "Internet",
  PHONE: "Phone",
  HOA: "HOA",
  PROPERTY_TAX: "Property Tax",
  ELECTRIC: "Electric",
  GAS_HEATING: "Gas/Heating",
  WATER_SEWER: "Water/Sewer",
  TRASH: "Trash",
  CAR_PAYMENT: "Car Payment",
  CAR_INSURANCE: "Car Insurance",
  GAS_FUEL: "Gas/Fuel",
  CREDIT_CARD: "Credit Card",
  STUDENT_LOAN: "Student Loan",
  OTHER_LOAN: "Other Loan",
  EDUCATION: "Education",
  FINANCE: "Finance",
  FOOD: "Food",
  GAMING: "Gaming",
  HEALTH_WELLNESS: "Health/Wellness",
  SUB_HOME: "Home",
  MUSIC: "Music",
  NEWS_MEDIA: "News/Media",
  PET_CARE: "Pet Care",
  TRAVEL: "Travel",
  SHOPPING: "Shopping",
  SOFTWARE: "Software",
  STREAMING: "Streaming",
  FIREARM: "Firearm",
  VEHICLE: "Vehicle",
  OTHER_SUBSCRIPTION: "Other",
  GYM_FITNESS: "Gym/Fitness",
  MEDICAL: "Medical",
  GROCERIES: "Groceries",
  CHILDCARE: "Childcare",
  PET: "Pet",
  OTHER: "Other",
};

const LEGACY_CATEGORY_SUB: Record<string, BillSubcategory> = {
  MORTGAGE_RENT: "MORTGAGE_RENT",
  ELECTRIC: "ELECTRIC",
  GAS_HEATING: "GAS_HEATING",
  WATER_SEWER: "WATER_SEWER",
  TRASH: "TRASH",
  INTERNET: "INTERNET",
  PHONE: "PHONE",
  CABLE_STREAMING: "STREAMING",
  CAR_PAYMENT: "CAR_PAYMENT",
  CAR_INSURANCE: "CAR_INSURANCE",
  HOME_INSURANCE: "HOME_INSURANCE",
  PROPERTY_TAX: "PROPERTY_TAX",
  HOA: "HOA",
  GROCERIES: "GROCERIES",
  GAS_FUEL: "GAS_FUEL",
  CHILDCARE: "CHILDCARE",
  STUDENT_LOAN: "STUDENT_LOAN",
  CREDIT_CARD: "CREDIT_CARD",
  SUBSCRIPTION: "OTHER_SUBSCRIPTION",
  GYM_FITNESS: "GYM_FITNESS",
  PET: "PET",
  OTHER: "OTHER",
};

export function fromLegacyCategory(category: string): BillSubcategory {
  return LEGACY_CATEGORY_SUB[category] ?? "OTHER";
}

export function subcategoriesForGeneral(
  general: BillGeneralCategory,
): BillSubcategory[] {
  return (Object.keys(SUBCATEGORY_GENERAL) as BillSubcategory[]).filter(
    (sub) => SUBCATEGORY_GENERAL[sub] === general,
  );
}

export function subcategoryLabel(
  sub: string,
  options?: { treatMortgageRentAsMortgage?: boolean },
): string {
  if (sub === "MORTGAGE_RENT" && options?.treatMortgageRentAsMortgage) {
    return "Mortgage";
  }
  return SUBCATEGORY_LABELS[sub as BillSubcategory] ?? sub;
}

export function generalForSubcategory(sub: string): BillGeneralCategory {
  return SUBCATEGORY_GENERAL[sub as BillSubcategory] ?? "OTHER";
}
