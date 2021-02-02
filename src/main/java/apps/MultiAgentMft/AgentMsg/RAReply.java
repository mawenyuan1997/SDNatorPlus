package apps.MultiAgentMft.AgentMsg;

import java.io.Serializable;

public class RAReply implements Serializable {
    private boolean flag;
    public RAReply(boolean flag) {
        this.flag = flag;
    }

    public boolean getFlag() {
        return flag;
    }
}
