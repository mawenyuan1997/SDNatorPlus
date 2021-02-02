package apps;

import due.DUE;
import messages.SwitchRequest;
import messages.Vote;
import messages.VoteRequest;
import redis.clients.jedis.JedisPubSub;
import utils.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;

public class Coordinator extends Thread {
    private HashMap<String, Group> groupMapping;
    private HashMap<Group, GroupStatus> groupStatus;
    private DUE due;
    private HashMap<Group, Integer> currentVotes;

    public Coordinator(HashSet<Group> groups) {
        groupMapping = new HashMap<>();
        groupStatus = new HashMap<>();
        for(Group g : groups) {
            for(String appId : g.getMembers()) {
                groupMapping.put(appId, g);
            }
            groupStatus.put(g, GroupStatus.NORMAL);
        }
        currentVotes = new HashMap<>();
        due = new DUE("coordinator");
    }

    public void run() {
        JedisPubSub jedisPubSub = new CoordPubSubHandler();
        due.getSubscribeApi().observe(jedisPubSub, MsgChannelFormat.getCoordChannel());
        while(true);
    }

    private class CoordPubSubHandler extends JedisPubSub {
        @Override
        public void onPMessage(String pattern, String channel, String msg) {
            String[] splited = channel.split("\\.");


            if (splited[0].equals("coordinator")) {
                Object msgObj = SerializationUtility.fromString(msg);

                System.out.println("coordinator receives "+msgObj);

                if (msgObj instanceof SwitchRequest) {
                    String appId = ((SwitchRequest) msgObj).getAppId();
                    RunningMode mode = ((SwitchRequest) msgObj).getMode();
                    Group group = groupMapping.get(appId);

                    // ignore the msg if the group is in a switch progress
                    if (groupStatus.get(group) == GroupStatus.NORMAL) {
                        // send vote request to other apps in the group
                        for (String otherApp : group.getMembers()) {
                            if (!otherApp.equals(appId))
                                due.sendVoteRequest(mode, otherApp);
                        }
                        groupStatus.put(group, GroupStatus.IN_VOTE);
                        currentVotes.put(group, 1);
                    }
                } else if (msgObj instanceof Vote) {
                    Vote v = (Vote) msgObj;
                    Group g = groupMapping.get(v.getAppId());
                    if (v.getVote()) {
                        currentVotes.put(g, currentVotes.get(g)+1);
                    } else {
                        for (String appId : g.getMembers()) {
                            due.sendSwitchResult(false, appId);
                        }
                        groupStatus.put(g, GroupStatus.NORMAL);
                    }

                    if (currentVotes.get(g) == g.size()) {
                        for (String appId : g.getMembers()) {
                            due.sendSwitchResult(true, appId);
                        }
                    }
                }
            }
        }

        @Override
        public void onPSubscribe(String channel, int subscribedChannels) {
            System.out.println(" subscribes to channel : "+ channel);
        }
    }
}
