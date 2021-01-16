package entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public abstract class BaseEntity {
    @Getter @Setter
    private UUID id;

    @Getter @Setter
    @JsonIgnore
    long revision;
}
