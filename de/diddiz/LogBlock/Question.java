package de.diddiz.LogBlock;

import java.util.HashMap;

import org.bukkit.entity.Player;

public class Question {
    private String answer = null;
    private final HashMap<Integer, String> answers;
    private final String questionMessage;
    private final Player respondent;
    private final int respondentHash;
    private final long start;

    Question(final Player respondent, final String questionMessage, final String[] answers) {
        this.start = System.currentTimeMillis();
        this.respondent = respondent;
        this.respondentHash = respondent.getName().hashCode();
        this.questionMessage = questionMessage;
        this.answers = new HashMap<Integer, String>(answers.length);
        for (final String ans : answers)
            this.answers.put(ans.toLowerCase().hashCode(), ans);
    }

    public boolean isPlayerQuestioned(final int playerNameHash) {
        return playerNameHash == this.respondentHash;
    }

    public boolean isRightAnswer(final int answerHash) {
        return this.answers.containsKey(answerHash);
    }

    public synchronized void returnAnswer(final int answerHash) {
        this.answer = this.answers.get(answerHash);
        notify();
    }

    synchronized String ask() {
        final StringBuilder options = new StringBuilder();
        for (final String ans : this.answers.values())
            options.append("/" + ans + ", ");
        options.delete(options.length() - 2, options.length());
        this.respondent.sendMessage(this.questionMessage);
        this.respondent.sendMessage("- " + options + "?");
        try {
            this.wait();
        } catch (final InterruptedException ex) {
            this.answer = "interrupted";
        }
        return this.answer;
    }

    synchronized boolean isExpired() {
        if (System.currentTimeMillis() - this.start > 300000) {
            this.answer = "timed out";
            notify();
            return true;
        }
        return false;
    }
}
