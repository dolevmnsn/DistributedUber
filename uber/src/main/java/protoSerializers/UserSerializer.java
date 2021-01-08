package protoSerializers;

import entities.User;

public class UserSerializer  implements Serializer<entities.User, generated.User>{
    @Override
    public generated.User serialize(entities.User user) {
        return generated.User.newBuilder().setFirstName(user.getFirstName())
                .setLastName(user.getLastName())
                .setPhoneNumber(user.getPhoneNumber()).build();
    }

    @Override
    public entities.User deserialize(generated.User generatedUser) {
        User user = new User();
        user.setFirstName(generatedUser.getFirstName());
        user.setLastName(generatedUser.getLastName());
        user.setPhoneNumber(generatedUser.getPhoneNumber());

        return user;
    }
}
