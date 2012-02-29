package de.diddiz.LogBlockQuestioner;

import java.util.Vector;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class LogBlockQuestioner extends JavaPlugin {
    private final Vector<Question> questions = new Vector<Question>();

    public String ask(Player respondent, String questionMessage, String... answers) {
        final Question question = new Question(respondent, questionMessage, answers);
        this.questions.add(question);
        return question.ask();
    }

    @Override
    public void onDisable() {
        getServer().getLogger().info("LogBlockQuestioner disabled");
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new LogBlockQuestionerPlayerListener(this.questions),
                this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new QuestionsReaper(this.questions),
                15000, 15000);
        getServer().getLogger().info("LogBlockQuestioner v" + getDescription().getVersion() + " enabled");
    }
}
