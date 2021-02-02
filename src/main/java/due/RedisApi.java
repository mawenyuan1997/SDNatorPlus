package due;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;
import java.util.List;

public class RedisApi {
    private String agentId;
    private List<String> interests;
    private List<String> capabilities;
    private Jedis jedis;

    public RedisApi(String id) {
        this.agentId = id;
        this.jedis = new Jedis();
    }

    public void observe(JedisPubSub jedisPubSub, String channel) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    jedis.psubscribe(jedisPubSub, channel);
                } catch (Exception e) {
                    System.out.println("Redis Exception : " + e.getMessage());
                }
            }
        }).start();
    }

    public void write(String msg, String channel) {
        try {
            /* Creating Jedis object for connecting with redis server */
            jedis.publish(channel, msg);
        } catch(Exception ex) {
            System.out.println("Exception : " + ex.getMessage());
        }
    }
}
