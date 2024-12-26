package net.botwithus;

import net.botwithus.rs3.game.skills.Skills;
import net.botwithus.rs3.imgui.ImGui;
import net.botwithus.rs3.imgui.ImGuiWindowFlag;
import net.botwithus.rs3.script.ScriptConsole;
import net.botwithus.rs3.script.ScriptGraphicsContext;

import java.util.LinkedList;

import static net.botwithus.rs3.script.ScriptConsole.println;

public class AntanhasEternalMagicTreesGraphicsContext extends ScriptGraphicsContext {

    private AntanhasEternalMagicTrees script;

    public AntanhasEternalMagicTreesGraphicsContext(ScriptConsole scriptConsole, AntanhasEternalMagicTrees script) {
        super(scriptConsole);
        this.script = script;
    }

    @Override
    public void drawSettings() {
        if (ImGui.Begin("Antanha's eternal magic trees", ImGuiWindowFlag.None.getValue())) {
            if (ImGui.BeginTabBar("My bar", ImGuiWindowFlag.None.getValue())) {
                if (ImGui.BeginTabItem("Main", ImGuiWindowFlag.None.getValue())) {
                    ImGui.Text("Script state: " + script.getBotState());
                    ImGui.BeginDisabled(script.getBotState() != AntanhasEternalMagicTrees.BotState.STOPPED);
                    if (ImGui.Button("Start")) {
                        //button has been clicked
                        script.setBotState(AntanhasEternalMagicTrees.BotState.SETUP);
                        script.logNames = new LinkedList<>();
                        script.logAmounts = new LinkedList<>();
                        script.startingExperience = Skills.WOODCUTTING.getSkill().getExperience();
                        script.startingTime = System.currentTimeMillis();
                    }
                    ImGui.EndDisabled();
                    ImGui.SameLine();
                    ImGui.BeginDisabled(script.getBotState() == AntanhasEternalMagicTrees.BotState.STOPPED);
                    if (ImGui.Button("Stop")) {
                        //has been clicked
                        script.setBotState(AntanhasEternalMagicTrees.BotState.STOPPED);
                        script.timeScriptWasLastActive = System.currentTimeMillis();
                    }
                    ImGui.EndDisabled();
                    ImGui.Separator();
                    ImGui.Text("Instructions:");
                    ImGui.Text("Requires War's Retreat teleport to be unlocked or that you have Ring of Fortune or Luck of the Dwarves equipped.");
                    ImGui.Text("It'll pick bird's nests up, and it'll use decorated urns and/or wood box if you leave them in your inventory.");
                    ImGui.Text("If you have a normal and/or perfect juju woodcutting potion when starting the script, it will use it and withdraw more if needed the next time it banks.");
                    ImGui.Text("Make sure your backpack and magic (if using War's Retreat teleport) or equipment (if using Ring of Fortune or Luck of the Dwarves) menus are open, then start the script.");
                    ImGui.EndTabItem();
                }
                if (ImGui.BeginTabItem("Stats", ImGuiWindowFlag.None.getValue())) {
                    ImGui.Text(script.logString());
                    ImGui.EndTabItem();
                }
                ImGui.EndTabBar();
            }
            ImGui.End();
        }

    }

    @Override
    public void drawOverlay() {
        super.drawOverlay();
    }
}
