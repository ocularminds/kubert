package io.github.ocularminds.blazra.service;

public interface Monitor {
    UpdateResult check() throws MonitoringException;
}
