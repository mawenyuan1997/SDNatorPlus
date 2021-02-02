package apps;


import jade.core.AID;
import jade.core.Agent;
import redis.clients.jedis.JedisPubSub;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Base64;

public class CentralController {

    public CentralController() {

    }


    private class CCPubSubHandler extends JedisPubSub {
        @Override
        public void onPMessage(String pattern, String channel, String msg) {
            System.out.println("From channel " + channel + " : " + msg);
            String[] splited = channel.split("\\.");
            if (splited[3].equals("capability")) {

            }
        }

        @Override
        public void onPSubscribe(String channel, int subscribedChannels) {
            System.out.println("Central controller subscribes to channel : "+ channel);
        }

    }



}
