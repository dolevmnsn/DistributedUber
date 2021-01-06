package entities;

import lombok.Getter;
import lombok.Setter;

import java.awt.*;
import java.util.Objects;

// TODO: mapping City -> RouteGuideClient (not here...)

public enum City {
    A(1, "A", new Point(0,0), generated.City.A),
    B(2, "B", new Point(10,10), generated.City.B),
    C(3, "C", new Point(20,20), generated.City.C);

    @Getter @Setter
    private long id;

    @Getter @Setter
    private String name;

    @Getter @Setter
    private Point location;

    @Getter @Setter
    private generated.City protoType;

    City(int id, String name, Point location, generated.City protoType) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.protoType = protoType;
    }
}

