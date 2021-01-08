package protoSerializers;

public interface Serializer <E, G> {
    G serialize(E entity);

    E deserialize(G generated);
}
