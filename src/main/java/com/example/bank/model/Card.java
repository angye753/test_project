package com.example.bank.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Card Value Object - Represents a card (debit/credit) associated with an account.
 * Card data is tokenized and masked for security.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Card implements Serializable {

    @Enumerated(EnumType.STRING)
    private CardType type;

    private String maskedNumber;

    private UUID accountId;

    private LocalDate expiresAt;

    @Enumerated(EnumType.STRING)
    private CardStatus status;

    /**
     * Validates card invariants.
     */
    public void validate() {
        if (type == null) {
            throw new IllegalArgumentException("Card type is required");
        }
        if (maskedNumber == null || maskedNumber.isBlank()) {
            throw new IllegalArgumentException("Masked number is required");
        }
        if (!maskedNumber.matches("^\\*{0,}?[0-9]{4}$")) {
            throw new IllegalArgumentException("Masked number must contain last 4 digits");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Expiration date is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("Card status is required");
        }
    }

    /**
     * Checks if card is active and not expired.
     */
    public boolean isActive() {
        return status == CardStatus.ACTIVE && !expiresAt.isBefore(LocalDate.now());
    }

    /**
     * Checks if card is expired.
     */
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDate.now());
    }

    public enum CardType {
        DEBIT, CREDIT
    }

    public enum CardStatus {
        ACTIVE, BLOCKED, EXPIRED
    }
}
