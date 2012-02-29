package de.diddiz.LogBlockQuestioner;

import java.util.Vector;

import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class LogBlockQuestionerPlayerListener implements Listener {
    private final Vector<Question> questions;

    LogBlockQuestionerPlayerListener(Vector<Question> questions) {
        this.questions = questions;
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!event.isCancelled() && !this.questions.isEmpty()) {
            final int playerHash = event.getPlayer().getName().hashCode();
            final int answerHash = event.getMessage().substring(1).toLowerCase().hashCode();
            for (final Question question : this.questions)
                if (question.isPlayerQuestioned(playerHash) && question.isRightAnswer(answerHash)) {
                    question.returnAnswer(answerHash);
                    this.questions.remove(question);
                    event.setCancelled(true);
                    break;
                }
        }
    }
}
