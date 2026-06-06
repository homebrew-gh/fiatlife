//! At-rest encryption for the user's nsec.
//!
//! - KEK = Argon2id(passphrase, salt) into 32 bytes.
//! - Ciphertext = XChaCha20-Poly1305(KEK, nonce, plaintext).
//!
//! The sealed blob is the only secret material persisted to disk. The
//! plaintext nsec only exists in memory inside [`Zeroizing`] containers, and
//! is wiped on lock / idle timeout / drop.

use anyhow::anyhow;
use argon2::{Algorithm, Argon2, Params, Version};
use chacha20poly1305::{
    aead::{Aead, KeyInit},
    XChaCha20Poly1305, XNonce,
};
use rand::RngCore;
use zeroize::Zeroizing;

/// Argon2id parameters. Tuned for "modern desktop-class server" — 64 MiB,
/// 3 iterations, single lane. Worth revisiting before public release; for
/// now it's a sane default that matches the threat model in
/// `docs/WEB_APP.md`.
const ARGON2_M_KIB: u32 = 64 * 1024;
const ARGON2_T: u32 = 3;
const ARGON2_P: u32 = 1;

const SALT_LEN: usize = 16;
const NONCE_LEN: usize = 24;
const KEY_LEN: usize = 32;

#[derive(Debug, Clone)]
pub struct SealedBlob {
    pub salt: [u8; SALT_LEN],
    pub nonce: [u8; NONCE_LEN],
    pub ciphertext: Vec<u8>,
}

pub fn random_salt() -> [u8; SALT_LEN] {
    let mut s = [0u8; SALT_LEN];
    rand::rngs::OsRng.fill_bytes(&mut s);
    s
}

pub fn random_nonce() -> [u8; NONCE_LEN] {
    let mut n = [0u8; NONCE_LEN];
    rand::rngs::OsRng.fill_bytes(&mut n);
    n
}

/// Derive a 32-byte KEK from `passphrase` and `salt`. Returned as a
/// [`Zeroizing`] buffer so the cipher key doesn't linger after we drop it.
pub fn derive_kek(
    passphrase: &str,
    salt: &[u8; SALT_LEN],
) -> anyhow::Result<Zeroizing<[u8; KEY_LEN]>> {
    let params = Params::new(ARGON2_M_KIB, ARGON2_T, ARGON2_P, Some(KEY_LEN))
        .map_err(|e| anyhow!("argon2 params: {e}"))?;
    let argon = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);
    let mut out = Zeroizing::new([0u8; KEY_LEN]);
    argon
        .hash_password_into(passphrase.as_bytes(), salt, out.as_mut())
        .map_err(|e| anyhow!("argon2 derive: {e}"))?;
    Ok(out)
}

pub fn seal(passphrase: &str, plaintext: &[u8]) -> anyhow::Result<SealedBlob> {
    let salt = random_salt();
    let nonce = random_nonce();
    let kek = derive_kek(passphrase, &salt)?;
    let cipher = XChaCha20Poly1305::new(kek.as_slice().into());
    let ciphertext = cipher
        .encrypt(XNonce::from_slice(&nonce), plaintext)
        .map_err(|e| anyhow!("encrypt: {e}"))?;
    Ok(SealedBlob {
        salt,
        nonce,
        ciphertext,
    })
}

pub fn open(passphrase: &str, blob: &SealedBlob) -> anyhow::Result<Zeroizing<Vec<u8>>> {
    let kek = derive_kek(passphrase, &blob.salt)?;
    let cipher = XChaCha20Poly1305::new(kek.as_slice().into());
    let plaintext = cipher
        .decrypt(XNonce::from_slice(&blob.nonce), blob.ciphertext.as_ref())
        .map_err(|_| anyhow!("decrypt failed (likely wrong passphrase)"))?;
    Ok(Zeroizing::new(plaintext))
}
