package com.maths.teacher.payment.web;

public class PurchaseStatusResponse {

    private final boolean purchased;

    public PurchaseStatusResponse(boolean purchased) {
        this.purchased = purchased;
    }

    public boolean isPurchased() { return purchased; }
}
