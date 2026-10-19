package dev.stx.anticheat.core;

/** Bir kontrolün ürettiği kanıt. weight: şüphe puanı katkısı, detail: log için okunabilir açıklama. */
public record Result(double weight, String detail) {
    public static Result of(double weight, String detail) { return new Result(weight, detail); }
}
