package messages;

import utils.RunningMode;

import java.io.Serializable;

public class SwitchRequest implements Serializable {
    private String appId;
    private RunningMode requestMode;

    public SwitchRequest(RunningMode requestMode, String appId) {
        this.requestMode = requestMode;
        this.appId = appId;
    }

    public String getAppId() {
        return appId;
    }

    public RunningMode getMode() {
        return requestMode;
    }
}
