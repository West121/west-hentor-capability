package com.sgs.capability.model;

import java.math.BigDecimal;

/** Subscription payment row copied from SubscriptionPaymentDto/ListDto. */
public class SubscriptionPaymentItem {
    public Long id;
    public String description;
    public int gateway;
    public String gatewayName;
    public BigDecimal amount = BigDecimal.ZERO;
    public int editionId;
    public int tenantId;
    public int dayCount;
    public int paymentPeriodType;
    public String paymentPeriodTypeName;
    public String paymentId;
    public String payerId;
    public String editionDisplayName;
    public String invoiceNo;
    public int status;
    public String statusName;
    public boolean isRecurring;
    public String externalPaymentId;
    public String successUrl;
    public String errorUrl;
    public int editionPaymentType;
    public String editionPaymentTypeName;
    public String creationTime;
}
