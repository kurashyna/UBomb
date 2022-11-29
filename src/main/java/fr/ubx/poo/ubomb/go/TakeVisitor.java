package fr.ubx.poo.ubomb.go;

import fr.ubx.poo.ubomb.go.character.Monster;
import fr.ubx.poo.ubomb.go.decor.bonus.*;

// Double dispatch visitor pattern
public interface TakeVisitor {
    // Key
    default void take(Key key) {}

    default void take(Hearth hearth) {}

    default void take(Princess princess) {}

    default void take(Monster monster) {}
}
