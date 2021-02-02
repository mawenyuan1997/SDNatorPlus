package due;

import messages.SwitchRequest;
import messages.SwitchResult;
import messages.Vote;
import messages.VoteRequest;
import utils.MsgChannelFormat;
import utils.RunningMode;
import utils.SerializationUtility;

public class DUE {
    private MongodbApi mongodbApi;
    private RedisApi subscribeApi, publishApi;

    public DUE(String appId) {
        mongodbApi = new MongodbApi(appId);
        subscribeApi = new RedisApi(appId);
        publishApi = new RedisApi(appId);
    }

    public void requestSwitch(RunningMode requestMode, String fromAppId) {
        System.out.println(fromAppId + " requestSwitch");

        publishApi.write(SerializationUtility.serialize(new SwitchRequest(requestMode, fromAppId)),
                       MsgChannelFormat.getCoordChannel());
    }

    public void sendVoteRequest(RunningMode requestMode, String requestAppId) {
        System.out.println(requestAppId + " sendVoteRequest");

        publishApi.write(SerializationUtility.serialize(new VoteRequest(requestMode)),
                       MsgChannelFormat.getAppChannel(requestAppId));
    }

    public void vote(boolean flag, String id) {
        System.out.println(id + " vote");

        publishApi.write(SerializationUtility.serialize(new Vote(flag, id)),
                       MsgChannelFormat.getCoordChannel());
    }

    public void voteWithAvailTime(boolean flag, int time, String id) {
        System.out.println(id + " vote");

        publishApi.write(SerializationUtility.serialize(new Vote(flag, time, id)),
                MsgChannelFormat.getCoordChannel());
    }

    public void sendSwitchResult(boolean isSuccess, String appId) {
        System.out.println(appId + " sendSwitchResult");

        publishApi.write(SerializationUtility.serialize(new SwitchResult(isSuccess)),
                       MsgChannelFormat.getAppChannel(appId));
    }

    public void moveToGroup() {

    }

    public MongodbApi getMongodbApi() {
        return mongodbApi;
    }

    public RedisApi getSubscribeApi() {
        return subscribeApi;
    }

    public RedisApi getPublishApi() {
        return publishApi;
    }
}
