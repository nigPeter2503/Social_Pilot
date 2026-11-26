package com.socialpilot.app.data.security;

/**
 * Authenticated encryption for one named record. The record name is bound as associated data so
 * a ciphertext copied to another record name fails authentication instead of being accepted.
 */
interface RecordCipher {
    String encrypt(String name, String plaintext) throws Exception;

    String decrypt(String name, String ciphertext) throws Exception;
}
