package de.diddiz.LogBlock.listeners;

import java.util.Vector;

import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import de.diddiz.LogBlock.Question;



public class LogBlockQuestionerPlayerListener implements Listener {
    private final Vector<Question> questions;

    public LogBlockQuestionerPlayerListener(final Vector<Question> questions) {
        this.questions = questions;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        if (!this.questions.isEmpty()) {
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
