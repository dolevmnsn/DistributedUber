package entities;

import lombok.Getter;
import lombok.Setter;

import java.awt.Point;


// TODO: mapping City -> Shard (not here...)

public enum City {
    A("A", new Point(0,0), generated.City.A),
    B("B", new Point(10,10), generated.City.B),
    C("C", new Point(20,20), generated.City.C);

    @Getter @Setter
    private String name;

    @Getter @Setter
    private Point location;

    @Getter @Setter
    private generated.City protoType;

    City(String name, Point location, generated.City protoType) {
        this.name = name;
        this.location = location;
        this.protoType = protoType;
    }
}

