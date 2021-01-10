package entities;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public abstract class BaseEntity {
    @Getter @Setter
    private UUID id;
}
