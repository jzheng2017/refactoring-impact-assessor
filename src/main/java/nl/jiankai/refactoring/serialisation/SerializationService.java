package nl.jiankai.refactoring.serialisation;

public interface SerializationService {
    byte[] serialize(Object object);

    <T> T deserialize(byte[] object, Class<T> deserializedClass);
}
