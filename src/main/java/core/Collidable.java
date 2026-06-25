package core;

import java.awt.geom.Rectangle2D;

public interface Collidable {
    Rectangle2D getBounds();
    boolean isAlive();
}