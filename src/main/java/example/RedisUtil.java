package example;

import com.google.gson.Gson;
import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class RedisUtil {
    private static final String topic_name = "message:pack_status";

    private static final RedissonClient redisson = createRedisClient();

    private static RedissonClient createRedisClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://139.196.91.67:6379")
                .setPassword("Epms@@sy12")
                .setDatabase(0);
        return Redisson.create(config);
    }

    public static void send(Long id, String objectName, Boolean success, String environment, String errorMessage) {
        try {
            RTopic topic = redisson.getTopic(topic_name);
            MessageDto dto = new MessageDto();
            dto.setKey(id);
            dto.setSuccess(success);
            dto.setObjectName(objectName);
            dto.setEnvironment(environment);
            dto.setErrorMessage(errorMessage);
            topic.publish(new Gson().toJson(dto, MessageDto.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        send(1L, "1212", Boolean.TRUE, "qa", null);
    }
}
