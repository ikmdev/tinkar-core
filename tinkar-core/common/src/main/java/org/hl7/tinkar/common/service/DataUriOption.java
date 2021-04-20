package org.hl7.tinkar.common.service;


import java.net.URI;

public record DataUriOption(String name, URI uri) {

    @Override
    public String toString() {
        return name;
    }
}
