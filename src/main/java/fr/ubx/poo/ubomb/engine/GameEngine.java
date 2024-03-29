/*
 * Copyright (c) 2020. Laurent Réveillère
 */

package fr.ubx.poo.ubomb.engine;

import fr.ubx.poo.ubomb.game.Direction;
import fr.ubx.poo.ubomb.game.Game;
import fr.ubx.poo.ubomb.game.Position;
import fr.ubx.poo.ubomb.go.GameObject;
import fr.ubx.poo.ubomb.go.character.Monster;
import fr.ubx.poo.ubomb.go.character.Player;
import fr.ubx.poo.ubomb.go.decor.Bomb;
import fr.ubx.poo.ubomb.go.decor.door.Door;
import fr.ubx.poo.ubomb.view.*;
import javafx.animation.AnimationTimer;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;


public final class GameEngine {

    private static AnimationTimer gameLoop;
    private final Game game;
    private final Player player;
    private final ArrayList<Monster> monsters;
    private final List<Sprite> sprites = new LinkedList<>();
    private final Set<Sprite> cleanUpSprites = new HashSet<>();
    private final Stage stage;
    private StatusBar statusBar;
    private Pane layer;
    private Input input;

    public GameEngine(Game game, final Stage stage) {
        this.stage = stage;
        this.game = game;
        this.player = game.player();
        this.monsters = game.monster();
        initialize();
        buildAndSetGameLoop();
    }

    private void initialize() {
        Group root = new Group();
        layer = new Pane();

        int height = game.grid().height();
        int width = game.grid().width();
        int sceneWidth = width * ImageResource.size;
        int sceneHeight = height * ImageResource.size;
        Scene scene = new Scene(root, sceneWidth, sceneHeight + StatusBar.height);
        scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

        stage.setScene(scene);
        stage.setResizable(true);
        stage.sizeToScene();
        stage.hide();
        stage.show();

        input = new Input(scene);
        root.getChildren().add(layer);
        statusBar = new StatusBar(root, sceneWidth, sceneHeight, game);

        // Create sprites
        for (var decor : game.grid().values()) {
            sprites.add(SpriteFactory.create(layer, decor));
            decor.setModified(true);
        }

        sprites.add(new SpritePlayer(layer, player));

        for (Monster monster : monsters){
            if(monster.getGridNumber() == game.getGridNumber()) {
                sprites.add(new SpriteMonster(layer, monster));
            }
        }
    }

    void buildAndSetGameLoop() {
        gameLoop = new AnimationTimer() {
            public void handle(long now) {
                // Check keyboard actions
                processInput(now);

                // Do actions
                update(now);
                createNewBombs(now);
                checkCollision(now);
                checkExplosions();

                // Graphic update
                cleanupSprites();
                render();
                statusBar.update(game);
            }
        };
    }

    private void checkExplosions() {
        for(int i = 0; i < player.getBombs().size(); i++) {
            Bomb b = player.getBombs().get(i);
            if (b.hasDetonated()) { // Overlapped detonation
                player.getBombs().remove(i);
                if(b.getGridNumber() == game.getGridNumber()) {
                    for (Position p : b.getExplosionBounds()) {
                        animateExplosion(b.getPosition(), p);
                    }
                }
                b.remove();
            }
            if(!b.getTimer().isRunning()) { // Manual detonation
                b.explode();
                // Duplicated to prevent frame skip
                player.getBombs().remove(i);
                if(b.getGridNumber() == game.getGridNumber()) {
                    for (Position p : b.getExplosionBounds()) {
                        animateExplosion(b.getPosition(), p);
                    }
                }
                b.remove();
            }
        }
        // Check explosions of bombs
    }

    private void animateExplosion(Position src, Position dst) {
        ImageView explosion = new ImageView(ImageResource.EXPLOSION.getImage());
        TranslateTransition tt = new TranslateTransition(Duration.millis(200), explosion);
        tt.setFromX(src.x() * Sprite.size);
        tt.setFromY(src.y() * Sprite.size);
        tt.setToX(dst.x() * Sprite.size);
        tt.setToY(dst.y() * Sprite.size);
        tt.setOnFinished(e -> {
            layer.getChildren().remove(explosion);
        });
        layer.getChildren().add(explosion);
        tt.play();
    }

    private void createNewBombs(long now) {
        if(player.isBombPlaced()) {
            sprites.add(new SpriteBomb(layer,(Bomb)game.grid().get(player.getPosition())));
            player.bombIsRendered();
        }
        // Create a new Bomb is needed
    }

    private void checkCollision(long now) {
        List<GameObject> collided = game.getGameObjects(player.getPosition());
        List<Monster> monsters = collided.stream().filter(c -> game.monster().contains(c)).map(c -> (Monster)c).toList();
        if(!monsters.isEmpty()) {
            monsters.forEach(m -> m.damage());
            game.player().damage();
        }
        // Check a collision between a monster and the player
    }

    private void processInput(long now) {
        if (input.isExit()) {
            gameLoop.stop();
            Platform.exit();
            System.exit(0);
        } else if (input.isMoveDown()) {
            player.requestMove(Direction.DOWN);
        } else if (input.isMoveLeft()) {
            player.requestMove(Direction.LEFT);
        } else if (input.isMoveRight()) {
            player.requestMove(Direction.RIGHT);
        } else if (input.isMoveUp()) {
            player.requestMove(Direction.UP);
        } else if (input.isKey()) {
            player.interactWithDoor();
        } else if (input.isBomb()) {
            player.placeABomb();
        }
        input.clear();
    }

    private void showMessage(String msg, Color color) {
        Text waitingForKey = new Text(msg);
        waitingForKey.setTextAlignment(TextAlignment.CENTER);
        waitingForKey.setFont(new Font(60));
        waitingForKey.setFill(color);
        StackPane root = new StackPane();
        root.getChildren().add(waitingForKey);
        Scene scene = new Scene(root, 400, 200, Color.WHITE);
        stage.setScene(scene);
        input = new Input(scene);
        stage.show();
        new AnimationTimer() {
            public void handle(long now) {
                processInput(now);
            }
        }.start();
    }


    private void update(long now) {
        if(game.gridNeedUpdate()) { // Level Change
            game.updateGridForNewLevel();

            // Create sprites
            for (var decor : game.grid().values()) {
                sprites.add(SpriteFactory.create(layer, decor));
                decor.setModified(true);
            }
            game.gridUpdated();
            sprites.add(new SpritePlayer(layer,game.player()));

            for (Monster monster : monsters){
                sprites.add(new SpriteMonster(layer, monster));
                monster.setModified(true);
            }

            Group root = new Group();
            int height = game.grid().height();
            int width = game.grid().width();
            int sceneWidth = width * ImageResource.size;
            int sceneHeight = height * ImageResource.size;
            Scene scene = new Scene(root, sceneWidth, sceneHeight + StatusBar.height);
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

            stage.setScene(scene);
            stage.sizeToScene();

            input = new Input(scene);
            root.getChildren().add(layer);
            statusBar = new StatusBar(root, sceneWidth, sceneHeight, game);
        }
        player.update(now);
        game.monster().forEach(m -> m.update(now));
        player.getBombs().forEach(b -> {
            long remainChanged = b.getTimer().remaining() / 1000;
            b.getTimer().update(now);
            if(remainChanged != b.getTimer().remaining() / 1000 && b.getGridNumber() == game.getGridNumber()) {
                b.setModified(true);
            }
        });
        if (player.haveWon()){
            gameLoop.stop();
            showMessage("You win !", Color.GREEN);
        }
        if (player.getLives() == 0) {
            gameLoop.stop();
            showMessage("Perdu!", Color.RED);
        }
    }

    public void cleanupSprites() {
        sprites.forEach(sprite -> {
            if (sprite.getGameObject().isDeleted()) {
                game.grid().remove(sprite.getPosition());
                cleanUpSprites.add(sprite);
            } else if (game.gridNeedUpdate()) {
                cleanUpSprites.addAll(sprites);
            }
        });
        cleanUpSprites.forEach(Sprite::remove);
        sprites.removeAll(cleanUpSprites);
        cleanUpSprites.clear();
    }

    private void render() {
        sprites.forEach(Sprite::render);
    }

    public void start() {
        gameLoop.start();
    }
}