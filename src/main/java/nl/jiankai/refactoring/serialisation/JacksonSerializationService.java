package nl.jiankai.refactoring.serialisation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class JacksonSerializationService implements SerializationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonSerializationService.class);
    private ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public byte[] serialize(Object object) {
        try {
            return objectMapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            LOGGER.warn("Could not serialize provided object", e);
            throw new RuntimeException(e);
        }
    }

    public <T> T deserialize(byte[] object, Class<T> deserializedClass) {
        try {
            return objectMapper.readValue(object, deserializedClass);
        } catch (IOException e) {
            LOGGER.warn("Could not deserialize object to {}", deserializedClass, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Map<String, Object> read(byte[] object) {
        try {
            return objectMapper.readValue(object, Map.class);
        } catch (IOException e) {
            LOGGER.warn("Could not read provided object to map");
            throw new RuntimeException(e);
        }
    }
}
