/*
 * Copyright (c) 2020. Laurent Réveillère
 */

package fr.ubx.poo.ubomb.view;

import fr.ubx.poo.ubomb.go.character.Player;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;

public class SpritePlayer extends SpriteCharacter {

    public SpritePlayer(Pane layer, Player player) {
        super(layer, player);
        updateImage();
    }

    @Override
    public void updateImage() {
        Player player = (Player) getGameObject();
        image = ImageResourceFactory.getPlayer(player.getDirection()).getImage();
        super.updateImage();
        setImage(image);
    }
}

