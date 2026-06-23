package com.sgs.capability.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Invoice row copied from InvoiceDto. */
public class InvoiceItem {
    public Long id;
    public Long subscriptionPaymentId;
    public BigDecimal amount = BigDecimal.ZERO;
    public String editionDisplayName;
    public String invoiceNo;
    public String invoiceDate;
    public String tenantLegalName;
    public List<String> tenantAddress = new ArrayList<>();
    public String tenantTaxNo;
    public String hostLegalName;
    public List<String> hostAddress = new ArrayList<>();
}
