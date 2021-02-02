package utils;

import messages.SwitchRequest;

/**
 * class that specifies the Redis channels
 */
public class MsgChannelFormat {

    public static final String ALL_AGENTS_INFO = "agents.*";
    public static final String SWITCH_TO_CENTRAL = "switch.centralized.*";

    // mode switch message and channel
    // ==========================================================================
    public static String getAppChannel(String appId) {
        return String.format("apps.%s", appId);
    }

    public static String getCoordChannel() {
        return "coordinator";
    }

    // ==========================================================================
}
