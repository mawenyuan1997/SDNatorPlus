package messages;

import java.io.Serializable;

public class Vote implements Serializable {
    private String appId;
    private boolean vote;
    private int time;

    public Vote(boolean vote, String appId) {
        this.vote = vote;
        this.appId = appId;
    }

    public Vote(boolean vote, int time, String appId) {
        this(vote, appId);
        this.time = time;
    }

    public boolean getVote() {
        return vote;
    }

    public String getAppId() {
        return appId;
    }
}
