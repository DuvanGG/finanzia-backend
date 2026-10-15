package com.finanzia.domain.common;

import com.finanzia.domain.DomainException;

import java.util.Objects;

/**
 * Dirección IP de origen. Se valida de forma laxa (IPv4/IPv6 no vacíos).
 */
public record IpAddress(String value) {

    public IpAddress {
        if (value == null || value.isBlank()) {
            throw new DomainException("IP address must not be blank");
        }
    }

    public static IpAddress of(String value) {
        return new IpAddress(value);
    }

    @Override
    public String toString() {
        return value;
    }
}