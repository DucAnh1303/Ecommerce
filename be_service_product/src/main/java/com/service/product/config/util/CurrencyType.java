package com.service.product.config.util;

public enum CurrencyType {
    VND,
    CNY,
    USD;


    public String getValue(CurrencyType currencyType) {
        return switch (currencyType) {
            case VND, CNY, USD -> this.name();
        };
    }


}
