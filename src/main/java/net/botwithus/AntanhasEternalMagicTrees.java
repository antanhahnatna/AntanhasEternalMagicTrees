package net.botwithus;

import net.botwithus.api.game.hud.inventories.Bank;
import net.botwithus.internal.scripts.ScriptDefinition;
import net.botwithus.rs3.events.impl.InventoryUpdateEvent;
import net.botwithus.rs3.game.Area;
import net.botwithus.rs3.game.Client;
import net.botwithus.rs3.game.Coordinate;
import net.botwithus.rs3.game.hud.interfaces.Component;
import net.botwithus.rs3.game.inventories.Backpack;
import net.botwithus.rs3.game.inventories.Equipment;
import net.botwithus.rs3.game.minimenu.actions.GroundItemAction;
import net.botwithus.rs3.game.movement.Movement;
import net.botwithus.rs3.game.movement.NavPath;
import net.botwithus.rs3.game.queries.builders.animations.SpotAnimationQuery;
import net.botwithus.rs3.game.queries.builders.characters.NpcQuery;
import net.botwithus.rs3.game.queries.builders.components.ComponentQuery;
import net.botwithus.rs3.game.queries.builders.items.GroundItemQuery;
import net.botwithus.rs3.game.queries.builders.items.InventoryItemQuery;
import net.botwithus.rs3.game.queries.builders.objects.SceneObjectQuery;
import net.botwithus.rs3.game.scene.entities.animation.SpotAnimation;
import net.botwithus.rs3.game.scene.entities.characters.player.LocalPlayer;
import net.botwithus.rs3.game.scene.entities.item.GroundItem;
import net.botwithus.rs3.game.scene.entities.object.SceneObject;
import net.botwithus.rs3.game.skills.Skills;
import net.botwithus.rs3.game.vars.VarManager;
import net.botwithus.rs3.script.Execution;
import net.botwithus.rs3.script.LoopingScript;
import net.botwithus.rs3.script.config.ScriptConfig;

import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class AntanhasEternalMagicTrees extends LoopingScript {

    private BotState botState = BotState.STOPPED;
    private Random random = new Random();

    private Coordinate eternalMagicTrees = new Coordinate(2330, 3588, 0);
    private Boolean pickedUpBirdsNest = false;
    LinkedList<String> logNames = new LinkedList<>();
    LinkedList<Integer> logAmounts = new LinkedList<>();
    int startingExperience = Skills.WOODCUTTING.getSkill().getExperience();
    long startingTime = System.currentTimeMillis();
    long timeScriptWasLastActive = System.currentTimeMillis();

    enum BotState {
        //define your own states here
        STOPPED,
        SETUP,
        MOVINGTOTREE,
        WOODCUTTING,
        MOVINGTOBANK,
        BANKING,
        //...
    }

    public AntanhasEternalMagicTrees(String s, ScriptConfig scriptConfig, ScriptDefinition scriptDefinition) {
        super(s, scriptConfig, scriptDefinition);
        this.sgc = new AntanhasEternalMagicTreesGraphicsContext(getConsole(), this);
    }

    @Override
    public boolean initialize() {
        super.initialize();

        //this subscription is just to check whether bird's nests are picked up
        subscribe(InventoryUpdateEvent.class, inventoryUpdateEvent -> {
            if(inventoryUpdateEvent.getInventoryId() == 93) {
                if(inventoryUpdateEvent.getNewItem().getName().equals("Bird's nest") && inventoryUpdateEvent.getOldItem().getName() == null) {
                    pickedUpBirdsNest = true;
                }
            }
        });

        //this subscription updates the item log
        subscribe(InventoryUpdateEvent.class, inventoryUpdateEvent -> {
            //more events available at https://botwithus.net/javadoc/net.botwithus.rs3/net/botwithus/rs3/events/impl/package-summary.html
            //println("Chatbox message received: %s", chatMessageEvent.getMessage());
            if(inventoryUpdateEvent.getNewItem().getName() != null && (inventoryUpdateEvent.getInventoryId() == 93 || (inventoryUpdateEvent.getInventoryId() == 937 && !inventoryUpdateEvent.getNewItem().getName().equals("Eternal magic logs")) ) && botState != BotState.STOPPED) {
                //println("New item: " + inventoryUpdateEvent.getNewItem().getName());
                //println("Old item: " + inventoryUpdateEvent.getOldItem().getName());
                int increment;
                if (inventoryUpdateEvent.getOldItem().getName() == null) {
                    increment = inventoryUpdateEvent.getNewItem().getStackSize();
                } else if (inventoryUpdateEvent.getNewItem().getName().equals(inventoryUpdateEvent.getOldItem().getName()) && inventoryUpdateEvent.getNewItem().getStackSize() > inventoryUpdateEvent.getOldItem().getStackSize()) {
                    increment = inventoryUpdateEvent.getNewItem().getStackSize() - inventoryUpdateEvent.getOldItem().getStackSize();
                } else {
                    increment = 0;
                }
                if(increment > 0) {
                    if (logNames.contains(inventoryUpdateEvent.getNewItem().getName())) {
                        logAmounts.set(logNames.indexOf(inventoryUpdateEvent.getNewItem().getName()), logAmounts.get(logNames.indexOf(inventoryUpdateEvent.getNewItem().getName())) + increment);
                    } else {
                        logNames.push(inventoryUpdateEvent.getNewItem().getName());
                        logAmounts.push(increment);
                    }
                }
            }
        });

        return true;
    }

    @Override
    public void onLoop() {
        println("onLoop()");

        //Loops every 100ms by default, to change:
        this.loopDelay = 500;
        LocalPlayer player = Client.getLocalPlayer();
        if (player == null || Client.getGameState() != Client.GameState.LOGGED_IN) {
            //wait some time so we dont immediately start on login.
            Execution.delay(random.nextLong(3000,7000));
            return;
        }

        switch (botState) {
            case STOPPED -> {
                //do nothing
                println("We're idle!");
                Execution.delay(random.nextLong(1000,3000));
            }
            //this only gets called when the start button is clicked, it can't clog onLoop() up
            case SETUP -> {
                if (Backpack.isFull()) {
                    println("Going to banking state");
                    botState = BotState.MOVINGTOBANK;
                } else {
                    println("Going to eternal magic trees");
                    botState = BotState.MOVINGTOTREE;
                }
            }
            //this clogs up the onLoop
            case MOVINGTOTREE -> {
                Execution.delay(handleMoving(eternalMagicTrees, player));
                if(botState != BotState.STOPPED) botState = BotState.WOODCUTTING;
            }
            //notice that, if the script is running fine, this doesn't clog onLoop() up besides the brief pauses to emulate human-like behavior
            //this is the only case where the breakpoint (botState = BotState.MOVINGTOBANK) isn't here but in the function itself, it's part of why I wrote handleWoodcutting() to be non-clogging
            case WOODCUTTING -> {
                handleWoodcutting(player);
            }
            //this clogs up the onLoop
            case MOVINGTOBANK -> {
                //first, try teleporting to War's Retreat
                //if(Achievement.byId(1747).isCompleted()) {
                //the function above bafflingly crashes the entire game for id values >= 1620, so I have to use another way to check if the player has War's Retreat teleport unlocked
                if(VarManager.getVarbitValue(45680) == 1) {
                    Component warsRetreat = ComponentQuery.newQuery(1461).componentIndex(1).subComponentIndex(205).results().first();
                    if (warsRetreat != null) {
                        warsRetreat.interact(warsRetreat.getOptions().get(0));
                        Execution.delayUntil(20000, () -> {
                            //return SceneObjectQuery.newQuery().name("Bank chest").option("Use").results().nearest() != null;
                            return player.getCoordinate().equals(new Coordinate(3294, 10127, 0)) && SceneObjectQuery.newQuery().name("Bank chest").option("Use").results().nearest() != null;
                        });
                    }
                //if War's Retreat teleport isn't unlocked, try using Ring of Fortune or Luck of the Dwarves to teleport to GE in order to bank
                //the next two lines are clumsy put together, but such is the limited API we have
                } else if(InventoryItemQuery.newQuery(94).option("Grand Exchange").results().first() != null) {
                    Equipment.interact(Equipment.Slot.RING, "Grand Exchange");
                    Execution.delayUntil(20000, () -> {
                        //return NpcQuery.newQuery().name("Banker").option("Bank").results().nearest() != null;
                        return new Area.Rectangular(new Coordinate(3163, 3467, 0), new Coordinate(3161, 3461, 0)).contains(player) && NpcQuery.newQuery().name("Banker").option("Bank").results().nearest() != null;
                    });
                }
                if(botState != BotState.STOPPED) botState = BotState.BANKING;
            }
            //this clogs up the onLoop
            case BANKING -> {
                Execution.delay(handleBanking());
                if(botState != BotState.STOPPED) botState = BotState.MOVINGTOTREE;
            }
        }

    }

    private long handleWoodcutting(LocalPlayer player) {

        //teleport an urn as soon as possible so we don't have to worry about the interface that pops up if we try to teleport more than 1
        if(Backpack.contains("Decorated woodcutting urn (full)")) {
            Backpack.interact("Decorated woodcutting urn (full)", "Teleport urn");
        }
        //the break condition while woodcutting is if we get our backpack full. notice that this condition works here because handleWoodcutting() doesn't clog up onLoop, so this function will be called up often
        if (Backpack.isFull()) {
            Execution.delay(random.nextLong(500,1500));
            //First try filling the wood box
            if (Backpack.contains("Eternal magic wood box")) {
                Backpack.interact("Eternal magic wood box", "Fill");
                Execution.delay(random.nextLong(1500,3000));
            }
            //if inventory is still full after filling the wood box, go to bank
            if (Backpack.isFull()) {
                if (botState != BotState.STOPPED) botState = BotState.MOVINGTOBANK;
                return random.nextLong(1500, 3000);
            }
        }
        //check grounditems for a bird's nest
        GroundItem birdsNest = GroundItemQuery.newQuery().name(Pattern.compile("ird's nest")).results().nearest();
        if(birdsNest != null) {
            birdsNest.interact(GroundItemAction.GROUND_ITEM3);
            //we have to rely on pickedUpBirdsNest set by a subscription in order to know when we picked up the bird's nest
            Execution.delayUntil(20000, () -> {
                return pickedUpBirdsNest;
            });
            pickedUpBirdsNest = false;
        }
        //if there's a lumberjack's intuition spot, move there
        SpotAnimation lumberjacksIntuition = SpotAnimationQuery.newQuery().ids(8447).results().nearest();
        if(lumberjacksIntuition != null && !lumberjacksIntuition.getCoordinate().equals(player.getCoordinate())) {
            Execution.delay(random.nextLong(1000,2000));
            Movement.walkTo(lumberjacksIntuition.getCoordinate().getX(), lumberjacksIntuition.getCoordinate().getY(), false);
            Execution.delayUntil(20000, () -> {
                return lumberjacksIntuition.getCoordinate().equals(player.getCoordinate());
            });
            //there are 3 spots around the bigger tree where a smaller trees registers as closer than the bigger tree, so if the lumberjack's intuition appears at one of these bigger tree spots and the bot moves there, the bot ends up changing to a different tree; so we make this little ad hoc exception to make sure that the bot stays on the big tree
            if(lumberjacksIntuition.getCoordinate().equals(new Coordinate(2330, 3591, 0)) || lumberjacksIntuition.getCoordinate().equals(new Coordinate(2331, 3591, 0)) || lumberjacksIntuition.getCoordinate().equals(new Coordinate(2332, 3588, 0))) {
                SceneObject eternalMagicTree = SceneObjectQuery.newQuery().name("Eternal magic tree").hidden(false).inside(new Area.Rectangular(new Coordinate(2329, 3588, 0), new Coordinate(2331, 3590, 0))).results().nearest();
                if(eternalMagicTree != null) {
                    eternalMagicTree.interact(eternalMagicTree.getOptions().get(0));
                    Execution.delayUntil(20000, () -> {
                        return player.getAnimationId() != -1;
                    });
                }
            }
        }
        //if idle near a non-cut-down tree, start chopping
        if(player.getAnimationId() == -1) {
            SceneObject eternalMagicTree = SceneObjectQuery.newQuery().name("Eternal magic tree").hidden(false).results().nearest();
            if(eternalMagicTree != null) {
                eternalMagicTree.interact(eternalMagicTree.getOptions().get(0));
                Execution.delayUntil(20000, () -> {
                    return player.getAnimationId() != -1;
                });
            }
        }
        return random.nextLong(1500,3000);
    }

    private long handleMoving(Coordinate whichCoordinate, LocalPlayer player) {

        //if the player is already near the destination point, no need to move
        if(player.distanceTo(whichCoordinate) < 10) {
            return random.nextLong(1500,3000);
        }
        //traverse() seems to have a slight bug (or possibly feature?) where it doesn't get started if the player is currently busy with some things like mining or woodcutting. so here's a brief check to make it click somewhere unless it's idle
        Coordinate vicinity = new Area.Circular(player.getCoordinate(), 1).getRandomWalkableCoordinate();
        //there used to be a walkTo(Coordinate, boolean) among other versions but they were removed after deprecation so I'm using the only version left, walkTo(int, int, boolean)
        Movement.walkTo(vicinity.getX(), vicinity.getY(), false);
        Execution.delay(random.nextLong(1000,2000));
        Movement.traverse(NavPath.resolve(new Area.Circular(whichCoordinate, 2).getRandomWalkableCoordinate()));
        return random.nextLong(1500,3000);
    }

    private long handleBanking() {

        Execution.delayUntil(random.nextLong(1500, 3000), () -> Bank.open());
        Execution.delay(random.nextLong(1000, 2000));
        //time to empty the wood box
        //sadly, the public API is a bit lacking; Backpack.interact() doesn't work when the bank interface is open
        /*
        if (Backpack.contains("Eternal magic wood box")) {
            Backpack.interact("Eternal magic wood box", "Empty - logs and bird's nests");
            Execution.delay(random.nextLong(1000,3000));
        }
        */
        //also, we can't directly interact with the wood box from its Item returned by an inv query, so we have to use its component; ".newQuery(517).componentIndex(15)" adresses the inventory when the bank is open
        Component woodBoxComp = ComponentQuery.newQuery(517).componentIndex(15).itemName("Eternal magic wood box").option("Empty - logs and bird's nests").results().first();
        if (woodBoxComp != null) {
            println("Deposited box contents: " + woodBoxComp.interact("Empty - logs and bird's nests"));
            Execution.delay(random.nextLong(1000, 3000));
        }
        //it looks like .depositAllExcept(String... names) is currently broken, so I have to use .depositAllExcept(Int... ids)
        //Execution.delayUntil(random.nextLong(1500, 3000), () -> Bank.depositAllExcept("Eternal magic wood box", "Decorated woodcutting urn (r)", "Decorated woodcutting urn", "Decorated woodcutting urn (full)"));
        Execution.delayUntil(random.nextLong(1500, 3000), () -> Bank.depositAllExcept(58253, 39012, 39014, 39015));
        //Execution.delay(random.nextLong(1000,2000));
        //Bank.close();
        //Execution.delay(random.nextLong(1000,2000));
        return random.nextLong(1500,3000);
    }

    //the big String containing all text in the stats
    String logString() {
        int xpGained = Skills.WOODCUTTING.getSkill().getExperience() - startingExperience;
        String bigString = "Time elapsed: " + timeElapsed() + "\n";
        bigString = bigString + "Experience gained: " + xpGained + " (" + calculatePerHour(xpGained) + " / hr)\n";
        for(var i = logNames.size() - 1; i >= 0; i--) {
            bigString = bigString + logNames.get(i) + " x " + logAmounts.get(i) + " (" + calculatePerHour(logAmounts.get(i)) + " / hr)\n";
        }
        return bigString;
    }

    //used by logString()
    public String calculatePerHour(int toBeCalculated) {
        long timeToConsider = botState != BotState.STOPPED ? System.currentTimeMillis() : timeScriptWasLastActive;

        long timeElapsedMillis = timeToConsider - startingTime;

        long xpPerHour = (long) (toBeCalculated / (timeElapsedMillis / 3600000.0));

        NumberFormat numberFormat = NumberFormat.getInstance();
        String formattedPerHour = numberFormat.format(xpPerHour);

        return formattedPerHour;
    }

    //used by logString()
    public String timeElapsed() {
        long endingTime = botState != BotState.STOPPED ? System.currentTimeMillis() : timeScriptWasLastActive;;
        long elapsedTime = endingTime - startingTime;

        long hours = TimeUnit.MILLISECONDS.toHours(elapsedTime);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public BotState getBotState() {
        return botState;
    }

    public void setBotState(BotState botState) {
        this.botState = botState;
    }

}
