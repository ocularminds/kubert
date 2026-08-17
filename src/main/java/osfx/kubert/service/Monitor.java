package osfx.kubert.service;

public interface Monitor {
    UpdateResult check() throws MonitoringException;
}
