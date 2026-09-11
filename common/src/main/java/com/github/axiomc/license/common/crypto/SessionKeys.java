package com.github.axiomc.license.common.crypto;

public record SessionKeys(byte[] request, byte[] response, byte[] ephemeralPublic) {
}
