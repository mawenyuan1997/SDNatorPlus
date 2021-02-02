package messages;

import utils.RunningMode;

import java.io.Serializable;

public class VoteRequest implements Serializable {
    private RunningMode requestMode;

    public VoteRequest(RunningMode mode) {
        this.requestMode = mode;
    }

    public RunningMode getRequestMode() {
        return requestMode;
    }
}
