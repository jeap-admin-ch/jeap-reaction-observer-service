package ch.admin.bit.jeap.reaction.observer.service.test.model;

import java.util.SortedMap;
import java.util.TreeMap;

public record TestObservation(String type, String fqn, SortedMap<String, String> props, String id) {

    public static TestObservation ofCommand(String messageType) {
        return new TestObservation("command", messageType, new TreeMap<>(), "command:" + messageType);
    }

    public static TestObservation ofEvent(String messageType) {
        return new TestObservation("event", messageType, new TreeMap<>(), "event:" + messageType);
    }
}
