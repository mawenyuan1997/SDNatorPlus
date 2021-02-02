package apps.MultiAgentMft;


import apps.MultiAgentMft.AgentInfo.ResourceEvent;

public class MachineSimulator {
    private long startTime;
    private int duration;
    private ResourceEvent currentJob;

    public MachineSimulator() {
        currentJob = null;
    }

    public void set(ResourceEvent edge) {
        this.currentJob = edge;
        this.startTime = System.currentTimeMillis();
        System.out.println("machine set: " +edge);
    }

    public boolean read(ResourceEvent edge) {
        if (!edge.equals(currentJob)) return false;
        if (System.currentTimeMillis() - startTime >= currentJob.getEventTime()) {
            System.out.println(currentJob.toString() + " finishes at " + System.currentTimeMillis());
            this.currentJob = null;
            return true;
        }
        return false;
    }
}
