package messages;

import java.io.Serializable;

public class SwitchResult implements Serializable {
    private boolean isSuccess;

    public SwitchResult(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public boolean isSwitchSuccess() {
        return isSuccess;
    }
}
