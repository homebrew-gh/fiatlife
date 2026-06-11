//! FiatLife Nostr d-tag namespace (kind 30078), matching the Android app.

/// Whether a d-tag belongs to FiatLife (or CypherLog subscriptions it imports).
pub fn is_fiatlife_d_tag(d_tag: &str) -> bool {
    let d_tag = d_tag.trim();
    d_tag == "fiatlife/salary"
        || d_tag == "fiatlife/budget"
        || d_tag.starts_with("fiatlife/bill/")
        || d_tag.starts_with("fiatlife/goal/")
        || d_tag.starts_with("fiatlife/credit/")
        || d_tag.starts_with("fiatlife/biller/")
        || d_tag.starts_with("fiatlife/settings/")
        || d_tag.starts_with("fiatlife/cypherlog_deleted/")
        || d_tag.starts_with("subscription:")
}

/// Coarse category for dashboard grouping (used by future API extensions).
#[allow(dead_code)]
pub fn category_for_d_tag(d_tag: &str) -> &'static str {
    if d_tag == "fiatlife/salary" {
        return "salary";
    }
    if d_tag == "fiatlife/budget" {
        return "budget";
    }
    if d_tag.starts_with("fiatlife/bill/") {
        return "bills";
    }
    if d_tag.starts_with("fiatlife/goal/") {
        return "goals";
    }
    if d_tag.starts_with("fiatlife/credit/") {
        return "debt";
    }
    if d_tag.starts_with("fiatlife/biller/") {
        return "billers";
    }
    if d_tag.starts_with("fiatlife/settings/bank/") {
        return "banks";
    }
    if d_tag == "fiatlife/settings/app" {
        return "settings";
    }
    if d_tag.starts_with("subscription:") {
        return "subscriptions";
    }
    if d_tag.starts_with("fiatlife/cypherlog_deleted/") {
        return "tombstones";
    }
    "other"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recognizes_fiatlife_tags() {
        assert!(is_fiatlife_d_tag("fiatlife/salary"));
        assert!(is_fiatlife_d_tag("fiatlife/budget"));
        assert!(is_fiatlife_d_tag("fiatlife/bill/abc"));
        assert!(is_fiatlife_d_tag("subscription:xyz"));
        assert!(!is_fiatlife_d_tag("io.nomoxcel.utxo.wallets"));
    }
}
