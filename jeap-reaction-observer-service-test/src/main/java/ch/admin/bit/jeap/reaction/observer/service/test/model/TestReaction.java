package ch.admin.bit.jeap.reaction.observer.service.test.model;

public record TestReaction(TestObservation trigger, TestObservation action, String id) {

    public TestReaction(TestObservation trigger, TestObservation action) {
        this(trigger, action, createId(trigger, action));
    }

    private static String createId(TestObservation trigger, TestObservation action) {
        if (trigger == null) {
            return "#" + action.id();
        } else if (action == null) {
            return trigger.id();
        } else {
            return trigger.id() + "#" + action.id();
        }
    }
}
